package be.primatis.e2e;

import be.primatis.access.Role;
import be.primatis.access.RoleRepository;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.access.UserRoleRepository;
import be.primatis.catalogue.Author;
import be.primatis.catalogue.AuthorRepository;
import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.CopyRepository;
import be.primatis.catalogue.Genre;
import be.primatis.catalogue.GenreRepository;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleAuthor;
import be.primatis.catalogue.TitleAuthorId;
import be.primatis.catalogue.TitleAuthorRepository;
import be.primatis.catalogue.TitleGenre;
import be.primatis.catalogue.TitleGenreId;
import be.primatis.catalogue.TitleGenreRepository;
import be.primatis.catalogue.TitleRepository;
import be.primatis.catalogue.TitleStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberNumberGenerator;
import be.primatis.user.MemberStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Provisionne la baseline de comptes E2E (DEV-DEC-0067) — ROLE_MEMBER,
 * ROLE_LIBRARIAN, ROLE_ADMIN — au démarrage de l'application sous le profil
 * {@code e2e} uniquement. Ne s'exécute jamais sous {@code default}
 * (primatis_dev) ni {@code test} (primatis_test) : {@link Profile} garantit
 * qu'aucun autre environnement n'est affecté.
 *
 * Écrit directement via les Repository/Entity réels (mêmes {@link
 * AppUserRepository}/{@link RoleRepository}/{@link UserRoleRepository}/
 * {@link PasswordEncoder}/{@link MemberNumberGenerator} que le reste de
 * l'application — jamais de SQL brut) plutôt que d'appeler {@code
 * UserService#createUser}, qui exige {@code @PreAuthorize("hasAuthority(
 * 'USER_MANAGE')")} et un {@code adminUserId} déjà existant : sur une base
 * {@code primatis_e2e} tout juste reconstruite (DEV-DEC-0069), aucun Admin
 * authentifié n'existe encore pour satisfaire cette double exigence
 * (« problème d'amorçage classique », DEV-14.1 §7.1/GAP-14.1-01). Ce
 * contournement reste strictement local à ce provisioner E2E et ne modifie
 * ni {@code UserService} ni la sécurité de production.
 *
 * Idempotent : si le compte Admin E2E existe déjà (ex. run relancé sans
 * reset préalable de la base), ce Runner ne fait rien — y compris pour le
 * catalogue (DEV-14.4) : les deux baselines (comptes, catalogue) sont
 * toujours créées ensemble, dans la même transaction, jamais l'une sans
 * l'autre, un seul gate d'idempotence suffit.
 */
@Component
@Profile("e2e")
public class E2eBaselineProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(E2eBaselineProvisioner.class);

    /**
     * Comptes E2E fixes et documentés — jamais des identifiants réels ni
     * réutilisés hors {@code primatis_e2e} (DEV-DEC-0066/0067). Ces valeurs
     * sont volontairement non sensibles (base locale, jetable à chaque
     * reset) : voir {@code primatis-web/e2e/fixtures/e2e-accounts.ts} pour
     * la copie consommée par les tests Playwright (une seule source de
     * vérité textuelle, dupliquée ici uniquement parce que backend/frontend
     * sont deux runtimes distincts sans module partagé).
     */
    static final String ADMIN_EMAIL = "admin.e2e@primatis.local";
    static final String ADMIN_PASSWORD = "PrimatisE2eAdmin#Local";
    static final String LIBRARIAN_EMAIL = "librarian.e2e@primatis.local";
    static final String LIBRARIAN_PASSWORD = "PrimatisE2eLibrarian#Local";
    static final String MEMBER_EMAIL = "member.e2e@primatis.local";
    static final String MEMBER_PASSWORD = "PrimatisE2eMember#Local";
    /**
     * Second MEMBER (DEV-14.6, DEV-DEC-0068 §8 "préconditions structurelles") :
     * nécessaire pour un scénario Reservation auto-suffisant — un premier
     * membre emprunte l'unique Copy d'un Title (le rendant réellement
     * indisponible), un second réserve alors ce même Title. Sans ce second
     * compte, le scénario Reservation dépendrait implicitement de l'ordre
     * d'exécution d'un autre fichier de test (ex. loans.spec.ts), ce que le
     * handoff DEV-14.6 interdit explicitement (§9, isolation des tests).
     */
    static final String MEMBER2_EMAIL = "member2.e2e@primatis.local";
    static final String MEMBER2_PASSWORD = "PrimatisE2eMember2#Local";

    /**
     * Baseline catalogue E2E (DEV-14.4, DEV-DEC-0068) — petite, déterministe,
     * identifiée par des identifiants fonctionnels stables (ISBN,
     * inventoryCode), jamais par un ID technique. Copie textuelle côté
     * Playwright : {@code primatis-web/e2e/fixtures/e2e-catalogue.ts}. Trois
     * Titles ACTIVE (recherchables via le préfixe commun {@code CATALOGUE_Q_PREFIX})
     * + un Title WITHDRAWN (preuve que le filtrage ACTIVE-only est réel, pas
     * seulement une convention de présentation). Aucun Copy ON_LOAN/RESERVED
     * fabriqué sans workflow réel (Loan/Reservation inexistants à ce stade,
     * DEV-14.5+) — uniquement AVAILABLE ou absence de Copy, seuls états que
     * la baseline peut honnêtement représenter sans workflow métier associé.
     */
    static final String CATALOGUE_Q_PREFIX = "PRIMATIS E2E";
    static final String TITLE_A_ISBN = "9782000000001";
    static final String TITLE_B_ISBN = "9782000000002";
    static final String TITLE_C_ISBN = "9782000000003";
    static final String TITLE_WITHDRAWN_ISBN = "9782000000004";
    static final String COPY_A_INVENTORY_CODE = "E2E-COPY-0001";
    static final String COPY_B_INVENTORY_CODE = "E2E-COPY-0002";
    /**
     * Title/Copy dédiés au scénario Fine (DEV-14.7, DEV-DEC-0068 §8
     * "préconditions structurelles") — isolation totale par rapport à
     * Title A (E2E-COPY-0001, déjà utilisé par loans.spec.ts et
     * reservations.spec.ts) : le scénario Fine avance le Clock partagé
     * de l'application, un Copy dédié évite toute ambiguïté avec un autre
     * fichier de test réutilisant le même exemplaire dans une fenêtre
     * temporelle décalée.
     */
    static final String TITLE_FINE_ISBN = "9782000000005";
    static final String COPY_FINE_INVENTORY_CODE = "E2E-COPY-0003";

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberNumberGenerator memberNumberGenerator;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;
    private final TitleRepository titleRepository;
    private final TitleAuthorRepository titleAuthorRepository;
    private final TitleGenreRepository titleGenreRepository;
    private final CopyRepository copyRepository;
    private final Clock clock;

    public E2eBaselineProvisioner(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            MemberNumberGenerator memberNumberGenerator,
            AuthorRepository authorRepository,
            GenreRepository genreRepository,
            TitleRepository titleRepository,
            TitleAuthorRepository titleAuthorRepository,
            TitleGenreRepository titleGenreRepository,
            CopyRepository copyRepository,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.memberNumberGenerator = memberNumberGenerator;
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
        this.titleRepository = titleRepository;
        this.titleAuthorRepository = titleAuthorRepository;
        this.titleGenreRepository = titleGenreRepository;
        this.copyRepository = copyRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        provisionIfNeeded();
    }

    @Transactional
    void provisionIfNeeded() {
        if (appUserRepository.existsByEmail(ADMIN_EMAIL)) {
            log.info("Baseline E2E déjà provisionnée (compte {} existant) — aucune action.", ADMIN_EMAIL);
            return;
        }

        Role adminRole = requiredRole("ROLE_ADMIN");
        Role librarianRole = requiredRole("ROLE_LIBRARIAN");
        Role memberRole = requiredRole("ROLE_MEMBER");

        Instant now = clock.instant();

        AppUser admin = createStaffAccount(ADMIN_EMAIL, ADMIN_PASSWORD, "E2E", "Admin", now);
        assignRole(admin, adminRole, now, null);

        AppUser librarian = createStaffAccount(LIBRARIAN_EMAIL, LIBRARIAN_PASSWORD, "E2E", "Librarian", now);
        assignRole(librarian, librarianRole, now, admin);

        AppUser member = createMemberAccount(MEMBER_EMAIL, MEMBER_PASSWORD, "E2E", "Member", now);
        assignRole(member, memberRole, now, admin);

        AppUser member2 = createMemberAccount(MEMBER2_EMAIL, MEMBER2_PASSWORD, "E2E", "Member2", now);
        assignRole(member2, memberRole, now, admin);

        log.info("Baseline E2E provisionnée : 4 comptes créés (ADMIN={}, LIBRARIAN={}, MEMBER={}, MEMBER2={}).",
                ADMIN_EMAIL, LIBRARIAN_EMAIL, MEMBER_EMAIL, MEMBER2_EMAIL);

        provisionCatalogueBaseline(now);
    }

    private Role requiredRole(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException(
                        "Rôle RBAC obligatoire absent de primatis_e2e : " + code
                                + " — la migration V002 (bootstrap RBAC) n'a pas été appliquée correctement."));
    }

    private AppUser createStaffAccount(
            String email, String rawPassword, String firstName, String lastName, Instant now) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return appUserRepository.save(user);
    }

    private AppUser createMemberAccount(
            String email, String rawPassword, String firstName, String lastName, Instant now) {
        AppUser user = createStaffAccount(email, rawPassword, firstName, lastName, now);
        LocalDate registrationDate = LocalDate.now(clock);
        user.setMemberNumber(memberNumberGenerator.generateNext());
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setRegistrationDate(registrationDate);
        // Échéance arbitraire (1 an), donnée de fixture E2E — pas une règle
        // métier : aucun scénario DEV-14.2/14.3 ne dépend de cette valeur.
        user.setMemberExpirationDate(registrationDate.plusYears(1));
        return appUserRepository.save(user);
    }

    private void assignRole(AppUser user, Role role, Instant now, AppUser assignedBy) {
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), role.getId()));
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(now);
        userRole.setAssignedBy(assignedBy);
        userRoleRepository.save(userRole);
    }

    /**
     * Baseline catalogue E2E (DEV-14.4) : 3 Authors, 2 Genres, 4 Titles (3
     * ACTIVE + 1 WITHDRAWN), 2 Copies AVAILABLE. Volume minimal réellement
     * nécessaire aux parcours de consultation publique (DEV-14.4) et
     * réutilisable tel quel par les futurs domaines Loans/Reservations
     * (DEV-DEC-0068) sans réinvention.
     */
    private void provisionCatalogueBaseline(Instant now) {
        Author authorAlpha = createAuthor("E2E Author Alpha");
        Author authorBeta = createAuthor("E2E Author Beta");
        Author authorGamma = createAuthor("E2E Author Gamma");

        Genre novelGenre = createGenre("E2E_NOVEL", "Roman E2E");
        Genre essayGenre = createGenre("E2E_ESSAY", "Essai E2E");

        Title titleA = createTitle(
                TITLE_A_ISBN, CATALOGUE_Q_PREFIX + " Available Title", Language.FR, TitleStatus.ACTIVE, now);
        linkAuthor(titleA, authorAlpha);
        linkGenre(titleA, novelGenre);
        createCopy(titleA, COPY_A_INVENTORY_CODE, now);

        Title titleB = createTitle(
                TITLE_B_ISBN, CATALOGUE_Q_PREFIX + " Second Title", Language.EN, TitleStatus.ACTIVE, now);
        linkAuthor(titleB, authorAlpha);
        linkAuthor(titleB, authorBeta);
        linkGenre(titleB, essayGenre);
        createCopy(titleB, COPY_B_INVENTORY_CODE, now);

        Title titleC = createTitle(
                TITLE_C_ISBN, CATALOGUE_Q_PREFIX + " Title Without Copy", Language.FR, TitleStatus.ACTIVE, now);
        linkAuthor(titleC, authorGamma);
        linkGenre(titleC, novelGenre);
        // Aucun Copy : Title volontairement sans exemplaire (fixture pour
        // réutilisation Reservations DEV-14.6, pas testé visuellement en
        // DEV-14.4 — le catalogue public n'affiche aucun Copy, TitleController
        // Javadoc "N'inclut aucun exemplaire").

        createTitle(TITLE_WITHDRAWN_ISBN, CATALOGUE_Q_PREFIX + " Withdrawn Title", Language.FR,
                TitleStatus.WITHDRAWN, now);
        // Aucun Author/Genre/Copy : jamais visible publiquement (titleStatus
        // WITHDRAWN), sert uniquement à prouver que le filtrage ACTIVE-only
        // est réellement appliqué par le backend (DEV-14.4).

        Title titleFine = createTitle(
                TITLE_FINE_ISBN, CATALOGUE_Q_PREFIX + " Fine Scenario Title", Language.FR, TitleStatus.ACTIVE, now);
        linkAuthor(titleFine, authorGamma);
        linkGenre(titleFine, essayGenre);
        createCopy(titleFine, COPY_FINE_INVENTORY_CODE, now);
        // Précondition structurelle uniquement (DEV-DEC-0068 §8, DEV-14.7) :
        // Copy AVAILABLE dédié au scénario Fine — jamais un Loan/Fine
        // préchargé, ceux-ci restent produits par le workflow applicatif
        // réel (registerLoan/registerReturn) une fois le Clock E2E avancé.

        log.info("Baseline catalogue E2E provisionnée : 3 Authors, 2 Genres, 5 Titles (4 ACTIVE + 1 WITHDRAWN), 3 Copies.");
    }

    private Author createAuthor(String fullName) {
        Author author = new Author();
        author.setFullName(fullName);
        return authorRepository.save(author);
    }

    private Genre createGenre(String code, String label) {
        Genre genre = new Genre();
        genre.setCode(code);
        genre.setLabel(label);
        return genreRepository.save(genre);
    }

    private Title createTitle(String isbn, String title, Language language, TitleStatus status, Instant now) {
        Title entity = new Title();
        entity.setIsbn(isbn);
        entity.setTitle(title);
        entity.setLanguage(language);
        entity.setTitleStatus(status);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return titleRepository.save(entity);
    }

    private void linkAuthor(Title title, Author author) {
        TitleAuthor titleAuthor = new TitleAuthor();
        titleAuthor.setId(new TitleAuthorId(title.getId(), author.getId()));
        titleAuthor.setTitle(title);
        titleAuthor.setAuthor(author);
        titleAuthorRepository.save(titleAuthor);
    }

    private void linkGenre(Title title, Genre genre) {
        TitleGenre titleGenre = new TitleGenre();
        titleGenre.setId(new TitleGenreId(genre.getId(), title.getId()));
        titleGenre.setGenre(genre);
        titleGenre.setTitle(title);
        titleGenreRepository.save(titleGenre);
    }

    private void createCopy(Title title, String inventoryCode, Instant now) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        copyRepository.save(copy);
    }
}
