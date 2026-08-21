package be.primatis.notification.dto;

import be.primatis.notification.Notification;
import be.primatis.notification.NotificationStatus;
import be.primatis.notification.NotificationType;

import java.time.Instant;
import java.util.Objects;

/**
 * Contrat REST de lecture d'une {@code Notification} (DEV-10.4), destiné à
 * la consultation self-service ({@code GET /api/v1/me/notifications}).
 * Aucune permission staff/écran global Notification en V1 (DEV-10.1 §10) :
 * un seul contrat, jamais de variante staff.
 *
 * <p>{@code originId} expose uniquement l'identifiant de l'origine réelle
 * (Loan/Reservation/Fine/Article) — jamais quatre champs nullable {@code
 * loanId}/{@code reservationId}/{@code fineId}/{@code articleId} exposés en
 * parallèle (mission DEV-10.4 §14 : contrat inutilement lourd). Aucun champ
 * {@code originType} distinct : la catégorie d'origine est déjà
 * intégralement déductible de {@code notificationType} via la matrice de
 * compatibilité FIGÉE (business-rules.md §6.4 : {@code LOAN_*} → Loan,
 * {@code RESERVATION_*} → Reservation, {@code FINE_*} → Fine, {@code
 * ARTICLE_PUBLISHED} → Article) — introduire un second champ purement
 * redondant n'apporterait aucune information supplémentaire au frontend.
 * Même précédent que {@code ReservationResponse.fulfilledByLoanId}
 * (DEV-08.3) : un identifiant nu plutôt qu'un sous-objet imbriqué lorsque
 * le contexte principal n'a besoin que de la référence, pas du détail
 * complet de la ressource d'origine.
 *
 * <p>{@code notificationStatus} exposé tel quel (enum {@link
 * NotificationStatus}, pas de traduction). Aucun champ calculé/artificiel
 * (pas d'{@code isUnread}) : le DTO reflète l'état persistant, jamais une
 * décision UI, même principe que {@code FineResponse}/{@code
 * ReservationResponse}.
 */
public record NotificationResponse(
        Long id,
        NotificationType notificationType,
        NotificationStatus notificationStatus,
        String title,
        String message,
        Long originId,
        Instant createdAt,
        Instant readAt) {

    public static NotificationResponse from(Notification notification) {
        Objects.requireNonNull(notification, "notification");

        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getNotificationStatus(),
                notification.getTitle(),
                notification.getMessage(),
                originId(notification),
                notification.getCreatedAt(),
                notification.getReadAt());
    }

    /**
     * Lecture de l'identifiant de l'unique origine non {@code null} —
     * {@code getId()} sur une relation {@code @ManyToOne(LAZY)} non
     * initialisée ne déclenche aucun chargement supplémentaire (l'identifiant
     * est déjà disponible sur le proxy Hibernate, même raisonnement exact
     * que {@code ReservationResponse.fulfilledByLoanId}, DEV-08.3).
     */
    private static Long originId(Notification notification) {
        if (notification.getLoan() != null) {
            return notification.getLoan().getId();
        }
        if (notification.getReservation() != null) {
            return notification.getReservation().getId();
        }
        if (notification.getFine() != null) {
            return notification.getFine().getId();
        }
        if (notification.getArticle() != null) {
            return notification.getArticle().getId();
        }
        throw new IllegalStateException(
                "Incohérence de données : Notification " + notification.getId() + " sans origine "
                        + "(ck_notification_exactly_one_origin garantit normalement au moins une origine).");
    }
}
