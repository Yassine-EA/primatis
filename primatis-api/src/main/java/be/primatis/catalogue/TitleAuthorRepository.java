package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Consommateur réel depuis DEV-06.5 ({@code CatalogueService.createTitle}/
 * {@code updateTitle}) : calcul du delta entre les associations
 * {@code Author} actuellement attribuées à un {@code Title} et l'ensemble
 * final demandé (ajout/retrait) — même précédent exact que
 * {@code UserRoleRepository.findByIdUserId} (DEV-05.6). Absent avant
 * DEV-06.5 : aucune écriture sur {@code title_author} n'existait encore
 * (DEV-06.1/06.2/06.4 : lecture seule).
 */
public interface TitleAuthorRepository extends JpaRepository<TitleAuthor, TitleAuthorId> {

    List<TitleAuthor> findByIdTitleId(Long titleId);
}
