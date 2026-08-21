package be.primatis.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Consultation paginée des Notifications d'un recipient, tous statuts
     * confondus (historique inclus) — même précédent exact que {@code
     * LoanRepository.findByUserId}/{@code ReservationRepository.findByUserId}/
     * {@code FineRepository.findByLoanUserId} (DEV-07.2/DEV-08.4/DEV-09.4),
     * destinée au futur {@code GET /api/v1/me/notifications} (DEV-10.4).
     * Aucun ordre imposé ici : le tri appartient à l'appelant via
     * {@link Pageable} — aucune source ne fixe encore d'ordre métier pour
     * la consultation Notification (DEV-10.1 §19 : {@code createdAt DESC,
     * id DESC} anticipé mais pas encore codé).
     */
    Page<Notification> findByRecipientUserId(Long recipientUserId, Pageable pageable);

    /**
     * Chargement scopé par ownership — empêche qu'un user charge la
     * Notification d'un autre user (même précédent exact que {@code
     * CopyRepository.findByIdAndTitleId}, DEV-06.6 : « ne jamais faire
     * confiance à une comparaison client-side approximative si le
     * Repository peut exprimer directement l'ownership »). Destinée au
     * futur mark-read individuel (DEV-10.4) : {@code Optional} vide aussi
     * bien pour un identifiant inexistant que pour une Notification
     * appartenant à un autre user — même 404 uniforme que
     * {@code RESERVATION_NOT_FOUND}/{@code FINE_NOT_FOUND}.
     */
    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    /**
     * Notifications {@code UNREAD} d'un recipient, non paginées —
     * destinée au futur mark-all-read (DEV-DEC-0052, DEV-10.4) : le
     * Service chargera chaque Entity pour appliquer {@code UNREAD → READ}
     * via le cycle de vie JPA normal (option retenue en DEV-10.3 §9,
     * aucun bulk update JPQL introduit ici — voir
     * {@code documentation-interne//DEV-10.3 — PERSISTANCE NOTIFICATIONS.md} §9).
     */
    List<Notification> findByRecipientUserIdAndNotificationStatus(
            Long recipientUserId, NotificationStatus notificationStatus);

    /**
     * Comptage des Notifications d'un recipient pour un statut donné
     * (DEV-DEC-0051, compteur {@code UNREAD} exposé au user authentifié).
     * Générique sur {@link NotificationStatus} plutôt que figé sur
     * {@code UNREAD} : réutilisable sans dupliquer la requête si un besoin
     * de comptage par statut apparaît ailleurs.
     */
    long countByRecipientUserIdAndNotificationStatus(Long recipientUserId, NotificationStatus notificationStatus);

    /**
     * Test d'existence pour le futur anti-doublon {@code LOAN_DUE_SOON}
     * (business-rules.md §6.7/§10.6, database-model.md §13.7 : « maximum
     * une Notification LOAN_DUE_SOON par Loan »). Générique sur
     * {@link NotificationType} plutôt que figé, réutilisable pour un
     * éventuel anti-doublon {@code LOAN_OVERDUE} périodique ultérieur sans
     * dupliquer la requête. Rend le futur workflow scheduler idempotent de
     * façon lisible ; la protection ultime en concurrence reste l'index
     * unique partiel {@code ux_notification_loan_due_soon} (V007, DEV-10.3
     * §10) — ce test d'existence ne remplace jamais la contrainte DB.
     */
    boolean existsByLoanIdAndNotificationType(Long loanId, NotificationType notificationType);
}
