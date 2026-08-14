import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-public-layout',
  imports: [RouterOutlet],
  template: `
    <div class="layout-shell">
      <header class="layout-header">
        <strong>PRIMATIS</strong>
      </header>

      <main class="layout-content">
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './public-layout.scss',
})
export class PublicLayout {}
