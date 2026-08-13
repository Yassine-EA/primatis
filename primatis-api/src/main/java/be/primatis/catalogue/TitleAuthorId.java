package be.primatis.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TitleAuthorId implements Serializable {

    @Column(name = "title_id")
    private Long titleId;

    @Column(name = "author_id")
    private Long authorId;

    protected TitleAuthorId() {
    }

    public TitleAuthorId(Long titleId, Long authorId) {
        this.titleId = titleId;
        this.authorId = authorId;
    }

    public Long getTitleId() {
        return titleId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TitleAuthorId that)) {
            return false;
        }
        return Objects.equals(titleId, that.titleId) && Objects.equals(authorId, that.authorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(titleId, authorId);
    }
}
