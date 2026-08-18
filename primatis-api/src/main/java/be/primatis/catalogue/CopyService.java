package be.primatis.catalogue;

import be.primatis.catalogue.dto.CopyResponse;
import be.primatis.catalogue.dto.CreateCopyRequest;
import be.primatis.catalogue.dto.UpdateCopyAvailabilityRequest;
import be.primatis.catalogue.dto.UpdateCopyRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Gestion staff des {@code Copy} (DEV-06.6, {@code COPY_READ}/
 * {@code COPY_MANAGE}). Service dédié séparé de {@code CatalogueService}
 * (Title) et {@code CatalogueManagementService} (Author/Genre) : {@code Copy}
 * possède sa propre sécurité (permissions distinctes), ses propres
 * invariants physiques (§ ci-dessous) et son propre cycle d'état — même
 * principe de séparation que la décision DEV-06.5.1 pour Author/Genre.
 *
 * <p><b>Interdiction absolue DEV-06.6</b> : aucune méthode de ce Service ne
 * peut écrire directement {@code ON_LOAN} ou {@code RESERVED} —
 * exclusivement réservés aux futurs workflows Loan (DEV-07) et Reservation
 * (DEV-08). Leur lecture reste autorisée et reflétée telle quelle par
 * {@link CopyResponse}.
 *
 * <p><b>Invariants physiques FIGÉS</b> (database-model.md §9.8,
 * ck_copy_condition_availability) : {@code LOST}/{@code OUT_OF_SERVICE}
 * impliquent {@code UNAVAILABLE} ; {@code DAMAGED} n'implique rien
 * automatiquement. Un retour vers {@code GOOD}/{@code DAMAGED} depuis
 * {@code LOST}/{@code OUT_OF_SERVICE} ne remet jamais automatiquement
 * {@code AVAILABLE} — seule une action explicite sur {@code /availability}
 * le peut.
 */
@Service
public class CopyService {

    private static final String TITLE_NOT_FOUND_CODE = "TITLE_NOT_FOUND";
    private static final String COPY_NOT_FOUND_CODE = "COPY_NOT_FOUND";

    private final CopyRepository copyRepository;
    private final TitleRepository titleRepository;
    private final Clock clock;

    public CopyService(CopyRepository copyRepository, TitleRepository titleRepository, Clock clock) {
        this.copyRepository = copyRepository;
        this.titleRepository = titleRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------
    // Lecture (COPY_READ)
    // ---------------------------------------------------------------

    /**
     * Exemplaires d'un Title, triés par {@code inventoryCode}
     * ({@link CopyRepository#findByTitleIdOrderByInventoryCodeAsc}, DEV-06.2)
     * — aucune pagination (collection sans limite métier maximale). Title
     * inexistant → 404 {@code TITLE_NOT_FOUND} ; Title existant sans Copy →
     * liste vide.
     */
    @PreAuthorize("hasAuthority('COPY_READ')")
    @Transactional(readOnly = true)
    public List<CopyResponse> listCopiesByTitle(Long titleId) {
        if (!titleRepository.existsById(titleId)) {
            throw new ResourceNotFoundException(TITLE_NOT_FOUND_CODE, "Aucun titre pour l'identifiant " + titleId + ".");
        }
        return copyRepository.findByTitleIdOrderByInventoryCodeAsc(titleId).stream().map(CopyResponse::from).toList();
    }

    /**
     * Détail d'un Copy, scopé au {@code titleId} du path
     * ({@link CopyRepository#findByIdAndTitleId}, §34) — Copy inexistant OU
     * appartenant à un autre Title produisent la même 404
     * {@code COPY_NOT_FOUND}, jamais 403, jamais une distinction révélant
     * l'existence du Copy ailleurs.
     */
    @PreAuthorize("hasAuthority('COPY_READ')")
    @Transactional(readOnly = true)
    public CopyResponse getCopyById(Long titleId, Long copyId) {
        return CopyResponse.from(requireScopedCopy(titleId, copyId));
    }

    // ---------------------------------------------------------------
    // Écriture (COPY_MANAGE)
    // ---------------------------------------------------------------

    /**
     * Création staff. {@code titleId} vient du path (jamais du body) — le
     * Title doit exister, {@code WITHDRAWN} inclus (aucune règle autoritaire
     * n'interdit de gérer les Copies d'un Title retiré). Aucun défaut caché :
     * {@code copyCondition}/{@code availabilityStatus} sont fournis
     * explicitement par l'appelant, leur combinaison est validée. Seules
     * {@code AVAILABLE}/{@code UNAVAILABLE} sont acceptables à la création —
     * {@code ON_LOAN}/{@code RESERVED} refusés (aucun workflow Loan/
     * Reservation ne crée de Copy dans DEV-06.6).
     */
    @PreAuthorize("hasAuthority('COPY_MANAGE')")
    @Transactional
    public CopyResponse createCopy(Long titleId, CreateCopyRequest request) {
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TITLE_NOT_FOUND_CODE, "Aucun titre pour l'identifiant " + titleId + "."));

        requireManuallyWritableAvailability(request.availabilityStatus());
        requireConditionAvailabilityCoherence(request.copyCondition(), request.availabilityStatus());

        if (copyRepository.existsByInventoryCode(request.inventoryCode())) {
            throw new ConflictException(
                    "INVENTORY_CODE_ALREADY_EXISTS", "Un exemplaire existe déjà avec ce inventoryCode.");
        }

        Instant now = clock.instant();
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(request.inventoryCode());
        copy.setLocation(request.location());
        copy.setCopyCondition(request.copyCondition());
        copy.setAvailabilityStatus(request.availabilityStatus());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        copyRepository.save(copy);

        return CopyResponse.from(copy);
    }

    /**
     * Modification staff (PATCH sparse, cf. {@link UpdateCopyRequest}).
     * {@code copyCondition} passant à {@code LOST}/{@code OUT_OF_SERVICE}
     * impose {@code UNAVAILABLE} dans la même transaction (§23, invariant
     * physique déjà FIGÉ). Tout autre champ (y compris {@code
     * copyCondition = GOOD/DAMAGED}) laisse {@code availabilityStatus}
     * strictement inchangé — y compris s'il vaut déjà {@code ON_LOAN}/
     * {@code RESERVED} (§30 : ce endpoint ne les écrase jamais).
     */
    @PreAuthorize("hasAuthority('COPY_MANAGE')")
    @Transactional
    public CopyResponse updateCopy(Long titleId, Long copyId, UpdateCopyRequest request) {
        Copy copy = requireScopedCopy(titleId, copyId);
        boolean mutated = false;

        if (request.isInventoryCodePresent()) {
            if (request.getInventoryCode() == null || request.getInventoryCode().isBlank()) {
                throw new BusinessRuleException(
                        "INVENTORY_CODE_MUST_NOT_BE_BLANK", "inventoryCode ne peut pas être vide.");
            }
            if (!request.getInventoryCode().equals(copy.getInventoryCode())
                    && copyRepository.existsByInventoryCode(request.getInventoryCode())) {
                throw new ConflictException(
                        "INVENTORY_CODE_ALREADY_EXISTS", "Un exemplaire existe déjà avec ce inventoryCode.");
            }
            copy.setInventoryCode(request.getInventoryCode());
            mutated = true;
        }
        if (request.isLocationPresent()) {
            copy.setLocation(request.getLocation());
            mutated = true;
        }
        if (request.isCopyConditionPresent()) {
            if (request.getCopyCondition() == null) {
                throw new BusinessRuleException(
                        "COPY_CONDITION_MUST_NOT_BE_NULL", "copyCondition ne peut pas être effacée.");
            }
            copy.setCopyCondition(request.getCopyCondition());
            if (request.getCopyCondition() == CopyCondition.LOST
                    || request.getCopyCondition() == CopyCondition.OUT_OF_SERVICE) {
                copy.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
            }
            mutated = true;
        }

        if (mutated) {
            copy.setUpdatedAt(clock.instant());
        }
        return CopyResponse.from(copy);
    }

    /**
     * Action dédiée de disponibilité manuelle (§25-28). Écrit uniquement
     * {@code AVAILABLE}/{@code UNAVAILABLE} — {@code ON_LOAN}/{@code
     * RESERVED} demandés → 409 {@code COPY_AVAILABILITY_WORKFLOW_MANAGED}.
     * {@code AVAILABLE} demandé alors que {@code copyCondition} vaut
     * {@code LOST}/{@code OUT_OF_SERVICE} → 409 {@code
     * COPY_CONDITION_REQUIRES_UNAVAILABLE}. Idempotent (même statut demandé
     * → succès sans effet de bord, même contrat que {@code
     * UserService.updateAccountStatus}/{@code updateTitleStatus}).
     */
    @PreAuthorize("hasAuthority('COPY_MANAGE')")
    @Transactional
    public CopyResponse updateCopyAvailability(Long titleId, Long copyId, UpdateCopyAvailabilityRequest request) {
        Copy copy = requireScopedCopy(titleId, copyId);

        requireManuallyWritableAvailability(request.status());
        if (request.status() == AvailabilityStatus.AVAILABLE
                && (copy.getCopyCondition() == CopyCondition.LOST
                        || copy.getCopyCondition() == CopyCondition.OUT_OF_SERVICE)) {
            throw new BusinessRuleException("COPY_CONDITION_REQUIRES_UNAVAILABLE",
                    "Un exemplaire LOST/OUT_OF_SERVICE ne peut pas être remis AVAILABLE.");
        }

        if (copy.getAvailabilityStatus() != request.status()) {
            copy.setAvailabilityStatus(request.status());
            copy.setUpdatedAt(clock.instant());
        }
        return CopyResponse.from(copy);
    }

    // ---------------------------------------------------------------
    // Utilitaires privés
    // ---------------------------------------------------------------

    private Copy requireScopedCopy(Long titleId, Long copyId) {
        return copyRepository.findByIdAndTitleId(copyId, titleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        COPY_NOT_FOUND_CODE, "Aucun exemplaire pour l'identifiant " + copyId
                                + " sous le titre " + titleId + "."));
    }

    /**
     * {@code ON_LOAN}/{@code RESERVED} ne sont jamais une cible d'écriture
     * manuelle DEV-06.6 (interdiction absolue §9) — ni à la création ni via
     * l'action dédiée de disponibilité. Même code d'erreur dans les deux
     * cas : il s'agit de la même règle, appliquée à deux points d'entrée.
     */
    private static void requireManuallyWritableAvailability(AvailabilityStatus availabilityStatus) {
        if (availabilityStatus == AvailabilityStatus.ON_LOAN || availabilityStatus == AvailabilityStatus.RESERVED) {
            throw new BusinessRuleException("COPY_AVAILABILITY_WORKFLOW_MANAGED",
                    "ON_LOAN et RESERVED sont exclusivement gérés par les workflows Loan/Reservation.");
        }
    }

    /**
     * Même règle que {@link #requireManuallyWritableAvailability}, réutilisée
     * pour la combinaison condition/disponibilité à la création : {@code
     * LOST}/{@code OUT_OF_SERVICE} exigent {@code UNAVAILABLE} (invariant
     * physique déjà FIGÉ, ck_copy_condition_availability). {@code DAMAGED}
     * n'impose rien.
     */
    private static void requireConditionAvailabilityCoherence(
            CopyCondition copyCondition, AvailabilityStatus availabilityStatus) {
        boolean requiresUnavailable = copyCondition == CopyCondition.LOST || copyCondition == CopyCondition.OUT_OF_SERVICE;
        if (requiresUnavailable && availabilityStatus != AvailabilityStatus.UNAVAILABLE) {
            throw new BusinessRuleException("COPY_CONDITION_REQUIRES_UNAVAILABLE",
                    "Un exemplaire LOST/OUT_OF_SERVICE doit être UNAVAILABLE.");
        }
    }
}
