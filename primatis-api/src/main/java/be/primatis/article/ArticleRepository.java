package be.primatis.article;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * slug est l'identifiant public de navigation d'un Article
     * (GET /api/v1/articles/:slug).
     */
    Optional<Article> findBySlug(String slug);
}
