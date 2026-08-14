import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-member-layout',
  imports: [RouterOutlet],
  template: `
    <div class="layout-shell">
      <header class="layout-header">
        <strong>Espace membre</strong>
      </header>

      <main class="layout-content">
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './member-layout.scss',
})
export class MemberLayout {}
