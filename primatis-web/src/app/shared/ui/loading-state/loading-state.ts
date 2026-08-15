import { Component, input } from '@angular/core';

/**
 * État de chargement générique et accessible (DEV-04.11). `role="status"` +
 * `aria-live="polite"` : un lecteur d'écran annonce le message sans
 * interrompre l'utilisateur. Aucune dépendance PrimeNG — HTML/CSS natif
 * (budget bundle déjà tendu).
 */
@Component({
  selector: 'app-loading-state',
  templateUrl: './loading-state.html',
  styleUrl: './loading-state.scss',
})
export class LoadingState {
  readonly message = input('Chargement en cours…');
}
