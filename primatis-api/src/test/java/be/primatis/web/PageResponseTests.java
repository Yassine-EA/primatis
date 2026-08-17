package be.primatis.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie {@link PageResponse#from(org.springframework.data.domain.Page)} :
 * transfert exact des propriétés stables du contrat
 * (PRIMATIS_CONTEXT_DEV_v1.0 §10.13).
 */
class PageResponseTests {

    @Test
    void mapsContentAndMetadataFromSpringPage() {
        PageImpl<String> springPage = new PageImpl<>(
                List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(springPage);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void mapsEmptyPage() {
        PageImpl<String> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PageResponse<String> response = PageResponse.from(emptyPage);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
