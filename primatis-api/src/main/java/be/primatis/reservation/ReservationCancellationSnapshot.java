package be.primatis.reservation;

/**
 * Projection scalaire (interface Spring Data) utilisée exclusivement pour
 * décider de l'ordre de verrouillage d'une annulation de Reservation
 * (DEV-08.6) avant toute acquisition de lock — jamais l'Entity {@code
 * Reservation} elle-même. Voir
 * {@link ReservationRepository#findCancellationSnapshotById(Long)}.
 */
public interface ReservationCancellationSnapshot {

    Long getId();

    ReservationStatus getReservationStatus();

    Long getAssignedCopyId();

    Long getUserId();
}
