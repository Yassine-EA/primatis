import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-staff-layout',
  imports: [RouterOutlet],
  template: `
    <div class="layout-shell">
      <header class="layout-header">
        <strong>Espace personnel</strong>
      </header>

      <main class="layout-content">
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './staff-layout.scss',
})
export class StaffLayout {}
