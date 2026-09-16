import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { StaffAdminShell } from '../staff-admin-shell/staff-admin-shell';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, StaffAdminShell],
  template: `
    <app-staff-admin-shell zoneTitle="Administration">
      <router-outlet />
    </app-staff-admin-shell>
  `,
})
export class AdminLayout {}
