package be.primatis.catalogue.dto;

import be.primatis.catalogue.Author;
import be.primatis.catalogue.Genre;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Contrat REST de consultation détaillée d'un {@code Title} (DEV-06.3,
 * DEV-06.4), y compris ses Authors/Genres. {@code Title} ne porte aucune
 * collection inverse vers {@code TitleAuthor}/{@code TitleGenre} (relations
 * unidirectionnelles, audit DEV-06.1/06.2) : {@code authors}/{@code genres}
 * sont donc reçus explicitement en paramètre, jamais retrouvés en base par ce
 * DTO — aucune Repository, aucune requête ici.
 * <p>
 * Ne contient délibérément aucune liste de {@code Copy} : le catalogue public
 * expose des Titles, l'inventaire physique reste un domaine staff protégé par
 * {@code COPY_READ}/{@code COPY_MANAGE} (futur Service/Controller DEV-06.4+).
 */
public record TitleDetailResponse(
        Long id,
        String isbn,
        String title,
        String subtitle,
        String summary,
        Integer publicationYear,
        Language language,
        Integer pageCount,
        String publisher,
        String coverImageUrl,
        TitleStatus titleStatus,
        List<AuthorResponse> authors,
        List<GenreResponse> genres,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * {@code authors}/{@code genres} {@code null} → liste vide. Un élément
     * {@code null} au sein d'une liste non nulle échoue explicitement (via
     * {@link AuthorResponse#from}/{@link GenreResponse#from}), jamais
     * silencieusement. L'ordre reçu est conservé tel quel — aucun tri n'est
     * inventé ici, un futur besoin de tri reste un contrat du Service/
     * Repository appelant.
     */
    public static TitleDetailResponse from(Title title, List<Author> authors, List<Genre> genres) {
        Objects.requireNonNull(title, "title");
        return new TitleDetailResponse(
                title.getId(),
                title.getIsbn(),
                title.getTitle(),
                title.getSubtitle(),
                title.getSummary(),
                title.getPublicationYear(),
                title.getLanguage(),
                title.getPageCount(),
                title.getPublisher(),
                title.getCoverImageUrl(),
                title.getTitleStatus(),
                authors == null ? List.of() : authors.stream().map(AuthorResponse::from).toList(),
                genres == null ? List.of() : genres.stream().map(GenreResponse::from).toList(),
                title.getCreatedAt(),
                title.getUpdatedAt());
    }
}
