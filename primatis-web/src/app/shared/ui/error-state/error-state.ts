import { Component, input, output } from '@angular/core';

/**
 * État d'erreur générique et accessible (DEV-04.11) : n'affiche jamais de
 * détail technique (code, stack trace, exception backend) — uniquement un
 * message destiné à l'utilisateur (typiquement `AppError.message`,
 * `api-error.util.ts`).
 *
 * Le bouton "Réessayer" n'apparaît que si le consommateur le demande
 * explicitement via `showRetry` — pas de retry automatique.
 */
@Component({
  selector: 'app-error-state',
  templateUrl: './error-state.html',
  styleUrl: './error-state.scss',
})
export class ErrorState {
  readonly message = input.required<string>();
  readonly showRetry = input(false);

  readonly retry = output<void>();
}
