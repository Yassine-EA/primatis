import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Navigation } from '../../../shared/navigation/navigation';

@Component({
  selector: 'app-member-layout',
  imports: [RouterOutlet, Navigation],
  template: `
    <div class="layout-shell">
      <header class="layout-header">
        <strong>Espace membre</strong>
        <app-navigation />
      </header>

      <main class="layout-content">
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './member-layout.scss',
})
export class MemberLayout {}
