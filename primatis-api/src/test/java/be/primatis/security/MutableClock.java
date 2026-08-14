package be.primatis.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Clock de test dont l'instant courant peut être avancé explicitement.
 * Permet de tester déterministement des règles temporelles (verrouillage 15
 * minutes) sans Thread.sleep().
 */
class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    MutableClock(Instant initialInstant, ZoneId zone) {
        this.instant = initialInstant;
        this.zone = zone;
    }

    void setInstant(Instant instant) {
        this.instant = instant;
    }

    void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
