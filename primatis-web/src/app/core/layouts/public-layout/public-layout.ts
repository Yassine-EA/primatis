import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { PublicShell } from './public-shell/public-shell';

@Component({
  selector: 'app-public-layout',
  imports: [RouterOutlet, PublicShell],
  template: `
    <app-public-shell>
      <router-outlet />
    </app-public-shell>
  `,
})
export class PublicLayout {}
