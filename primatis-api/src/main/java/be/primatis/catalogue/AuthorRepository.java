package be.primatis.catalogue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    /**
     * Recherche partielle insensible à la casse (DEV-06.4 : filtre "auteur"
     * de la recherche catalogue résolvant un nom vers un {@code authorId} ;
     * DEV-06.5 : autocomplétion staff évitant la création d'un doublon
     * d'Author). Requête dérivée Spring Data standard — aucun risque de
     * paramètre {@code IS NULL} non typé : le contrat exige un
     * {@code fullName} non {@code null} (un {@code null} produirait un
     * {@code LIKE} systématiquement faux, jamais une exception, mais aucun
     * cas d'usage identifié n'appelle cette méthode sans terme de recherche —
     * utiliser {@code findAll(Pageable)} hérité dans ce cas).
     */
    Page<Author> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);

    /**
     * Authors d'un Title, en une seule requête (DEV-06.4, consultation
     * détaillée — {@code TitleDetailResponse} exige des collections que
     * {@code Title} ne porte pas nativement, cf. audit DEV-06.1/06.2 : aucune
     * collection inverse vers {@code TitleAuthor}). {@code SELECT ta.author}
     * traverse directement la table d'association, sans charger les Authors
     * un par un. Aucune ambiguïté de duplication possible : la clé composite
     * {@code (title_id, author_id)} de {@code title_author} garantit au plus
     * une ligne par {@code (titleId, authorId)}. Ordre déterministe
     * ({@code fullName ASC, id ASC}) — IMPLEMENTATION FREEDOM, aucune règle
     * métier ne fixe cet ordre, tie-break sur {@code id} pour les homonymes.
     */
    @Query("SELECT ta.author FROM TitleAuthor ta WHERE ta.title.id = :titleId "
            + "ORDER BY ta.author.fullName ASC, ta.author.id ASC")
    List<Author> findByTitleIdOrderByFullNameAscIdAsc(@Param("titleId") Long titleId);
}
