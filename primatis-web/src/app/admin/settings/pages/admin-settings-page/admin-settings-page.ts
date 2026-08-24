import { Component, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';

import { AuthService } from '../../../../auth/services/auth.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { SettingResponse } from '../../../../settings/models/setting-response';
import { SettingApiService } from '../../../../settings/services/setting-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { SettingValueEditDialog } from '../../components/setting-value-edit-dialog/setting-value-edit-dialog';

/**
 * Consultation Admin des six paramètres applicatifs existants (DEV-12.3,
 * `SETTING_READ`, `GET /api/v1/settings`) et modification de leur seule
 * valeur (`SETTING_MANAGE`, via {@link SettingValueEditDialog}). Même
 * pattern principal que `StaffFinesPage`/`AdminUsersPage` pour les états
 * chargement/erreur/vide, mais liste statique non paginée (collection fixe
 * à six lignes, ordre déjà déterministe côté backend — aucun tri client,
 * aucun `p-table` en mode `lazy`).
 *
 * `SETTING_READ` conditionne déjà l'accès à la route entière
 * (`permissionGuard`) ; `SETTING_MANAGE` est vérifiée ici uniquement pour
 * révéler/masquer la colonne Actions et le bouton Modifier — UX seulement,
 * le backend reste l'autorité (`@PreAuthorize` sur
 * `ApplicationSettingService.updateSettingValue`). Aucune création,
 * aucune suppression, aucun changement de type : `settingKey`/`valueType`/
 * `description` ne sont jamais modifiables (mandat DEV-12.3 §10).
 *
 * <p>Après une modification réussie, la ligne est remplacée par le {@link
 * SettingResponse} exact renvoyé par le backend — jamais reconstruite
 * localement (même précédent que `StaffFinesPage`).
 */
@Component({
  selector: 'app-admin-settings-page',
  imports: [TableModule, ButtonModule, LoadingState, EmptyState, ErrorState, SettingValueEditDialog],
  templateUrl: './admin-settings-page.html',
  styleUrl: './admin-settings-page.scss',
})
export class AdminSettingsPage {
  private readonly settingApiService = inject(SettingApiService);
  private readonly authService = inject(AuthService);

  readonly rows = signal<SettingResponse[]>([]);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur) — même principe que StaffFinesPage/AdminUsersPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly editingSetting = signal<SettingResponse | null>(null);

  constructor() {
    this.load();
  }

  get canManageSettings(): boolean {
    return this.authService.hasPermission('SETTING_MANAGE');
  }

  retry(): void {
    this.load();
  }

  edit(setting: SettingResponse): void {
    if (!this.canManageSettings) {
      return;
    }
    this.editingSetting.set(setting);
  }

  closeDialog(): void {
    this.editingSetting.set(null);
  }

  onSaved(updated: SettingResponse): void {
    this.rows.set(this.rows().map((row) => (row.settingKey === updated.settingKey ? updated : row)));
    this.editingSetting.set(null);
  }

  /**
   * `updatedByUser === null` est le seul signal réellement nullable côté
   * backend (`updatedAt` est toujours renseigné dès le bootstrap Flyway,
   * DEV-12.1 §8) — voir la note sur {@link SettingResponse}.
   */
  auditLabel(setting: SettingResponse): string {
    if (setting.updatedByUser === null) {
      return 'Jamais modifié';
    }
    return `${setting.updatedAt} — ${setting.updatedByUser.firstName} ${setting.updatedByUser.lastName}`;
  }

  valueTypeLabel(setting: SettingResponse): string {
    switch (setting.valueType) {
      case 'INTEGER':
        return 'Entier';
      case 'DECIMAL':
        return 'Décimal';
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.settingApiService.getSettings().subscribe({
      next: (response) => {
        this.rows.set(response);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
