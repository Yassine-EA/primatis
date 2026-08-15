package be.primatis.web;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Contrat REST générique de pagination PRIMATIS (PRIMATIS_CONTEXT_DEV_v1.0
 * §10.13), réutilisable par tout endpoint de liste paginée. Remplace
 * volontairement la sérialisation directe de {@link Page} (structure Jackson
 * de {@code PageImpl} non stable, expose des détails internes {@code
 * pageable}/{@code sort} non prévus par le contrat).
 *
 * Noms de propriétés stables une fois implémentés (§10.13) : ne pas
 * renommer sans nouvelle décision explicite.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
