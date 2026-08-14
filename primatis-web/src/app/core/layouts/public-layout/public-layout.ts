import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Navigation } from '../../../shared/navigation/navigation';

@Component({
  selector: 'app-public-layout',
  imports: [RouterOutlet, Navigation],
  template: `
    <div class="layout-shell">
      <header class="layout-header">
        <strong>PRIMATIS</strong>
        <app-navigation />
      </header>

      <main class="layout-content">
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './public-layout.scss',
})
export class PublicLayout {}
