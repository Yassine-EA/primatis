package be.primatis.catalogue;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "title_author")
public class TitleAuthor {

    @EmbeddedId
    private TitleAuthorId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("titleId")
    @JoinColumn(name = "title_id")
    private Title title;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("authorId")
    @JoinColumn(name = "author_id")
    private Author author;

    public TitleAuthor() {
    }

    public TitleAuthorId getId() {
        return id;
    }

    public void setId(TitleAuthorId id) {
        this.id = id;
    }

    public Title getTitle() {
        return title;
    }

    public void setTitle(Title title) {
        this.title = title;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }
}
