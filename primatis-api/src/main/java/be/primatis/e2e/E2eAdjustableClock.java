package be.primatis.e2e;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link Clock} ajustable réservé au profil {@code e2e} (DEV-DEC-0070).
 * Décalage relatif à un {@code Clock} de base réel ({@code
 * Clock.systemUTC()} en pratique, injecté par {@link E2eClockConfig}) —
 * jamais un instant absolu figé : {@code instant() = baseClock.instant()
 * + offset}. État initial {@code offset = Duration.ZERO}, donc identique
 * à {@code baseClock} tant qu'{@link #advance(Duration)} n'a pas été
 * appelé (contrainte 2 de DEV-DEC-0070) — les suites déjà validées
 * (DEV-14.2→14.6) restent inchangées.
 *
 * Thread-safe (contrainte 5) : {@code offset} est un {@link
 * AtomicReference}, jamais un champ mutable non protégé — ce Clock est un
 * bean Spring singleton, potentiellement lu par plusieurs threads
 * (requêtes HTTP concurrentes) même si son écriture ({@link #advance}/
 * {@link #reset}) reste, en pratique, exclusivement pilotée par une suite
 * Playwright sérialisée (DEV-DEC-0069, {@code workers = 1}).
 */
public final class E2eAdjustableClock extends Clock {

    private final Clock baseClock;
    private final AtomicReference<Duration> offset;

    public E2eAdjustableClock(Clock baseClock) {
        this(baseClock, new AtomicReference<>(Duration.ZERO));
    }

    private E2eAdjustableClock(Clock baseClock, AtomicReference<Duration> offset) {
        this.baseClock = Objects.requireNonNull(baseClock, "baseClock");
        this.offset = offset;
    }

    @Override
    public ZoneId getZone() {
        return baseClock.getZone();
    }

    /**
     * Conserve la même référence {@code offset} (partagée, jamais copiée)
     * pour qu'un décalage déjà appliqué reste visible depuis un Clock
     * obtenu via {@code withZone} — PRIMATIS cible exclusivement UTC en
     * pratique (aucun appel réel identifié dans le repository), ce cas
     * reste néanmoins correctement géré plutôt que silencieusement rompu.
     */
    @Override
    public Clock withZone(ZoneId zone) {
        return new E2eAdjustableClock(baseClock.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return baseClock.instant().plus(offset.get());
    }

    /** Avance le temps perçu d'une durée relative — jamais un instant absolu (contrainte 4, DEV-DEC-0070). */
    void advance(Duration duration) {
        offset.updateAndGet(current -> current.plus(duration));
    }

    /** Restaure {@code offset = Duration.ZERO} — le Clock redevient identique au temps UTC réel. */
    void reset() {
        offset.set(Duration.ZERO);
    }

    Duration currentOffset() {
        return offset.get();
    }
}
