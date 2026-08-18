package be.primatis.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Consommateur réel depuis DEV-06.5 ({@code CatalogueService.createTitle}/
 * {@code updateTitle}) : même besoin/même construction exacte que
 * {@link TitleAuthorRepository}, côté {@code Genre}.
 */
public interface TitleGenreRepository extends JpaRepository<TitleGenre, TitleGenreId> {

    List<TitleGenre> findByIdTitleId(Long titleId);
}
