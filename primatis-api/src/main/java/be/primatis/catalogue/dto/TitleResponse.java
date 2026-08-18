package be.primatis.catalogue.dto;

import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;

import java.util.Objects;

/**
 * Contrat REST de consultation liste/recherche d'un {@code Title} (DEV-06.3,
 * DEV-06.4). Volontairement compact : n'expose ni {@code summary}, ni
 * {@code pageCount}, ni les relations {@code authors}/{@code genres}/
 * {@code copies}, ni {@code createdAt}/{@code updatedAt} — charger ces
 * informations pour chaque élément d'une page paginée n'est pas justifié pour
 * un contrat de liste. La représentation détaillée appartient à
 * {@link TitleDetailResponse}. Ne déclenche aucune requête ni chargement de
 * relation : mapping strict des colonnes propres à {@code Title}.
 */
public record TitleResponse(
        Long id,
        String isbn,
        String title,
        String subtitle,
        Integer publicationYear,
        Language language,
        String publisher,
        String coverImageUrl,
        TitleStatus titleStatus) {

    public static TitleResponse from(Title title) {
        Objects.requireNonNull(title, "title");
        return new TitleResponse(
                title.getId(),
                title.getIsbn(),
                title.getTitle(),
                title.getSubtitle(),
                title.getPublicationYear(),
                title.getLanguage(),
                title.getPublisher(),
                title.getCoverImageUrl(),
                title.getTitleStatus());
    }
}
