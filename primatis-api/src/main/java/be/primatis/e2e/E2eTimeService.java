package be.primatis.e2e;

import be.primatis.e2e.web.E2eTimeResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Contrôle du temps perçu par l'application sous le profil {@code e2e}
 * (DEV-DEC-0070) — {@code advance}/{@code reset} sur {@link
 * E2eAdjustableClock}, jamais une mutation directe de {@code Loan}/{@code
 * Reservation}/{@code Fine} : seul le {@code Clock} lu par les Services
 * métier réels ({@code LoanService}, {@code FineService}, ...) est
 * déplacé, les workflows applicatifs restent seuls responsables de
 * produire l'état transactionnel (DEV-DEC-0068, non modifiée).
 *
 * {@code @PreAuthorize("hasAuthority('ROLE_ADMIN')")} sur chaque méthode
 * (contrainte 6, DEV-DEC-0070) : vérification directe du rôle JWT plutôt
 * qu'une permission métier — ce contrôle est une capacité technique
 * réservée aux tests E2E, jamais une des 19 permissions canoniques V1
 * (RBAC métier FIGÉ, business-rules.md §6.3) ; l'inventer comme une
 * permission laisserait croire, à tort, qu'elle appartient au périmètre
 * métier réel. {@link E2eTimeController} n'existe de toute façon que sous
 * {@code @Profile("e2e")} — cette vérification est une seconde ligne de
 * défense, jamais la seule.
 *
 * {@code @Profile("e2e")} indispensable ici aussi (pas seulement sur
 * {@link E2eTimeController}) : sans cette annotation, Spring tenterait
 * d'instancier ce {@code @Service} sous tout profil (y compris
 * {@code test}/{@code default}), échouant à injecter {@link
 * E2eAdjustableClock} (bean absent hors {@code e2e}) — erreur constatée
 * et corrigée lors de l'implémentation (DEV-14.7 reprise).
 */
@Service
@Profile("e2e")
public class E2eTimeService {

    private final E2eAdjustableClock clock;

    public E2eTimeService(E2eAdjustableClock clock) {
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public E2eTimeResponse currentState() {
        return toResponse();
    }

    /** {@code hours} peut être négatif ou positif — reste un décalage relatif, jamais un instant absolu. */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public E2eTimeResponse advance(long hours) {
        clock.advance(Duration.ofHours(hours));
        return toResponse();
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public E2eTimeResponse reset() {
        clock.reset();
        return toResponse();
    }

    private E2eTimeResponse toResponse() {
        return new E2eTimeResponse(clock.instant(), clock.currentOffset().getSeconds());
    }
}
