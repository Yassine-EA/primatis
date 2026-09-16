import { Component, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

/**
 * Page éditoriale dédiée à Georges Lemaître (DEV-15.4 §10, refondue
 * DESIGN-V2-B5). Contenu strictement statique et factuel — aucun appel
 * réseau, aucune règle métier. Faits vérifiés auprès des sources listées
 * dans le template (UCLouvain — Archives Georges Lemaître, ESA, Wikimedia
 * Commons pour le portrait) ; aucune génération IA utilisée comme source
 * historique, aucune recherche web externe effectuée pour cette refonte.
 *
 * Ordre 1927/1931 (Univers en expansion / atome primitif) : conforme au
 * contenu déjà validé dans ce fichier avant refonte — la maquette
 * `docs/design/public/georges-lemaitre.png` inverse ces deux dates,
 * contradiction traitée conformément à CLAUDE.md §4 (DO NOT CONTRADICT) en
 * conservant le contenu déjà sourcé plutôt que l'exemple illustratif de la
 * maquette, documentée dans `.claude/logs/DESIGN-V2-B5 — GEORGES
 * LEMAITRE.md`.
 *
 * Aucune citation attribuée à Georges Lemaître n'est utilisée nulle part
 * sur cette page : ni celles de la maquette ("Un même ciel nous
 * rassemble.", "Entre les livres et les étoiles...", "L'Univers n'est pas
 * seulement plus étrange...") ni aucune autre — aucune n'est documentée
 * avec une source dans ce dépôt (même constat que DESIGN-V2-B1 §7 pour une
 * citation similaire, corrigée à l'époque dans le footer public).
 */
@Component({
  selector: 'app-georges-lemaitre',
  imports: [RouterLink],
  templateUrl: './georges-lemaitre.html',
  styleUrl: './georges-lemaitre.scss',
})
export class GeorgesLemaitrePage {
  private readonly titleService = inject(Title);

  constructor() {
    this.titleService.setTitle('Georges Lemaître — PRIMATIS');
  }

  /**
   * Un simple `<a href="#lemaitre-reperes">` est mal résolu par le Router
   * Angular en présence de `<base href="/">` (`index.html`) : un lien
   * "fragment seul" se résout contre la base plutôt que contre l'URL
   * courante, ce qui navigue réellement vers `/#lemaitre-reperes` (donc
   * vers la Home) au lieu de rester sur `/georges-lemaitre`. Défilement
   * manuel ciblé plutôt qu'une fonctionnalité de scroll de router globale
   * (`withInMemoryScrolling`), qui changerait le comportement de toutes les
   * routes de l'application pour un seul lien de cette page.
   */
  scrollToReperes(event: Event): void {
    event.preventDefault();
    document.getElementById('lemaitre-reperes')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
