package be.primatis.catalogue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
