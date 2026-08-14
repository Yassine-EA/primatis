import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet],
  template: `
    <div class="layout-shell">
      <header class="layout-header">
        <strong>Administration</strong>
      </header>

      <main class="layout-content">
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './admin-layout.scss',
})
export class AdminLayout {}
