# PRIMATIS — Spécification d’implémentation Angular / PrimeNG

**Version :** 2.0  
**Statut :** Référence d’implémentation frontend  
**Stack cible :** Angular 21 / TypeScript / PrimeNG 21 / SCSS  
**Application :** PRIMATIS — Bibliothèque publique Georges Lemaître

---

# 1. Objet du document

Ce document définit comment traduire le Design System PRIMATIS v2 et les
maquettes visuelles validées dans l'application Angular réelle.

Il complète :

- `docs/design/README.md`
- `docs/design/design-system-v2.md`
- les maquettes présentes sous `docs/design/public/`
- les maquettes présentes sous `docs/design/member/`
- les maquettes présentes sous `docs/design/staff-admin/`

Les maquettes constituent la référence visuelle.

Ce document constitue la référence technique.

Aucun des deux ne remplace l'autre.

---

# 2. Principe fondamental

L'implémentation Angular doit reproduire fidèlement :

- la hiérarchie visuelle ;
- la densité ;
- les proportions ;
- les surfaces ;
- la typographie ;
- les contrastes ;
- les rythmes ;
- les compositions ;
- l'identité PRIMATIS ;

des maquettes validées.

PrimeNG est un outil d'implémentation.

PrimeNG ne définit pas la direction artistique.

Il est interdit de réduire une maquette riche à un assemblage de composants
PrimeNG par défaut.

---

# 3. Règle de fidélité visuelle

Pour chaque écran :

1. ouvrir la maquette correspondante ;
2. analyser sa structure visuelle ;
3. analyser le composant Angular existant ;
4. analyser les données réellement disponibles ;
5. établir un mapping maquette → données réelles ;
6. implémenter ;
7. lancer l'application réelle ;
8. capturer le rendu desktop ;
9. capturer le rendu mobile ;
10. comparer les captures à la maquette ;
11. corriger les écarts ;
12. obtenir une validation visuelle.

Les tests automatisés ne remplacent jamais les étapes 8 à 12.

---

# 4. Règle de données réelles

Les maquettes peuvent contenir des exemples illustratifs.

L'implémentation doit utiliser uniquement :

- API réelles ;
- DTO réels ;
- modèles réels ;
- routes réelles ;
- règles métier réelles ;
- données réellement disponibles.

Il est interdit d'inventer une donnée uniquement pour reproduire une maquette.

Exemple :

si la maquette affiche la disponibilité d'un ouvrage mais que le contrat API
public ne fournit pas cette disponibilité :

- ne pas inventer `Disponible` ;
- ne pas faire de requête N+1 artificielle ;
- adapter la composition ;
- conserver la richesse graphique autrement.

---

# 5. Langue

PRIMATIS V1 est uniquement en français.

Aucun système d'internationalisation ne doit être ajouté.

Ne pas introduire :

- ngx-translate ;
- Transloco ;
- Angular i18n ;
- sélecteur FR/EN/NL ;
- fichiers de traduction.

La langue d'un ouvrage reste une donnée bibliographique.

Exemples :

- Français
- Anglais
- Néerlandais
- Allemand

Cela ne constitue pas une langue d'interface.

---

# 6. Architecture visuelle générale

PRIMATIS possède quatre univers :

```text
Public
Member
Staff
Admin
```

Ils partagent la même identité mais pas exactement la même composition.

---

# 7. Univers Public

Objectifs :

- éditorial ;
- culturel ;
- institutionnel ;
- visuellement riche ;
- forte identité Georges Lemaître.

Composants structurants :

```text
PublicShell
PublicHeader
PublicNavigation
PublicFooter
AccountMenu
```

Pages principales :

```text
Home
Georges Lemaître
Catalogue
Détail Title
Articles
Détail Article
Login
```

Le Public est l'univers avec la plus forte richesse graphique.

---

# 8. Univers Member

Objectifs :

- personnel ;
- rassurant ;
- clair ;
- plus fonctionnel que Public ;
- identité PRIMATIS toujours visible.

Structure :

```text
MemberLayout
MemberNavigation
AccountMenu
```

Pages :

```text
Profil
Prêts
Réservations
Amendes
Notifications
```

La composition doit conserver un lien visuel clair avec le portail public.

---

# 9. Univers Staff

Objectifs :

- métier ;
- dense ;
- efficace ;
- rapide ;
- professionnel.

Structure principale :

```text
StaffAdminShell
```

Navigation :

- sidebar desktop/tablette ;
- drawer mobile.

Pages métier principales :

```text
Prêts
Réservations
Amendes
Catalogue
Titres
Exemplaires
Articles
Tags
Utilisateurs
```

---

# 10. Univers Admin

L'univers Admin utilise le même shell métier que Staff.

Pages principales :

```text
Utilisateurs
Création utilisateur
Détail utilisateur
Paramètres applicatifs
```

Il doit être visuellement cohérent avec Staff.

---

# 11. Composants Angular — philosophie

Créer des composants partagés uniquement lorsqu'ils apportent une vraie valeur.

Éviter les abstractions prématurées du type :

```text
GenericCardComponent
GenericTableComponent
GenericPageComponent
UniversalDialogComponent
```

si elles rendent les écrans rigides ou compliquent la fidélité aux maquettes.

Préférer :

- primitives simples ;
- composants ciblés ;
- composition Angular standard.

---

# 12. Primitives globales recommandées

Les primitives CSS existantes ou futures doivent couvrir :

```text
.page-shell
.page-header
.page-title
.page-description
.section-title
.content-container
.editorial-title
```

Elles définissent une base commune.

Elles ne doivent pas imposer la même composition à toutes les pages.

---

# 13. Tokens

Toutes les valeurs globales importantes doivent provenir du Design System.

Fichiers de référence :

```text
styles/
_design-tokens.scss
_breakpoints.scss
primitives.scss
```

Éviter les couleurs codées directement dans les composants.

Mauvais :

```scss
color: #123f5e;
```

Préféré :

```scss
color: var(--primatis-navy-700);
```

---

# 14. Variables CSS recommandées

Exemple de namespace :

```scss
:root {
  --primatis-navy-900: #082b40;
  --primatis-navy-800: #0b3650;
  --primatis-navy-700: #123f5e;
  --primatis-navy-600: #1a4f72;

  --primatis-copper-700: #8b4b26;
  --primatis-copper-600: #a65d2e;
  --primatis-copper-500: #b87333;
  --primatis-copper-300: #d7a16e;

  --primatis-ivory-50: #faf9f6;
  --primatis-ivory-100: #f6f1e8;
  --primatis-ivory-200: #ece5da;

  --primatis-gray-100: #f1f3f5;
  --primatis-gray-300: #d7dde3;
  --primatis-gray-500: #7c8795;
  --primatis-gray-700: #46515f;
  --primatis-gray-900: #1f2833;
}
```

Les valeurs finales doivent rester cohérentes avec `design-system-v2.md`.

---

# 15. PrimeNG Theme

PRIMATIS utilise PrimeNG comme bibliothèque de composants.

Le preset PrimeNG doit refléter le Design System PRIMATIS.

Le preset doit piloter en priorité :

- primary ;
- surfaces ;
- borders ;
- focus ;
- radius ;
- typography ;
- inputs ;
- buttons ;
- tables ;
- tags ;
- dialogs.

Éviter les overrides globaux massifs non maîtrisés.

---

# 16. PrimeNG Button

Composant :

```text
p-button
pButton
```

Utilisation :

### Primary métier

```text
Bleu nuit
```

### CTA éditorial public

```text
Cuivre
```

### Secondary

Fond clair + bordure.

### Danger

Rouge.

### Text

Action secondaire ou navigation.

---

# 17. Boutons — règles

Tous les boutons doivent avoir :

- label clair ou aria-label ;
- état hover ;
- état focus-visible ;
- état disabled ;
- état pending si action async.

Ne jamais désactiver un bouton sans expliquer l'action bloquée si cela crée
une ambiguïté.

---

# 18. PrimeNG Table

Composant :

```text
p-table
```

PrimeNG fournit :

- tri ;
- pagination ;
- lazy loading ;
- structure tabulaire ;
- scroll interne.

Le style visuel doit être PRIMATIS.

---

# 19. Tables Staff/Admin

Les tables doivent être :

- compactes ;
- lisibles ;
- hiérarchisées ;
- utilisables au clavier ;
- adaptées à la largeur disponible.

Header :

- contraste léger ;
- texte clair ;
- pas de gros header SaaS.

Actions :

- à droite ;
- accessibles ;
- visibles autant que possible.

---

# 20. Tables responsive

### 1440px

Toutes les colonnes importantes visibles.

### 1024px

Les actions métier principales doivent rester accessibles sans scroll si une
solution raisonnable existe.

### 768px

Le scroll horizontal interne PrimeNG est accepté pour les tables denses.

### 375px

Ne jamais provoquer un scroll horizontal global de la page.

Selon le contexte :

- scroll interne ;
- colonnes masquées ;
- rendu card/list.

---

# 21. PrimeNG Tag

Composant :

```text
p-tag
```

Utilisé pour :

- états métier ;
- statut publication ;
- compte ;
- prêt ;
- réservation ;
- amende ;
- titre.

Les mappings doivent rester centralisés lorsque cela apporte une vraie
cohérence.

---

# 22. Tags et accessibilité

Un statut ne doit jamais être indiqué uniquement par sa couleur.

Toujours afficher un label.

Exemple :

```text
Vert + "Disponible"
```

et non :

```text
pastille verte sans texte
```

---

# 23. PrimeNG Dialog

Composant :

```text
p-dialog
```

Utilisations :

- création prêt ;
- réservation ;
- exemplaire ;
- auteur ;
- genre ;
- tag ;
- paramètre ;
- autres formulaires modaux existants.

---

# 24. Dialog desktop

Le dialog doit avoir :

- largeur contrôlée ;
- header lisible ;
- body avec scrolling si nécessaire ;
- footer visible ;
- boutons alignés ;
- focus correct.

---

# 25. Dialog mobile

À 375px :

```text
width: calc(100vw - 32px)
```

ou comportement équivalent.

Éviter les dialogs minuscules centrés avec contenu compressé.

---

# 26. PrimeNG Drawer

Composant :

```text
p-drawer
```

Utilisations principales :

- navigation Public mobile ;
- navigation Staff/Admin mobile.

Le drawer doit :

- avoir un titre accessible ;
- conserver le focus ;
- fonctionner au clavier ;
- se fermer avec Escape ;
- restituer le focus correctement.

---

# 27. PrimeNG Tabs

Utilisé principalement dans Member si pertinent.

À mobile :

- scrolling horizontal autorisé ;
- indication visuelle qu'il existe du contenu hors viewport ;
- item actif visible autrement que par la couleur seule.

---

# 28. Toast

Composant :

```text
p-toast
```

Utilisé pour :

- succès ;
- erreur ;
- information ponctuelle.

Ne jamais afficher un toast de succès avant confirmation backend.

Éviter les doubles messages :

```text
toast + message inline identique
```

sans raison.

---

# 29. ConfirmDialog

Composant :

```text
p-confirmdialog
```

À utiliser pour les actions sensibles :

- retour ;
- annulation ;
- désactivation ;
- archivage ;
- actions destructives.

Le message doit décrire précisément l'action.

---

# 30. Inputs PrimeNG

Selon composants réellement présents :

```text
p-inputtext
p-select
p-inputnumber
p-textarea
p-password
```

Chaque champ doit conserver :

```text
label
control
hint éventuel
error éventuelle
```

---

# 31. Formulaires Angular

Utiliser Reactive Forms lorsque déjà prévu par l'architecture.

Ne pas introduire un second système de formulaire.

Validation :

- structure frontend ;
- format ;
- required ;
- feedback utilisateur.

Ne pas dupliquer les règles métier backend complexes.

---

# 32. États pending

Pour chaque action réseau :

```text
pending = true
```

doit empêcher les doubles soumissions.

Exemple :

```html
<p-button
  [loading]="isSaving()"
  [disabled]="isSaving()"
  label="Enregistrer"
/>
```

ou mécanisme équivalent.

---

# 33. Shared states

Les composants partagés existants doivent être conservés :

```text
LoadingState
EmptyState
ErrorState
```

Ils doivent rester graphiquement intégrés au Design System v2.

---

# 34. Loading State

Pour une page complète :

- skeleton si pertinent ;
- sinon composant LoadingState.

Éviter un gros spinner isolé au milieu d'une page riche.

---

# 35. Empty State

Doit comporter :

- titre ;
- explication contextualisée ;
- CTA éventuel.

Exemple :

```text
Aucune réservation en cours
Vos prochaines réservations apparaîtront ici.
```

---

# 36. Error State

Doit comporter :

- message clair ;
- possibilité de réessayer si l'action est pertinente ;
- `role="alert"` ou sémantique adaptée.

---

# 37. Images

Les assets validés doivent vivre sous un emplacement stable, par exemple :

```text
public/assets/
```

ou convention Angular actuelle.

Chaque image doit posséder :

- alt pertinent ;
- dimensions ;
- ratio maîtrisé ;
- fallback si nécessaire.

---

# 38. Portrait Georges Lemaître

Le portrait validé est une composante identitaire majeure.

Il doit être réutilisé avec parcimonie :

- Home ;
- page Georges Lemaître ;
- éventuellement petits rappels institutionnels.

Ne pas le répéter sur toutes les pages.

---

# 39. Iconographie

Utiliser PrimeIcons lorsque possible.

Interdit :

- emojis système ;
- mélange incohérent de plusieurs sets d'icônes.

Taille typique :

```text
18px
20px
24px
```

---

# 40. PublicShell

Le `PublicShell` doit comporter :

```text
Header
Navigation
Main
Footer
```

Le Footer fait partie intégrante de l'identité publique.

Ne pas conserver une page publique qui se termine brutalement après son
contenu principal.

---

# 41. Public Header

Desktop :

- logo ;
- nom bibliothèque ;
- navigation ;
- recherche ou accès recherche ;
- compte.

Mobile :

- identité compacte ;
- compte ;
- menu.

Le header ne doit pas devenir disproportionné.

---

# 42. Public Footer

Le footer peut contenir selon données réellement connues :

- identité PRIMATIS ;
- navigation ;
- services ;
- informations institutionnelles ;
- mentions légales ;
- accessibilité ;
- citation.

Ne pas inventer :

- horaires ;
- téléphone ;
- adresse ;
- réseaux sociaux ;

si ces informations ne sont pas réellement validées.

---

# 43. Home — mapping technique

Référence :

```text
docs/design/public/home.png
```

Structure cible :

```text
PublicHeader
Hero
Search
QuickAccess
FeaturedArticles
CatalogueHighlights
LemaitreSection
InstitutionalFooter
```

---

# 44. Home — données réelles

Utiliser :

- articles PUBLISHED ;
- catalogue réel ;
- contenu Georges Lemaître validé ;
- routes réelles.

Ne pas inventer :

- événements ;
- agenda ;
- newsletter ;
- horaires ;
- statistiques fictives.

Si les dernières acquisitions ne sont pas directement disponibles :

adapter la section à des titres réels retournés par l'API.

---

# 45. Catalogue — mapping technique

Référence :

```text
docs/design/public/catalogue.png
```

Structure :

```text
HeroCatalogue
SearchBar
Filters
ResultCount
BookResults
Pagination
Footer
```

Filtres uniquement si les API réelles les supportent.

---

# 46. Catalogue — filtres

Avant implémentation :

inspecter le contrat réel.

Ne jamais créer un filtre UI dont le backend ne supporte pas réellement le
paramètre.

Les filtres visuels de la maquette doivent être adaptés au contrat.

---

# 47. Détail Title

Référence :

```text
docs/design/public/title-detail.png
```

Structure possible :

```text
Breadcrumb
BookHero
Cover
Metadata
Authors
Genres
Summary
ReservationCTA
RelatedSection éventuelle
```

Ne pas afficher de disponibilité inventée.

---

# 48. Reservation CTA

Selon contexte réel :

### Anonymous

```text
Se connecter pour réserver
```

avec retour vers la page courante si mécanisme existant.

### Member

```text
Réserver
```

si autorisé.

### Staff/Admin

Pas de CTA Member automatique.

---

# 49. Articles

Référence :

```text
docs/design/public/articles.png
```

Structure :

```text
ArticlesHero
FeaturedArticle
ArticleGrid/List
Pagination
Footer
```

Utiliser uniquement les articles PUBLISHED côté public.

---

# 50. Georges Lemaître

Référence :

```text
docs/design/public/georges-lemaitre.png
```

Cette page bénéficie d'un traitement spécifique.

Composants possibles :

```text
LemaitreHero
QuoteBlock
BiographySection
Timeline
ExpansionSection
PrimitiveAtomSection
CharleroiSection
LegacySection
SourcesSection
```

Il n'est pas nécessaire de mutualiser ces composants si leur usage est
exclusif à cette page.

---

# 51. Georges Lemaître — règle qualitative

Cette page doit être la page la plus travaillée visuellement du portail.

La fidélité à la maquette est prioritaire.

Elle ne doit jamais être réduite à :

```text
<h1>
<p>
<img>
<h2>
<p>
<h2>
<p>
```

---

# 52. Login

Référence :

```text
docs/design/public/login.png
```

L'écran doit rester simple mais identitaire.

Structure :

```text
IdentityPanel
LoginCard
Email
Password
Submit
Error
```

Ne pas ajouter :

- inscription publique ;
- OAuth ;
- mot de passe oublié ;

si ces fonctions ne sont pas réellement présentes dans PRIMATIS.

---

# 53. Member Layout

Référence graphique :

```text
docs/design/member/
```

L'espace Member doit garder :

- identité PRIMATIS ;
- navigation claire ;
- contexte utilisateur ;
- mise en page plus fonctionnelle.

---

# 54. Member Profile

Référence :

```text
docs/design/member/profile.png
```

Mapper uniquement les champs réellement présents dans les DTO.

Ne pas créer :

- préférences inexistantes ;
- historique inexistant ;
- métriques fictives.

---

# 55. Member Loans

Référence :

```text
docs/design/member/loans.png
```

Respecter le contrat réel.

Notamment :

si le backend ne fournit pas actuellement le titre du livre dans
`LoanResponse`, ne pas effectuer un lookup N+1 artificiel pour imiter la
maquette.

Adapter la présentation.

---

# 56. Member Reservations

Référence :

```text
docs/design/member/reservations.png
```

Les statuts réels doivent gouverner :

- CTA ;
- couleur ;
- dates ;
- expiration READY.

Ne jamais recalculer les règles de réservation côté frontend.

---

# 57. Member Fines

Référence :

```text
docs/design/member/fines.png
```

Aucun paiement en ligne fictif.

Si PRIMATIS ne permet pas le paiement frontend :

ne pas afficher de bouton "Payer".

La maquette doit être adaptée.

---

# 58. Member Notifications

Référence :

```text
docs/design/member/notifications.png
```

Les catégories doivent provenir des vrais types Notification.

Unread :

- distinction visuelle ;
- distinction textuelle ;
- pas uniquement couleur.

---

# 59. StaffAdminShell

Référence générale :

```text
docs/design/staff-admin/
```

Structure :

```text
Sidebar
Topbar
MainContent
```

Le shell doit rester compact.

Aucun gros hero éditorial sur les pages métier normales.

---

# 60. Sidebar

Éléments :

- identité PRIMATIS ;
- navigation ;
- état actif ;
- retour portail public ;
- éventuel élément institutionnel discret.

Le cuivre peut souligner la route active.

---

# 61. Dashboard Staff/Admin

Attention :

ne créer un dashboard que si les données nécessaires existent réellement.

Les maquettes Staff/Admin peuvent servir de langage visuel sans imposer la
création de statistiques inexistantes.

---

# 62. Staff Loans

Conserver les workflows métier déjà fonctionnels.

La refonte est visuelle.

Ne modifier :

- endpoints ;
- commandes ;
- règles ;
- transitions ;

que si une décision métier indépendante l'exige.

---

# 63. Staff Reservations

Même principe :

maquette → composition.

Backend → autorité métier.

---

# 64. Staff Fines

Ne pas inventer :

- paiement ;
- calcul client ;
- suppression métier non autorisée.

---

# 65. Staff Catalogue / Copies

Les tableaux peuvent être enrichis visuellement avec :

- couverture ;
- metadata ;
- tags ;
- statuts ;

uniquement si les DTO les fournissent déjà sans requêtes excessives.

---

# 66. Articles / Tags

L'éditeur et les pages de gestion doivent conserver leur fonctionnement.

La refonte doit améliorer :

- hiérarchie ;
- formulaire ;
- actions ;
- metadata ;
- statuts.

---

# 67. Users

Références :

```text
staff-admin/users.png
staff-admin/user-detail.png
staff-admin/user-create.png
```

Les maquettes doivent être adaptées aux vrais champs :

- email ;
- statut compte ;
- statut membre ;
- rôles ;
- résidence ;
- données disponibles.

---

# 68. Application Settings

Ne montrer que les types réellement existants.

Si V1 supporte seulement :

```text
INTEGER
DECIMAL
```

ne pas créer d'UI BOOLEAN/STRING fictive.

---

# 69. Responsive

Breakpoints officiels :

```scss
mobile: < 768px;
tablet: >= 768px;
desktop: >= 1024px;
wide: >= 1440px;
```

Ne pas ajouter des breakpoints locaux sans nécessité démontrée.

---

# 70. Responsive — Visual Gate

Chaque écran doit être validé au minimum à :

```text
375px
1440px
```

Les écrans métier doivent également être contrôlés à :

```text
768px
1024px
```

---

# 71. Accessibilité

Cible pragmatique :

```text
WCAG 2.2 AA
```

La fidélité visuelle ne permet jamais de supprimer :

- labels ;
- focus ;
- contrastes ;
- aria ;
- headings corrects ;
- navigation clavier.

---

# 72. Headings

Une seule balise `<h1>` par page réelle.

Attention aux shells.

Les tests doivent parfois monter le shell + page ensemble pour détecter les
problèmes structurels transverses.

---

# 73. Document title

Chaque route principale doit définir un titre de document cohérent.

Exemples :

```text
Accueil — PRIMATIS
Catalogue — PRIMATIS
Georges Lemaître — PRIMATIS
Mes prêts — PRIMATIS
Prêts — PRIMATIS
Paramètres — PRIMATIS
```

---

# 74. Performance

La refonte graphique ne doit pas servir de prétexte pour :

- ajouter un framework CSS ;
- ajouter un second kit UI ;
- importer de lourdes bibliothèques ;
- charger des images énormes.

---

# 75. Images

Avant intégration :

- redimensionner ;
- compresser ;
- retirer métadonnées inutiles ;
- produire WebP/AVIF si pertinent.

Toujours conserver une qualité suffisante pour les grandes zones éditoriales.

---

# 76. Dépendances

Aucune nouvelle dépendance frontend par défaut.

Toute nouvelle dépendance doit être justifiée avant installation.

PrimeNG + PrimeIcons + Angular doivent couvrir l'essentiel.

---

# 77. SCSS

Utiliser :

- styles scoped de composant ;
- tokens globaux ;
- primitives ;
- variables PrimeNG.

Éviter :

- `::ng-deep` sauf nécessité démontrée ;
- `!important` répétés ;
- CSS global ciblant arbitrairement des composants particuliers.

---

# 78. Naming CSS

Préférer des noms sémantiques :

```text
catalogue-hero
book-card
member-summary
staff-table-actions
lematre-timeline
```

Éviter :

```text
blue-box
left-div
big-card
section2
```

---

# 79. Tests unitaires

Les tests doivent protéger :

- conditions UI ;
- mappings ;
- actions ;
- states ;
- logique responsive structurante si testable raisonnablement ;
- accessibilité structurelle.

Ne pas écrire de tests pixel-perfect.

---

# 80. Playwright

Les tests E2E doivent continuer à tester :

- workflows métier ;
- permissions ;
- navigation ;
- persistance réelle ;
- interactions critiques.

Ne pas transformer la suite officielle en système de comparaison visuelle
pixel-perfect.

---

# 81. QA visuelle

Pour la refonte, des scripts Playwright temporaires peuvent :

- ouvrir les routes ;
- capturer desktop ;
- capturer mobile ;
- vérifier overflow.

Ils doivent rester hors de la suite E2E officielle sauf intérêt durable.

---

# 82. Screenshots de validation

Chaque sous-DEV de refonte doit produire temporairement :

```text
reference
actual-desktop
actual-mobile
```

et comparer explicitement les trois.

---

# 83. Critères de comparaison

Comparer :

### Composition
La disposition générale correspond-elle ?

### Hiérarchie
Le regard est-il dirigé comme sur la maquette ?

### Densité
Le rendu n'est-il pas beaucoup plus vide ?

### Palette
Le bleu/cuivre/ivoire sont-ils réellement présents ?

### Typographie
Les niveaux sont-ils suffisamment proches ?

### Images
Les proportions et traitements sont-ils cohérents ?

### Responsive
La version mobile conserve-t-elle l'identité ?

---

# 84. Critère FAIL visuel

Une page est FAIL même avec tous les tests verts si :

- elle ressemble à un scaffold ;
- elle est très inférieure à la maquette ;
- la composition est absente ;
- les espaces sont disproportionnés ;
- l'identité PRIMATIS disparaît ;
- les composants PrimeNG dominent visuellement le design ;
- le mobile ressemble à un desktop simplement compressé.

---

# 85. Process obligatoire par page

```text
AUDIT
↓
MAQUETTE
↓
MAPPING API
↓
IMPLEMENTATION
↓
UNIT TESTS
↓
BUILD
↓
RUN APP
↓
SCREENSHOT 1440
↓
SCREENSHOT 375
↓
VISUAL COMPARISON
↓
CORRECTIONS
↓
RE-SCREENSHOT
↓
PLAYWRIGHT
↓
VISUAL VALIDATION
↓
PASS
```

---

# 86. Mapping préalable obligatoire

Avant de modifier une page, produire brièvement :

```text
MAQUETTE
- éléments présents

DONNÉES RÉELLES
- disponibles
- indisponibles

À IMPLÉMENTER
- ...

À ADAPTER
- ...

INTERDIT D'INVENTER
- ...
```

Ce mapping doit être conservé dans le rapport du sous-DEV.

---

# 87. Git

Les règles Git PRIMATIS restent applicables.

Claude Code ne doit pas :

- créer une branche ;
- créer un worktree ;
- commit ;
- push ;
- merge ;
- rebase ;
- cherry-pick ;
- reset ;
- stash ;
- clean ;
- modifier l'historique.

Travail uniquement dans le checkout courant.

Les modifications restent non committées.

---

# 88. Sous-agents

Par défaut :

aucun sous-agent d'écriture.

Si un sous-agent est exceptionnellement autorisé pour lecture :

il doit être strictement read-only.

Pour les chantiers de refonte visuelle, préférer une session principale unique
afin de maintenir une cohérence graphique.

---

# 89. Rapport de chaque étape

Chaque étape d'implémentation doit produire :

```text
.claude/logs/...
```

avec :

- référence visuelle utilisée ;
- mapping données ;
- fichiers modifiés ;
- décisions ;
- différences justifiées avec maquette ;
- tests ;
- build ;
- screenshots contrôlés ;
- responsive ;
- Playwright ;
- dettes.

---

# 90. Validation humaine

La validation finale visuelle appartient au développeur.

Claude Code ne peut pas déclarer qu'une page est visuellement approuvée par le
développeur.

Il peut seulement conclure :

```text
READY FOR VISUAL REVIEW
```

tant que le développeur n'a pas explicitement validé le rendu.

---

# 91. Ordre d'implémentation

Ordre recommandé :

## Phase A — Fondations

```text
A1 Tokens
A2 Theme PrimeNG
A3 Typography
A4 Public shell/header/footer
A5 Member shell
A6 Staff/Admin shell
```

## Phase B — Public

```text
B1 Home
B2 Catalogue
B3 Title Detail
B4 Articles
B5 Georges Lemaître
B6 Login
```

## Phase C — Member

```text
C1 Profile
C2 Loans
C3 Reservations
C4 Fines
C5 Notifications
```

## Phase D — Staff/Admin

```text
D1 Shell final
D2 Loans
D3 Reservations
D4 Fines
D5 Catalogue
D6 Copies
D7 Articles
D8 Tags
D9 Users
D10 User Detail
D11 User Create
D12 Settings
```

---

# 92. Pas de Big Bang

Ne pas refaire tout le frontend dans une seule session.

Chaque écran ou famille cohérente doit avoir :

- périmètre limité ;
- validation technique ;
- validation visuelle.

---

# 93. Conservation du fonctionnel

La refonte doit partir du frontend fonctionnel existant.

Ne pas recréer inutilement :

- services ;
- stores ;
- DTO ;
- routes ;
- dialogs ;
- guards ;
- API clients ;
- tests fonctionnels.

La priorité est le rendu.

---

# 94. Refonte et code métier

Si une maquette semble nécessiter une évolution métier :

STOP.

Documenter :

```text
CHANGE REQUIRES DECISION
```

Ne pas modifier le backend pour satisfaire visuellement une maquette.

---

# 95. Définition finale de DONE

Une page est DONE uniquement lorsque :

- fonctionnalité préexistante intacte ;
- données réelles utilisées ;
- tests verts ;
- build vert ;
- Playwright pertinent vert ;
- responsive validé ;
- accessibilité sans régression ;
- screenshot desktop contrôlé ;
- screenshot mobile contrôlé ;
- fidélité visuelle suffisante ;
- validation humaine obtenue.

---

# 96. Principe final

PRIMATIS ne doit plus être évalué uniquement comme une application qui
fonctionne.

Le frontend doit démontrer simultanément :

```text
Architecture
Fonctionnalité
UX
Accessibilité
Responsive
Direction artistique
Qualité d'exécution
```

La qualité visuelle est désormais un critère de livraison à part entière.
