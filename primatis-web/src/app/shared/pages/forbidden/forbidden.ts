import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

import { PublicShell } from '../../../core/layouts/public-layout/public-shell/public-shell';

/**
 * Page système (DEV-15.3, §15) : rattachée au shell public (`PublicShell`)
 * plutôt qu'affichée nue — aucun changement de route ni de guard, seul le
 * template change (aucune sécurité ne dépend de cet affichage : le backend
 * Spring Security a déjà tranché avant que cette page ne s'affiche).
 */
@Component({
  selector: 'app-forbidden',
  imports: [RouterLink, ButtonModule, PublicShell],
  template: `
    <app-public-shell>
      <section>
        <h1>Accès interdit</h1>
        <p>Vous ne disposez pas des autorisations nécessaires pour accéder à cette page.</p>

        <p-button label="Retour à l'accueil" routerLink="/" />
      </section>
    </app-public-shell>
  `,
})
export class Forbidden {}
