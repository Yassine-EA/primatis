import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { PublicShell } from '../public-layout/public-shell/public-shell';
import { MemberNavigation } from './member-navigation/member-navigation';

/**
 * Shell de l'espace Member (DEV-15.3) — identité plus personnelle que
 * Staff/Admin (§3.2 du cadrage) : header simple (marque + compte), une
 * seule navigation à cinq entrées (`MemberNavigation`), pas de sidebar ni
 * de densité "outil métier". La route `/member` continue de rediriger
 * vers `/member/profile` (aucun dashboard Member introduit, §9).
 */
@Component({
  selector: 'app-member-layout',
  imports: [RouterOutlet, PublicShell, MemberNavigation],
  templateUrl: './member-layout.html',
  styleUrl: './member-layout.scss',
})
export class MemberLayout {}
