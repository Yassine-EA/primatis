package be.primatis.notification;

import be.primatis.article.Article;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.Fine;
import be.primatis.loan.Loan;
import be.primatis.notification.dto.NotificationMarkAllAsReadResponse;
import be.primatis.notification.dto.NotificationResponse;
import be.primatis.reservation.Reservation;
import be.primatis.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Mécanisme générique centralisé de création et de consultation des
 * {@code Notification} (DEV-10.4, architecture.md §11.2 : « NotificationService
 * conceptually centralizes recipient/NotificationType/unique business
 * origin/initial UNREAD state/type-origin consistency »). Un seul Service
 * pour les quatre origines métier (Loan/Reservation/Fine/Article) — jamais
 * quatre Services indépendants (architecture.md §11.1).
 *
 * <p><b>Création</b> ({@link #createForLoan}/{@link #createForReservation}/
 * {@link #createForFine}/{@link #createForArticle}/{@link
 * #createLoanDueSoonIfAbsent}) : primitives <em>sans leur propre frontière
 * transactionnelle</em>, même précédent architectural exact que {@code
 * FineService#createForLateReturnIfApplicable} (DEV-09.6)/{@code
 * ReservationAssignmentService#assignNextAdmissibleWaitingReservationOrMakeAvailable}
 * (DEV-08.6) — destinées à être appelées <em>depuis</em> une transaction
 * métier déjà ouverte (DEV-10.5+, {@code LoanService.registerReturn} etc.),
 * jamais {@code REQUIRES_NEW} : la Notification doit committer ou échouer
 * avec l'événement métier qui l'a produite (architecture.md §7.5). Pendant
 * DEV-10.4, testées directement depuis une classe de test {@code
 * @Transactional} (même pattern exact que {@code FineServiceTests}).
 *
 * <p><b>Self-service</b> ({@link #listOwnNotifications}/{@link
 * #markAsRead}/{@link #markAllAsRead}/{@link #countUnread}) : workflows
 * autonomes déclenchés directement par le Controller, jamais appelés
 * depuis une transaction métier d'un autre domaine — portent leur propre
 * frontière {@code @Transactional}, même précédent exact que {@code
 * FineService#confirmExternalPayment}/{@code #cancelFine}.
 *
 * <p><b>Compatibilité type/origine</b> : vérifiée explicitement via des
 * ensembles constants par origine (jamais une convention de préfixe de
 * nom, cf. mission DEV-10.4 §7) — une tentative invalide (ex. {@code
 * createForLoan(loan, NotificationType.FINE_ISSUED, ...)}) est un bug de
 * l'appelant, jamais un cas atteignable depuis une requête HTTP (les
 * quatre méthodes {@code createForX} ne sont jamais exposées directement
 * à un Controller) : rejetée par {@link IllegalArgumentException}, même
 * principe que {@code Objects.requireNonNull} déjà utilisé dans les DTO
 * {@code from(...)} du projet pour une précondition de programmation.
 */
@Service
public class NotificationService {

    private static final String NOTIFICATION_NOT_FOUND_CODE = "NOTIFICATION_NOT_FOUND";

    private static final Set<NotificationType> LOAN_NOTIFICATION_TYPES =
            Set.of(NotificationType.LOAN_DUE_SOON, NotificationType.LOAN_OVERDUE, NotificationType.LOAN_RETURNED);

    private static final Set<NotificationType> RESERVATION_NOTIFICATION_TYPES = Set.of(
            NotificationType.RESERVATION_CREATED, NotificationType.RESERVATION_READY,
            NotificationType.RESERVATION_EXPIRED, NotificationType.RESERVATION_CANCELLED);

    private static final Set<NotificationType> FINE_NOTIFICATION_TYPES =
            Set.of(NotificationType.FINE_ISSUED, NotificationType.FINE_PAID, NotificationType.FINE_CANCELLED);

    private static final Set<NotificationType> ARTICLE_NOTIFICATION_TYPES = Set.of(NotificationType.ARTICLE_PUBLISHED);

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------
    // Création — primitives sans frontière transactionnelle propre
    // ---------------------------------------------------------------

    /**
     * Crée une Notification d'origine {@code Loan} ({@code LOAN_DUE_SOON},
     * {@code LOAN_OVERDUE} ou {@code LOAN_RETURNED} uniquement). Recipient
     * dérivé de {@code loan.getUser()} (business-rules.md §6.5) — jamais
     * fourni par l'appelant. Pour {@code LOAN_DUE_SOON}, préférer {@link
     * #createLoanDueSoonIfAbsent}, seule méthode garantissant l'anti-doublon
     * (business-rules.md §6.7) ; cette méthode-ci crée inconditionnellement
     * et n'est donc jamais appropriée pour {@code LOAN_DUE_SOON} en dehors
     * de {@link #createLoanDueSoonIfAbsent} elle-même.
     */
    public NotificationResponse createForLoan(Loan loan, NotificationType type, String title, String message) {
        Objects.requireNonNull(loan, "loan");
        requireOrigin(LOAN_NOTIFICATION_TYPES, type, "Loan");
        return create(loan.getUser(), type, loan, null, null, null, title, message);
    }

    /**
     * Crée une Notification {@code LOAN_DUE_SOON} pour {@code loan} si
     * aucune n'existe déjà (business-rules.md §6.7/§10.6, database-model.md
     * §13.7 : « maximum une Notification LOAN_DUE_SOON par Loan, même si
     * LOAN_DUE_SOON_DAYS change » — invariant FIGÉ, DEV-10.1 §12). Contrôle
     * applicatif idempotent via {@link
     * NotificationRepository#existsByLoanIdAndNotificationType} avant toute
     * tentative d'insertion (évite de déclencher inutilement une violation
     * SQL dans le chemin normal) ; l'index unique partiel {@code
     * ux_notification_loan_due_soon} (V007, DEV-10.3) reste le dernier
     * filet d'intégrité en concurrence entre deux exécutions du futur
     * scheduler (DEV-10.6+), jamais remplacé par ce seul contrôle
     * applicatif.
     *
     * @return la Notification nouvellement créée, ou {@link Optional#empty()}
     *         si une {@code LOAN_DUE_SOON} existait déjà pour ce Loan
     *         (aucune ligne créée, aucune exception — no-op idempotent).
     */
    public Optional<NotificationResponse> createLoanDueSoonIfAbsent(Loan loan, String title, String message) {
        Objects.requireNonNull(loan, "loan");
        if (notificationRepository.existsByLoanIdAndNotificationType(loan.getId(), NotificationType.LOAN_DUE_SOON)) {
            return Optional.empty();
        }
        return Optional.of(create(loan.getUser(), NotificationType.LOAN_DUE_SOON, loan, null, null, null, title, message));
    }

    /**
     * Crée une Notification d'origine {@code Reservation} ({@code
     * RESERVATION_CREATED}, {@code RESERVATION_READY}, {@code
     * RESERVATION_EXPIRED} ou {@code RESERVATION_CANCELLED} uniquement).
     * Recipient dérivé de {@code reservation.getUser()} (business-rules.md
     * §6.5) — jamais fourni par l'appelant.
     */
    public NotificationResponse createForReservation(Reservation reservation, NotificationType type, String title, String message) {
        Objects.requireNonNull(reservation, "reservation");
        requireOrigin(RESERVATION_NOTIFICATION_TYPES, type, "Reservation");
        return create(reservation.getUser(), type, null, reservation, null, null, title, message);
    }

    /**
     * Crée une Notification d'origine {@code Fine} ({@code FINE_ISSUED},
     * {@code FINE_PAID} ou {@code FINE_CANCELLED} uniquement). Recipient
     * dérivé de {@code fine.getLoan().getUser()} (business-rules.md §6.5 :
     * « recipient → borrower of the Fine's Loan ») — jamais fourni par
     * l'appelant.
     */
    public NotificationResponse createForFine(Fine fine, NotificationType type, String title, String message) {
        Objects.requireNonNull(fine, "fine");
        requireOrigin(FINE_NOTIFICATION_TYPES, type, "Fine");
        return create(fine.getLoan().getUser(), type, null, null, fine, null, title, message);
    }

    /**
     * Crée une Notification {@code ARTICLE_PUBLISHED} pour <b>un seul</b>
     * {@code recipient} donné. Primitive unitaire structurellement prête,
     * délibérément <b>sans</b> logique de diffusion vers l'ensemble des
     * {@code AppUser} où {@code memberStatus = ACTIVE} (business-rules.md
     * §6.5/§6.10) : cette diffusion appartient au futur workflow de
     * publication Article (DEV-11, {@code ArticleService} inexistant à ce
     * jour, DEV-10.1 §16/§18 — dépendance différée, pas un gap DEV-10).
     * Aucune requête {@code AppUserRepository} n'est ajoutée ici (mission
     * DEV-10.4 §8 : « ne pas modifier AppUserRepository pour Article dans
     * DEV-10.4 »). Le futur appelant DEV-11 itérera la population ACTIVE et
     * appellera cette méthode une fois par destinataire.
     */
    public NotificationResponse createForArticle(Article article, AppUser recipient, String title, String message) {
        Objects.requireNonNull(article, "article");
        Objects.requireNonNull(recipient, "recipient");
        return create(recipient, NotificationType.ARTICLE_PUBLISHED, null, null, null, article, title, message);
    }

    /**
     * Helper privé commun : construit et persiste une Notification avec
     * exactement une origine non {@code null} parmi {@code loan}/{@code
     * reservation}/{@code fine}/{@code article} — chaque appelant public
     * ({@code createForX}) ne fournit jamais qu'une seule origine non
     * {@code null}, ce qui rend mécaniquement impossible qu'un appelant
     * externe compose une combinaison invalide (mission DEV-10.4 §6 :
     * l'API publique explicite par origine empêche structurellement ce cas,
     * la contrainte {@code ck_notification_exactly_one_origin} — V001 —
     * reste le filet de sécurité structurel ultime). État initial toujours
     * {@code UNREAD}/{@code readAt = null}/{@code createdAt = clock.instant()}
     * — jamais fourni par l'appelant (mission §10).
     */
    private NotificationResponse create(
            AppUser recipient, NotificationType type, Loan loan, Reservation reservation, Fine fine, Article article,
            String title, String message) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(type, "notificationType");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title ne peut pas être vide.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message ne peut pas être vide.");
        }

        Notification notification = new Notification();
        notification.setRecipientUser(recipient);
        notification.setLoan(loan);
        notification.setReservation(reservation);
        notification.setFine(fine);
        notification.setArticle(article);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationStatus(NotificationStatus.UNREAD);
        notification.setCreatedAt(clock.instant());
        notification.setReadAt(null);

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    private void requireOrigin(Set<NotificationType> allowedTypes, NotificationType type, String originName) {
        Objects.requireNonNull(type, "notificationType");
        if (!allowedTypes.contains(type)) {
            throw new IllegalArgumentException(
                    "NotificationType " + type + " incompatible avec l'origine " + originName + ".");
        }
    }

    // ---------------------------------------------------------------
    // Self-service — workflows autonomes, frontière transactionnelle propre
    // ---------------------------------------------------------------

    /**
     * Liste paginée des Notifications de l'utilisateur authentifié, tous
     * statuts confondus (historique inclus) — même précédent ownership
     * structurelle exact que {@code FineService#listOwnFines}/{@code
     * LoanService#listOwnLoans}. Aucun {@code @PreAuthorize} : {@code
     * selfUserId} provient exclusivement de l'identité authentifiée
     * (Controller), jamais d'un paramètre client. Page vide (jamais 404) en
     * l'absence de Notification.
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listOwnNotifications(Long selfUserId, Pageable pageable) {
        return notificationRepository.findByRecipientUserId(selfUserId, pageable).map(NotificationResponse::from);
    }

    /**
     * Nombre de Notifications {@code UNREAD} de l'utilisateur authentifié
     * (DEV-DEC-0051).
     */
    @Transactional(readOnly = true)
    public long countUnread(Long selfUserId) {
        return notificationRepository.countByRecipientUserIdAndNotificationStatus(selfUserId, NotificationStatus.UNREAD);
    }

    /**
     * Passage {@code UNREAD → READ} d'une Notification appartenant à
     * l'utilisateur authentifié. Chargement scopé par ownership via {@link
     * NotificationRepository#findByIdAndRecipientUserId} (jamais {@code
     * findById} suivi d'une comparaison côté application) — {@code
     * Optional} vide aussi bien pour un identifiant inexistant que pour une
     * Notification appartenant à un autre user, traduit dans les deux cas
     * en 404 uniforme (même précédent que {@code RESERVATION_NOT_FOUND}/
     * {@code FINE_NOT_FOUND} : ne jamais révéler qu'un identifiant existe
     * sous un autre scope).
     *
     * <p><b>Idempotence {@code READ → READ}</b> (DEV-10.1 §23 OD-NOTIF-2,
     * implementation freedom) : un second appel sur une Notification déjà
     * {@code READ} réussit sans erreur et sans modifier {@code readAt} — la
     * mutation n'est appliquée que si le statut courant est encore {@code
     * UNREAD}. {@code readAt} matérialise ainsi la <em>première</em>
     * lecture, jamais la plus récente (choisi explicitement pour ne pas
     * transformer sa sémantique en « dernière lecture »).
     */
    @Transactional
    public NotificationResponse markAsRead(Long selfUserId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, selfUserId)
                .orElseThrow(() -> new ResourceNotFoundException(NOTIFICATION_NOT_FOUND_CODE,
                        "Aucune notification pour l'identifiant " + notificationId + "."));

        if (notification.getNotificationStatus() == NotificationStatus.UNREAD) {
            notification.setNotificationStatus(NotificationStatus.READ);
            notification.setReadAt(clock.instant());
        }

        return NotificationResponse.from(notification);
    }

    /**
     * Passage {@code UNREAD → READ} de toutes les Notifications de
     * l'utilisateur authentifié (DEV-DEC-0052). Strictement limité aux
     * Notifications appartenant à l'utilisateur authentifié ({@link
     * NotificationRepository#findByRecipientUserIdAndNotificationStatus}).
     * Un seul {@link Instant} ({@code clock.instant()} appelé une seule
     * fois) appliqué à tout le lot — jamais un {@code now} distinct par
     * ligne, pour que toutes les Notifications marquées lors d'une même
     * opération partagent exactement le même {@code readAt}. Idempotent :
     * aucune Notification {@code UNREAD} restante ⇒ succès, liste vide
     * traitée, {@code updatedCount = 0} — jamais une erreur.
     */
    @Transactional
    public NotificationMarkAllAsReadResponse markAllAsRead(Long selfUserId) {
        List<Notification> unreadNotifications =
                notificationRepository.findByRecipientUserIdAndNotificationStatus(selfUserId, NotificationStatus.UNREAD);

        if (unreadNotifications.isEmpty()) {
            return new NotificationMarkAllAsReadResponse(0);
        }

        Instant now = clock.instant();
        for (Notification notification : unreadNotifications) {
            notification.setNotificationStatus(NotificationStatus.READ);
            notification.setReadAt(now);
        }

        return new NotificationMarkAllAsReadResponse(unreadNotifications.size());
    }
}
