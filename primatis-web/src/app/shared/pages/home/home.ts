import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ArticleSummaryResponse } from '../../../articles/models/article-summary-response';
import { ArticleApiService } from '../../../articles/services/article-api.service';
import { AuthService } from '../../../auth/services/auth.service';
import { TitleResponse } from '../../../catalogue/models/title-response';
import { CatalogueApiService } from '../../../catalogue/services/catalogue-api.service';
import { AppError } from '../../../core/errors/api-error';
import { toAppError } from '../../../core/errors/api-error.util';
import { EmptyState } from '../../ui/empty-state/empty-state';
import { ErrorState } from '../../ui/error-state/error-state';
import { LoadingState } from '../../ui/loading-state/loading-state';

const HOME_ARTICLES_LIMIT = 3;
const HOME_TITLES_LIMIT = 6;

interface QuickAccessItem {
  readonly iconPath: string;
  readonly label: string;
  readonly description: string;
  readonly routerLink: string;
}

/**
 * Home publique (VISUAL-RESET-01) — reconstruction de la vitrine PRIMATIS
 * depuis `docs/design/public/home.png`, sans conserver la composition
 * DEV-15. Aucun nouvel
 * endpoint : réutilise exclusivement les contrats publics déjà exposés
 * (`ArticleApiService.listPublishedArticles`,
 * `CatalogueApiService.searchTitles`) et l'état d'authentification déjà
 * exposé par `AuthService` pour l'accès rapide contextuel.
 *
 * Écarts assumés par rapport à la maquette : aucune route ou donnée fictive
 * pour les horaires, services, agenda et réseaux sociaux ; pas d'auteur
 * sur les cartes "Sélection du catalogue" (absent de
 * `TitleResponse`), aucune citation attribuée à Georges Lemaître (aucune
 * citation visible dans la maquette n'étant documentée comme authentique).
 * La recherche du hero transmet réellement `q` à `/catalogue`.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink, DatePipe, LoadingState, EmptyState, ErrorState],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly articleApiService = inject(ArticleApiService);
  private readonly catalogueApiService = inject(CatalogueApiService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  private static readonly ARTICLE_VISUALS = [
    '/assets/shared/editorial/editorial-cosmos-960.webp',
    '/assets/shared/editorial/editorial-library-960.webp',
    '/assets/shared/editorial/editorial-observatory-960.webp',
  ] as const;

  readonly articles = signal<ArticleSummaryResponse[]>([]);
  readonly articlesLoading = signal(true);
  readonly articlesError = signal<AppError | null>(null);

  readonly titles = signal<TitleResponse[]>([]);
  readonly titlesTotalCount = signal<number | null>(null);
  readonly titlesLoading = signal(true);
  readonly titlesError = signal<AppError | null>(null);

  /**
   * Accès rapide contextuel (§14 de la mission) : jamais de `userId`
   * envoyé, seul l'état déjà exposé par `AuthService` (claims JWT locales)
   * pilote le libellé/la destination — aucune nouvelle règle d'autorisation.
   *
   * Méthode simple, pas un `computed()` : `AuthService.authenticated()`/
   * `hasRole()` sont volontairement de simples fonctions (pas des Signals),
   * réévaluées à chaque lecture car l'expiration d'un JWT dépend du temps
   * qui passe, pas d'un Signal (cf. commentaire `AuthService`) — un
   * `computed()` ici mettrait en cache un résultat qui ne se réévaluerait
   * jamais après le premier rendu.
   */
  accountQuickAccess(): QuickAccessItem {
    if (!this.authService.authenticated()) {
      return {
        iconPath: '/assets/icons/editorial/account.svg',
        // "Se connecter" (jamais "Connexion") : l'en-tête public
        // (`AccountMenu`) porte déjà un lien nommé exactement "Connexion" —
        // un second lien dont le nom accessible contiendrait le même mot
        // serait ambigu au clavier/lecteur d'écran (et rendait déjà
        // `getByRole('link', { name: 'Connexion' })` strict-mode ambigu
        // dans `auth-rbac.spec.ts`, cf. rapport DESIGN-V2-B1).
        label: 'Se connecter',
        description: 'Accédez à votre espace personnel PRIMATIS.',
        routerLink: '/login',
      };
    }
    if (this.authService.hasRole('ROLE_MEMBER')) {
      return {
        iconPath: '/assets/icons/editorial/account.svg',
        label: 'Mon espace',
        description: 'Suivez vos prêts, réservations et amendes.',
        routerLink: '/member/profile',
      };
    }
    return {
      iconPath: '/assets/icons/editorial/account.svg',
      label: 'Espace personnel',
      description: 'Accédez à vos outils de gestion PRIMATIS.',
      routerLink: '/staff/users',
    };
  }

  searchCatalogue(event: Event, rawQuery: string): void {
    event.preventDefault();
    const query = rawQuery.trim();
    void this.router.navigate(['/catalogue'], query ? { queryParams: { q: query } } : undefined);
  }

  articleVisual(index: number): string {
    return Home.ARTICLE_VISUALS[index % Home.ARTICLE_VISUALS.length];
  }

  constructor() {
    this.loadArticles();
    this.loadTitles();
  }

  retryArticles(): void {
    this.loadArticles();
  }

  retryTitles(): void {
    this.loadTitles();
  }

  private loadArticles(): void {
    this.articlesLoading.set(true);
    this.articlesError.set(null);

    this.articleApiService.listPublishedArticles(0, HOME_ARTICLES_LIMIT).subscribe({
      next: (response) => {
        this.articles.set(response.content);
        this.articlesLoading.set(false);
      },
      error: (err: unknown) => {
        this.articlesLoading.set(false);
        this.articlesError.set(toAppError(err));
      },
    });
  }

  private loadTitles(): void {
    this.titlesLoading.set(true);
    this.titlesError.set(null);

    this.catalogueApiService.searchTitles({ page: 0, size: HOME_TITLES_LIMIT }).subscribe({
      next: (response) => {
        this.titles.set(response.content);
        this.titlesTotalCount.set(response.totalElements);
        this.titlesLoading.set(false);
      },
      error: (err: unknown) => {
        this.titlesLoading.set(false);
        this.titlesError.set(toAppError(err));
      },
    });
  }
}
