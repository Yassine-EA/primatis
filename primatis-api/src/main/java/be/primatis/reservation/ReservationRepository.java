package be.primatis.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Miroir de l'invariant structurel « au maximum une Reservation active
     * par couple (user, title) » (ux_reservation_active_user_title, V001).
     */
    List<Reservation> findByUserIdAndTitleIdAndReservationStatusIn(
            Long userId, Long titleId, Collection<ReservationStatus> reservationStatuses);

    /**
     * Miroir de l'invariant structurel « un Copy affecté à au plus une
     * Reservation READY » (ux_reservation_ready_assigned_copy, V001).
     */
    Optional<Reservation> findByAssignedCopyIdAndReservationStatus(
            Long assignedCopyId, ReservationStatus reservationStatus);
}
