package be.primatis.article;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * Pré-vérification d'unicité avant création (DEV-11.9, {@code
     * TagService.createTag}) — même précédent exact que {@code
     * GenreRepository.existsByCode} : permet de refuser proprement un
     * doublon ({@code 409 TAG_CODE_ALREADY_EXISTS}) plutôt que de laisser
     * remonter {@code uq_tag_code} comme {@code DataIntegrityViolationException}
     * brute. {@code Tag.label} n'est jamais unique en base (contrairement à
     * {@code Genre.label}, V001) — aucune méthode équivalente pour
     * {@code label}.
     */
    boolean existsByCode(String code);
}
