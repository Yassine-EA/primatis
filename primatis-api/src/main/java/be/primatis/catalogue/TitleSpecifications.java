package be.primatis.catalogue;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Construction dynamique du filtre de recherche catalogue (DEV-06.2),
 * consommée via {@code titleRepository.findAll(TitleSpecifications.matching(...), pageable)}
 * ({@link TitleRepository} implémente {@code JpaSpecificationExecutor}).
 * <p>
 * Chaque filtre {@code null} (ou chaîne blanche pour les filtres textuels)
 * n'ajoute simplement aucun prédicat — aucun paramètre lié n'est jamais
 * construit pour un filtre absent, ce qui écarte structurellement tout risque
 * de paramètre typé uniquement par une comparaison {@code IS NULL} (cf.
 * {@link TitleRepository}).
 * <p>
 * {@code Title} ne porte aucune collection inverse vers {@code TitleAuthor}/
 * {@code TitleGenre} (relations unidirectionnelles, database-model.md §20.3) :
 * les filtres auteur/genre passent donc par une sous-requête {@code IN (id...)}
 * sur la table d'association plutôt que par une jointure sur une collection
 * inexistante — évite aussi tout risque de doublon de {@code Title} (pas de
 * {@code JOIN} multipliant les lignes, donc aucun {@code DISTINCT} requis).
 */
public final class TitleSpecifications {

    private TitleSpecifications() {
    }

    /**
     * @param titleKeyword recherche partielle insensible à la casse sur {@code Title.title}
     * @param authorId     identifiant technique d'un Author (pas de code métier sur Author)
     * @param genreCode    code métier d'un Genre ({@code genre.code}, unique)
     * @param language     correspondance exacte
     * @param titleStatus  correspondance exacte
     */
    public static Specification<Title> matching(
            String titleKeyword, Long authorId, String genreCode, Language language, TitleStatus titleStatus) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (titleKeyword != null && !titleKeyword.isBlank()) {
                String pattern = "%" + titleKeyword.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            }

            if (authorId != null) {
                Subquery<Long> authorMatch = query.subquery(Long.class);
                Root<TitleAuthor> titleAuthor = authorMatch.from(TitleAuthor.class);
                authorMatch.select(titleAuthor.get("title").get("id"))
                        .where(cb.equal(titleAuthor.get("author").get("id"), authorId));
                predicates.add(root.get("id").in(authorMatch));
            }

            if (genreCode != null && !genreCode.isBlank()) {
                Subquery<Long> genreMatch = query.subquery(Long.class);
                Root<TitleGenre> titleGenre = genreMatch.from(TitleGenre.class);
                genreMatch.select(titleGenre.get("title").get("id"))
                        .where(cb.equal(titleGenre.get("genre").get("code"), genreCode));
                predicates.add(root.get("id").in(genreMatch));
            }

            if (language != null) {
                predicates.add(cb.equal(root.get("language"), language));
            }

            if (titleStatus != null) {
                predicates.add(cb.equal(root.get("titleStatus"), titleStatus));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
