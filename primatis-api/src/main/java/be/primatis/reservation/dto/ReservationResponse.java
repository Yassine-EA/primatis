package be.primatis.reservation.dto;

import be.primatis.catalogue.Copy;
import be.primatis.loan.Loan;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Contrat REST de lecture d'une {@code Reservation} (DEV-08.3), destiné aux
 * futures consultations staff ({@code RESERVATION_READ}, DEV-08.4) et
 * self-service ({@code /me/reservations}). {@code member}/{@code title}
 * sont des résumés compacts ({@link ReservationMemberResponse}/
 * {@link ReservationTitleResponse}), jamais les Entities {@code AppUser}/
 * {@code Title} exposées directement — même principe que
 * {@code loan.dto.LoanResponse}.
 *
 * <p>{@code assignedCopy} et {@code fulfilledByLoanId} reflètent
 * strictement l'état persisté de l'Entity, sans aucune réinterprétation :
 * une Reservation {@code FULFILLED} peut légitimement conserver un
 * {@code assignedCopy} non {@code null} (le Copy qui a effectivement été
 * prêté), {@code LoanService.registerLoan} ne le réinitialise jamais à
 * {@code null} (confirmé par l'audit DEV-08.1 §8) — le mapper ne doit
 * jamais transformer silencieusement cet état. {@code fulfilledByLoanId}
 * expose uniquement l'identifiant du {@code Loan} (jamais un résumé
 * imbriqué) : c'est la forme utilisée par le dictionnaire de données
 * lui-même ({@code fulfilled_by_loan_id}), et l'accès à
 * {@code Loan.getId()} sur la relation {@code LAZY} {@code fulfilledByLoan}
 * ne déclenche aucun chargement supplémentaire (l'identifiant est déjà
 * disponible sur le proxy Hibernate non initialisé).
 *
 * <p>{@code reservationStatus} exposé tel quel (enum {@link ReservationStatus},
 * pas de traduction). Aucun champ calculé/artificiel
 * ({@code isExpired}/{@code isCancelable}/{@code canFulfill}) : le DTO
 * reflète l'état persistant, jamais une décision UI (DEV-08.1 §21, mission
 * DEV-08.3 §5).
 */
public record ReservationResponse(
        Long id,
        ReservationMemberResponse member,
        ReservationTitleResponse title,
        ReservationCopyResponse assignedCopy,
        Long fulfilledByLoanId,
        Instant reservationDate,
        Instant expirationDate,
        ReservationStatus reservationStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static ReservationResponse from(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");

        Copy assignedCopy = reservation.getAssignedCopy();
        Loan fulfilledByLoan = reservation.getFulfilledByLoan();

        return new ReservationResponse(
                reservation.getId(),
                ReservationMemberResponse.from(reservation.getUser()),
                ReservationTitleResponse.from(reservation.getTitle()),
                assignedCopy == null ? null : ReservationCopyResponse.from(assignedCopy),
                fulfilledByLoan == null ? null : fulfilledByLoan.getId(),
                reservation.getReservationDate(),
                reservation.getExpirationDate(),
                reservation.getReservationStatus(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
