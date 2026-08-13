package be.primatis.article;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ArticleTagId implements Serializable {

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "tag_id")
    private Long tagId;

    protected ArticleTagId() {
    }

    public ArticleTagId(Long articleId, Long tagId) {
        this.articleId = articleId;
        this.tagId = tagId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public Long getTagId() {
        return tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArticleTagId that)) {
            return false;
        }
        return Objects.equals(articleId, that.articleId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(articleId, tagId);
    }
}
