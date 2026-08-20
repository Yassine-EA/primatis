package be.primatis.reservation.dto;

import be.primatis.catalogue.Title;

import java.util.Objects;

/**
 * Représentation compacte du {@code Title} ciblé par une {@code Reservation}
 * (DEV-08.3), embarquée dans {@link ReservationResponse}. Volontairement
 * limité à l'identification ({@code id}, {@code title}, {@code isbn}) —
 * même principe de minimalisme que {@code loan.dto.LoanBorrowerResponse}/
 * {@code LoanCopyResponse} : n'expose ni {@code subtitle}, ni
 * {@code summary}, ni {@code publicationYear}, ni {@code language}, ni
 * {@code pageCount}, ni {@code publisher}, ni {@code coverImageUrl}, ni
 * {@code titleStatus}, ni les relations {@code authors}/{@code genres}/
 * {@code copies} — ces informations détaillées restent du ressort du
 * contrat catalogue ({@code TitleResponse}/{@code TitleDetailResponse}),
 * pas d'un résumé Reservation. Délibérément non réutilisé depuis
 * {@code catalogue.dto.TitleResponse} pour garder chaque domaine
 * propriétaire de son propre contrat (même principe que
 * {@link ReservationMemberResponse}). Ne déclenche aucune requête ni
 * chargement de relation supplémentaire : mapping strict des colonnes
 * propres à {@code Title}.
 */
public record ReservationTitleResponse(Long id, String title, String isbn) {

    public static ReservationTitleResponse from(Title titleEntity) {
        Objects.requireNonNull(titleEntity, "titleEntity");
        return new ReservationTitleResponse(titleEntity.getId(), titleEntity.getTitle(), titleEntity.getIsbn());
    }
}
