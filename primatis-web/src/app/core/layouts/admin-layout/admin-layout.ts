import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Navigation } from '../../../shared/navigation/navigation';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, Navigation],
  template: `
    <div class="layout-shell">
      <header class="layout-header">
        <strong>Administration</strong>
        <app-navigation />
      </header>

      <main class="layout-content">
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './admin-layout.scss',
})
export class AdminLayout {}
