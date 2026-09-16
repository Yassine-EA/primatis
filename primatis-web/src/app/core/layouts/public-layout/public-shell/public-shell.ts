import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PublicNavigation } from '../public-navigation/public-navigation';

/**
 * Shell visuel du portail public, reconstruit par VISUAL-RESET-01 — barre
 * institutionnelle, header de marque, navigation, contenu et footer.
 * Réutilisé par `PublicLayout`
 * (via `<router-outlet>` projeté) et par `Forbidden`/`NotFound` (§15 du
 * cadrage : ces deux pages système ne doivent plus s'afficher nues, sans
 * dupliquer le shell). Aucune route n'est modifiée par cette réutilisation
 * — Forbidden/NotFound restent déclarées exactement comme avant dans
 * `app.routes.ts`, seul leur propre template change.
 *
 * Le logo est une image informative dont le texte alternatif porte le nom
 * complet. Le `<h1>` reste la responsabilité de chaque page projetée.
 */
@Component({
  selector: 'app-public-shell',
  imports: [RouterLink, PublicNavigation],
  templateUrl: './public-shell.html',
  styleUrl: './public-shell.scss',
})
export class PublicShell {
  /** Année du copyright pied de page (DESIGN-V2-A) — jamais codée en dur. */
  protected readonly currentYear = new Date().getFullYear();
}
