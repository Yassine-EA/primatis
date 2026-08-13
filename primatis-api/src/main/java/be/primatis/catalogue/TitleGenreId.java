package be.primatis.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TitleGenreId implements Serializable {

    @Column(name = "genre_id")
    private Long genreId;

    @Column(name = "title_id")
    private Long titleId;

    protected TitleGenreId() {
    }

    public TitleGenreId(Long genreId, Long titleId) {
        this.genreId = genreId;
        this.titleId = titleId;
    }

    public Long getGenreId() {
        return genreId;
    }

    public Long getTitleId() {
        return titleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TitleGenreId that)) {
            return false;
        }
        return Objects.equals(genreId, that.genreId) && Objects.equals(titleId, that.titleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(genreId, titleId);
    }
}
