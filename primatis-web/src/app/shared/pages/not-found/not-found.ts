import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, ButtonModule],
  template: `
    <section>
      <h1>Page introuvable</h1>
      <p>La page demandée n'existe pas.</p>

      <p-button
        label="Retour à l'accueil"
        routerLink="/"
      />
    </section>
  `,
})
export class NotFound {}
