# PRIMATIS — Références Design

Ce dossier contient les références visuelles contractuelles du frontend PRIMATIS.

Il rassemble :

- les maquettes validées ;
- le Design System Maître v2 ;
- la spécification d’implémentation Angular / PrimeNG ;
- les futures notes de mapping entre maquettes et données réelles.

---

# 1. Règle fondamentale

Les maquettes présentes dans ce dossier sont la référence visuelle de l’implémentation.

Le Design System et les spécifications techniques complètent les maquettes mais ne les remplacent pas.

Pour chaque page :

1. lire le Design System ;
2. ouvrir la maquette correspondante ;
3. auditer l’implémentation Angular existante ;
4. mapper la maquette aux données et API réellement disponibles ;
5. implémenter ;
6. produire un rendu desktop ;
7. produire un rendu mobile ;
8. comparer visuellement avec la maquette ;
9. corriger les écarts ;
10. ne déclarer PASS qu’après validation visuelle.

---

# 2. Arborescence cible

```text
docs/design/
├── README.md
├── design-system-v2.md
├── angular-primeng-spec.md
├── public/
│   ├── home.png
│   ├── catalogue.png
│   ├── title-detail.png
│   ├── articles.png
│   ├── georges-lemaitre.png
│   └── login.png
├── member/
│   ├── profile.png
│   ├── loans.png
│   ├── reservations.png
│   ├── fines.png
│   └── notifications.png
└── staff-admin/
    ├── dashboard.png
    ├── catalogue.png
    ├── users.png
    ├── user-detail.png
    ├── user-create.png
    ├── copies-articles-tags.png
    └── management-overview.png
```

Les noms peuvent évoluer légèrement si une maquette contient plusieurs écrans, mais chaque référence doit rester clairement identifiable.

---

# 3. Documents maîtres

## `design-system-v2.md`

Définit le langage visuel PRIMATIS :

- palette ;
- typographie ;
- spacing ;
- radius ;
- ombres ;
- boutons ;
- cartes ;
- formulaires ;
- tables ;
- navigation ;
- iconographie ;
- responsive ;
- accessibilité ;
- densité par univers.

## `angular-primeng-spec.md`

Définit la traduction technique du Design System dans Angular / PrimeNG :

- architecture Public / Member / Staff / Admin ;
- mapping PrimeNG ;
- règles SCSS ;
- composants partagés ;
- données réelles ;
- Visual Gate ;
- tests ;
- Playwright ;
- workflow d’implémentation.

---

# 4. Langue

PRIMATIS V1 est uniquement en français.

Aucune internationalisation de l’interface ne doit être ajoutée.

La langue d’un ouvrage reste une donnée bibliographique et ne constitue pas une langue d’interface.

---

# 5. Interdiction d’inventer des données

Les maquettes illustrent une direction visuelle.

L’implémentation doit utiliser uniquement :

- routes réelles ;
- DTO réels ;
- champs réels ;
- règles métier réelles ;
- fonctionnalités réellement disponibles.

Si une donnée visible dans une maquette n’existe pas dans PRIMATIS, elle ne doit pas être inventée.

La composition doit alors être adaptée sans diminuer la qualité, la densité ou la hiérarchie visuelle de l’écran.

Exemples :

- ne pas inventer une disponibilité publique si le contrat API ne la fournit pas ;
- ne pas inventer un bouton de paiement d’amende ;
- ne pas inventer une inscription publique ;
- ne pas inventer des statistiques dashboard ;
- ne pas inventer horaires, adresse, téléphone ou réseaux sociaux non validés.

---

# 6. Les maquettes comme contrat visuel

Chaque maquette validée constitue une cible de référence pour :

- la composition ;
- la hiérarchie ;
- la densité ;
- la palette ;
- la typographie ;
- les proportions ;
- les surfaces ;
- les images ;
- le responsive ;
- l’identité PRIMATIS.

Claude Code ne doit pas interpréter librement cette cible.

Il doit l’utiliser comme une référence à reproduire aussi fidèlement que possible dans les limites des données réelles.

---

# 7. PrimeNG n’est pas la direction artistique

PrimeNG est une bibliothèque de composants.

Il ne doit pas imposer :

- ses styles par défaut ;
- une apparence générique ;
- un rendu SaaS ;
- une densité standardisée au détriment des maquettes.

Le Design System PRIMATIS et les maquettes ont priorité sur l’apparence native des composants PrimeNG.

---

# 8. Validation visuelle obligatoire

Aucun écran ne peut être considéré comme terminé sur la seule base :

- des tests unitaires ;
- du build ;
- de Playwright ;
- de l’accessibilité ;
- du responsive technique.

La fidélité visuelle aux maquettes est un critère de validation obligatoire.

Pour chaque écran, produire au minimum :

```text
reference
actual-desktop
actual-mobile
```

Puis comparer explicitement :

- composition ;
- hiérarchie ;
- densité ;
- couleurs ;
- typographie ;
- images ;
- CTA ;
- navigation ;
- responsive.

---

# 9. Critère FAIL visuel

Un écran est FAIL même si tous les tests passent si :

- il ressemble à un scaffold ;
- il est nettement plus pauvre que la maquette ;
- la page est excessivement vide ;
- la hiérarchie visuelle est faible ;
- le branding PRIMATIS disparaît ;
- le bleu nuit / cuivre / ivoire sont presque absents ;
- le hero est réduit à un simple titre + paragraphe ;
- les composants PrimeNG dominent visuellement le design ;
- le mobile ressemble uniquement à un desktop compressé ;
- la page Georges Lemaître ressemble à un simple article HTML.

---

# 10. Validation humaine

La validation finale visuelle appartient au développeur.

Claude Code peut conclure :

```text
READY FOR VISUAL REVIEW
```

mais ne peut pas déclarer lui-même :

```text
VISUALLY APPROVED
```

sans validation explicite du développeur.

---

# 11. Workflow obligatoire par page

```text
AUDIT
↓
OUVERTURE DE LA MAQUETTE
↓
MAPPING DONNÉES / API
↓
PLAN D’IMPLÉMENTATION
↓
IMPLEMENTATION
↓
TESTS UNITAIRES
↓
BUILD
↓
LANCEMENT APPLICATION
↓
SCREENSHOT DESKTOP
↓
SCREENSHOT MOBILE
↓
COMPARAISON VISUELLE
↓
CORRECTIONS
↓
NOUVELLES CAPTURES
↓
PLAYWRIGHT PERTINENT
↓
READY FOR VISUAL REVIEW
↓
VALIDATION HUMAINE
↓
PASS
```

---

# 12. Mapping préalable obligatoire

Avant toute modification d’une page :

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

INTERDIT D’INVENTER
- ...
```

Ce mapping doit être conservé dans le rapport du sous-DEV.

---

# 13. Univers Public

Références attendues :

```text
public/home.png
public/catalogue.png
public/title-detail.png
public/articles.png
public/georges-lemaitre.png
public/login.png
```

Le Public possède la plus forte richesse graphique.

La page Georges Lemaître doit être la page visuellement la plus travaillée de PRIMATIS.

---

# 14. Univers Member

Références attendues :

```text
member/profile.png
member/loans.png
member/reservations.png
member/fines.png
member/notifications.png
```

Member doit conserver l’identité PRIMATIS tout en étant plus personnel et fonctionnel que le portail public.

---

# 15. Univers Staff/Admin

Références attendues :

```text
staff-admin/dashboard.png
staff-admin/catalogue.png
staff-admin/users.png
staff-admin/user-detail.png
staff-admin/user-create.png
staff-admin/copies-articles-tags.png
staff-admin/management-overview.png
```

Staff/Admin doit être plus dense et métier-first, mais jamais générique.

---

# 16. Responsive

Largeurs de référence :

```text
375px
768px
1024px
1440px
```

Au minimum :

- Public / Member : 375 + 1440 ;
- Staff / Admin : 375 + 768 + 1024 + 1440.

Aucun scroll horizontal global n’est acceptable.

---

# 17. Accessibilité

Objectif pragmatique :

```text
WCAG 2.2 AA
```

La fidélité visuelle ne permet jamais de sacrifier :

- labels ;
- focus visible ;
- contraste ;
- navigation clavier ;
- headings ;
- alt text ;
- aria ;
- statuts textuels.

---

# 18. Données et règles métier

Le backend reste l’autorité métier.

Le frontend ne doit pas recalculer ou inventer :

- disponibilité ;
- overdue ;
- due soon ;
- FIFO ;
- expiration READY ;
- amendes ;
- permissions ;
- statuts de compte.

Si une maquette semble nécessiter une évolution métier :

```text
CHANGE REQUIRES DECISION
```

et l’implémentation doit s’arrêter sur ce point.

---

# 19. Règles Git

Les règles PRIMATIS existantes restent applicables.

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
- modifier l’historique.

Travail uniquement dans le checkout courant.

---

# 20. Rapports

Chaque étape d’implémentation visuelle doit produire un rapport sous :

```text
.claude/logs/
```

avec au minimum :

- maquette utilisée ;
- mapping API ;
- fichiers modifiés ;
- différences justifiées ;
- tests ;
- build ;
- screenshots contrôlés ;
- responsive ;
- accessibilité ;
- Playwright ;
- dettes ;
- statut final.

---

# 21. Ordre d’implémentation recommandé

## Phase A — Fondations

1. Tokens
2. Theme PrimeNG
3. Typography
4. Public shell/header/footer
5. Member shell
6. Staff/Admin shell

## Phase B — Public

1. Home
2. Catalogue
3. Détail livre
4. Articles
5. Georges Lemaître
6. Login

## Phase C — Member

1. Profil
2. Prêts
3. Réservations
4. Amendes
5. Notifications

## Phase D — Staff/Admin

1. Shell
2. Prêts
3. Réservations
4. Amendes
5. Catalogue
6. Exemplaires
7. Articles
8. Tags
9. Utilisateurs
10. Détail utilisateur
11. Création utilisateur
12. Paramètres

---

# 22. Principe final

PRIMATIS doit démontrer simultanément :

```text
Architecture
Fonctionnalité
UX
Accessibilité
Responsive
Direction artistique
Qualité d’exécution
```

Le design n’est plus un embellissement final.

Il constitue désormais une exigence de livraison à part entière.
