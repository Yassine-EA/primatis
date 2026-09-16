package be.primatis.e2e.web;

import be.primatis.e2e.E2eTimeService;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôle du temps E2E (DEV-DEC-0070) — {@code @Profile("e2e")} :
 * n'existe tout simplement pas (aucun bean, aucune route enregistrée)
 * sous {@code default}/{@code dev}/{@code test}/{@code production}/
 * {@code preview} (contrainte 1). Reste mince : mapping HTTP, délégation
 * à {@link E2eTimeService} — l'autorisation ({@code hasAuthority(
 * 'ROLE_ADMIN')}) est appliquée sur le Service, jamais ici, même
 * convention que le reste du backend PRIMATIS (backend.md « Architecture »).
 */
@RestController
@RequestMapping("/api/v1/e2e/time")
@Profile("e2e")
public class E2eTimeController {

    private final E2eTimeService e2eTimeService;

    public E2eTimeController(E2eTimeService e2eTimeService) {
        this.e2eTimeService = e2eTimeService;
    }

    @GetMapping
    public E2eTimeResponse currentState() {
        return e2eTimeService.currentState();
    }

    @PostMapping("/advance")
    public E2eTimeResponse advance(@Valid @RequestBody AdvanceTimeRequest request) {
        return e2eTimeService.advance(request.hours());
    }

    @PostMapping("/reset")
    public E2eTimeResponse reset() {
        return e2eTimeService.reset();
    }
}
