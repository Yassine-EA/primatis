import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

import { PublicShell } from '../../../core/layouts/public-layout/public-shell/public-shell';

/**
 * Page système (DEV-15.3, §15) : rattachée au shell public (`PublicShell`)
 * plutôt qu'affichée nue — même principe exact que `Forbidden`. Aucun
 * changement de route (`**` reste déclarée telle quelle dans
 * `app.routes.ts`).
 */
@Component({
  selector: 'app-not-found',
  imports: [RouterLink, ButtonModule, PublicShell],
  template: `
    <app-public-shell>
      <section>
        <h1>Page introuvable</h1>
        <p>La page demandée n'existe pas.</p>

        <p-button label="Retour à l'accueil" routerLink="/" />
      </section>
    </app-public-shell>
  `,
})
export class NotFound {}
