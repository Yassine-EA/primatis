import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-forbidden',
  imports: [RouterLink, ButtonModule],
  template: `
    <section>
      <h1>Accès interdit</h1>
      <p>Vous ne disposez pas des autorisations nécessaires pour accéder à cette page.</p>

      <p-button
        label="Retour à l'accueil"
        routerLink="/"
      />
    </section>
  `,
})
export class Forbidden {}
