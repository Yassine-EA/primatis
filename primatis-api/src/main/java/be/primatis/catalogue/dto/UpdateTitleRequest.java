package be.primatis.catalogue.dto;

import be.primatis.catalogue.Language;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Contrat REST de modification staff d'un {@code Title} (DEV-06.5,
 * {@code CATALOGUE_MANAGE}). Volontairement absent : {@code titleStatus}
 * (action dédiée {@code UpdateTitleStatusRequest}, {@code PATCH .../status}).
 *
 * <p><b>Sémantique PATCH sparse à trois états</b> — {@code ABSENT} /
 * {@code PRESENT(valeur)} / {@code PRESENT(null)} — même mécanisme exact que
 * {@link be.primatis.user.web.UpdateUserRequest} (DEV-05.6) : classe
 * JavaBean, un indicateur de présence dédié par champ à sémantique de
 * {@code clear}, positionné uniquement par le setter Jackson (jamais appelé
 * pour une clé JSON absente). Champs sans sémantique de {@code clear}
 * ({@code title}/{@code language}, non-nullables en base) : présent+{@code
 * null} est refusé dans le Service, pas ici (contrainte conditionnelle, pas
 * une contrainte de forme). {@code isbn}/{@code subtitle}/{@code summary}/
 * {@code publicationYear}/{@code pageCount}/{@code publisher}/{@code
 * coverImageUrl} (tous nullable en base) : présent+{@code null} efface.
 *
 * <p>{@code authorIds}/{@code genreIds} : même cas particulier que {@code
 * roles} dans {@code UpdateUserRequest} — absent et {@code null} explicite
 * produisent intentionnellement le même état ({@code null} après
 * désérialisation), tous deux traités comme « inchangé ». {@code authorIds}
 * n'a pas de sémantique de {@code clear} (un Title garde toujours ≥1 Author,
 * un ensemble vide est refusé par le Service) ; {@code genreIds} fourni,
 * même vide, remplace intégralement l'ensemble des Genres (Genre n'étant pas
 * obligatoire).
 */
public class UpdateTitleRequest {

    private String isbn;
    private boolean isbnPresent;

    private String title;
    private boolean titlePresent;

    private String subtitle;
    private boolean subtitlePresent;

    private String summary;
    private boolean summaryPresent;

    private Integer publicationYear;
    private boolean publicationYearPresent;

    private Language language;
    private boolean languagePresent;

    private Integer pageCount;
    private boolean pageCountPresent;

    private String publisher;
    private boolean publisherPresent;

    private String coverImageUrl;
    private boolean coverImageUrlPresent;

    private List<Long> authorIds;

    private List<Long> genreIds;

    public UpdateTitleRequest() {
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
        this.isbnPresent = true;
    }

    @JsonIgnore
    public boolean isIsbnPresent() {
        return isbnPresent;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    @JsonIgnore
    public boolean isTitlePresent() {
        return titlePresent;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        this.subtitlePresent = true;
    }

    @JsonIgnore
    public boolean isSubtitlePresent() {
        return subtitlePresent;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
        this.summaryPresent = true;
    }

    @JsonIgnore
    public boolean isSummaryPresent() {
        return summaryPresent;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public void setPublicationYear(Integer publicationYear) {
        this.publicationYear = publicationYear;
        this.publicationYearPresent = true;
    }

    @JsonIgnore
    public boolean isPublicationYearPresent() {
        return publicationYearPresent;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
        this.languagePresent = true;
    }

    @JsonIgnore
    public boolean isLanguagePresent() {
        return languagePresent;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
        this.pageCountPresent = true;
    }

    @JsonIgnore
    public boolean isPageCountPresent() {
        return pageCountPresent;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
        this.publisherPresent = true;
    }

    @JsonIgnore
    public boolean isPublisherPresent() {
        return publisherPresent;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
        this.coverImageUrlPresent = true;
    }

    @JsonIgnore
    public boolean isCoverImageUrlPresent() {
        return coverImageUrlPresent;
    }

    public List<Long> getAuthorIds() {
        return authorIds;
    }

    public void setAuthorIds(List<Long> authorIds) {
        this.authorIds = authorIds;
    }

    public List<Long> getGenreIds() {
        return genreIds;
    }

    public void setGenreIds(List<Long> genreIds) {
        this.genreIds = genreIds;
    }
}
