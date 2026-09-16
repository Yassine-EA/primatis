# PRIMATIS — Design System Maître v2

**Version :** 2.0  
**Statut :** Référence visuelle officielle  
**Application :** PRIMATIS — Bibliothèque publique Georges Lemaître  
**Univers couverts :** Public / Member / Staff / Admin

---

# 1. Positionnement visuel

PRIMATIS doit évoquer une bibliothèque publique :

- institutionnelle ;
- culturelle ;
- scientifique ;
- patrimoniale ;
- moderne ;
- chaleureuse ;
- sérieuse sans être austère.

L’identité visuelle s’inspire de Georges Lemaître, de la cosmologie, de
l’observation scientifique et du patrimoine local.

## À éviter absolument

- look SaaS générique ;
- interface startup ;
- thème spatial flashy ;
- néons ;
- gradients violets omniprésents ;
- surabondance de cartes blanches sans hiérarchie ;
- pages excessivement vides ;
- design “administration Bootstrap” ;
- iconographie décorative sans sens.

---

# 2. Principe majeur

Le design repose sur trois couches.

## Public

Éditorial, culturel, riche, immersif.

## Member

Personnel, rassurant, lisible, plus fonctionnel mais toujours marqué par
l’identité PRIMATIS.

## Staff/Admin

Dense, professionnel, métier-first, avec identité visuelle plus discrète mais
cohérente.

---

# 3. Palette principale

## Bleu nuit — couleur de marque principale

Usage :

- hero ;
- sidebar ;
- footer ;
- headers institutionnels ;
- surfaces fortes ;
- texte très important sur fond clair.

Références indicatives :

```text
Navy 900 : #082B40
Navy 800 : #0B3650
Navy 700 : #123F5E
Navy 600 : #1A4F72
```

Le bleu nuit ne doit pas être utilisé seulement pour les boutons.
Il doit réellement structurer l’interface.

---

# 4. Cuivre scientifique — accent

Références indicatives :

```text
Copper 700 : #8B4B26
Copper 600 : #A65D2E
Copper 500 : #B87333
Copper 300 : #D7A16E
```

Usage :

- CTA principaux ;
- états actifs ;
- soulignements ;
- séparateurs éditoriaux ;
- badges ;
- détails iconographiques.

Ne pas transformer le cuivre en orange saturé.

---

# 5. Ivoire / surfaces

```text
Ivory 50  : #FAF9F6
Ivory 100 : #F6F1E8
Ivory 200 : #ECE5DA
```

Usage :

- background principal ;
- cartes ;
- formulaires ;
- surfaces éditoriales.

---

# 6. Gris

```text
Gray 100 : #F1F3F5
Gray 300 : #D7DDE3
Gray 500 : #7C8795
Gray 700 : #46515F
Gray 900 : #1F2833
```

---

# 7. Couleurs métier

## Success

```text
#238B45
```

## Warning

```text
#C27A15
```

## Danger

```text
#D64545
```

## Info

```text
#2F6FED
```

Ces couleurs sont secondaires à la palette de marque.

---

# 8. Typographie

## Titres éditoriaux

Utiliser une serif élégante.

Cible :

```text
Playfair Display
```

Fallback :

```text
Georgia, "Times New Roman", serif
```

Usage :

- hero ;
- H1 public ;
- titres éditoriaux ;
- citations ;
- sections Georges Lemaître.

## UI / texte

Cible :

```text
Inter
```

Fallback :

```text
system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
```

Usage :

- navigation ;
- formulaires ;
- tableaux ;
- boutons ;
- metadata ;
- Member ;
- Staff/Admin.

---

# 9. Échelle typographique

## Desktop

```text
Hero H1         56–72px
Page H1 public  44–56px
Section H2      28–36px
Card title      18–22px
Body            16px
Small           14px
Meta            12–13px
```

## Staff/Admin

```text
Page H1         34–42px
Section H2      22–26px
Table text      13–14px
Form text       14–15px
```

## Mobile

```text
Hero H1         36–44px
Page H1         30–36px
H2              22–26px
Body            15–16px
```

---

# 10. Hiérarchie des titres

Une seule balise :

```html
<h1>
```

par page.

Le titre de shell ou de zone utilise :

```html
<h2>
```

ou un élément non-heading si purement décoratif.

La hiérarchie doit être sémantique, pas seulement visuelle.

---

# 11. Layout global

## Largeur maximale Public

```text
max-width : 1440px
```

## Largeur maximale Member

```text
max-width : 1360px
```

## Staff/Admin

Utiliser toute la largeur disponible après sidebar.

---

# 12. Grille

## Desktop

```text
12 colonnes
gutter : 24px
```

## Tablet

```text
8 colonnes
gutter : 20px
```

## Mobile

```text
4 colonnes
gutter : 16px
```

---

# 13. Spacing scale

Utiliser une échelle stable :

```text
4
8
12
16
24
32
40
48
64
80
96
```

Éviter les valeurs arbitraires du type :

```text
17px
23px
37px
```

sauf justification réelle.

---

# 14. Radius

```text
Small  : 6px
Medium : 10px
Large  : 16px
XL     : 22px
```

Staff/Admin plus sobre :

```text
6–10px
```

Public :

```text
10–16px
```

---

# 15. Ombres

Très discrètes.

```text
shadow-sm
shadow-md
```

Jamais d’effet floating agressif.

Les ombres servent à hiérarchiser, pas à décorer.

---

# 16. Header public

Composition :

- logo PRIMATIS ;
- nom complet ;
- navigation ;
- recherche ;
- Connexion / Mon compte.

Desktop :

- clair ;
- hauteur confortable ;
- séparation fine.

Mobile :

- logo ;
- menu hamburger ;
- navigation drawer.

---

# 17. Barre institutionnelle supérieure

Optionnelle mais recommandée sur desktop.

Contenu possible :

- phrase institutionnelle ;
- horaires ;
- accès ;
- contact.

Style :

- bleu nuit ;
- texte petit ;
- très compact.

Ne pas afficher une donnée institutionnelle non validée.

---

# 18. Logo

Le logo doit être présent dans :

- header ;
- footer ;
- sidebar Staff/Admin.

Toujours associé au nom :

> PRIMATIS  
> Bibliothèque publique Georges Lemaître

---

# 19. Hero public

Le hero est une vraie composition graphique.

Il doit inclure au minimum :

- titre ;
- sous-titre ;
- image ou composition ;
- fond travaillé ;
- CTA ;
- parfois recherche.

Ne jamais réduire le hero à :

> titre + paragraphe + image flottante.

---

# 20. Traitement Georges Lemaître

Éléments possibles :

- portrait noir et blanc ;
- tracés orbitaux ;
- cosmologie ;
- ciel nocturne ;
- observatoire ;
- formules scientifiques ;
- citations ;
- Charleroi.

Toujours subtil.

Ne jamais tomber dans :

- science-fiction ;
- galaxie kitsch ;
- NASA fanpage.

---

# 21. Images

Les images doivent avoir :

- traitement cohérent ;
- contraste maîtrisé ;
- ratio défini ;
- fallback élégant.

Formats recommandés :

```text
WebP / AVIF
```

Limiter les JPEG lourds.

---

# 22. Cartes publiques

Les cartes peuvent être utilisées pour :

- articles ;
- ouvrages ;
- accès rapides ;
- contenus éditoriaux.

Structure :

- image ;
- badge ;
- titre ;
- metadata ;
- CTA discret.

Les cartes ne doivent pas toutes avoir la même taille si une hiérarchie
éditoriale est nécessaire.

---

# 23. Carte ouvrage

Contenu :

- couverture ;
- titre ;
- auteur ;
- année ;
- langue ou genre si utile ;
- disponibilité si réellement disponible ;
- CTA détail.

Desktop :

- grille ou liste riche.

Mobile :

- liste compacte avec couverture gauche.

---

# 24. Disponibilité

Toujours explicite par :

- couleur ;
- texte ;
- éventuellement icône.

Jamais uniquement couleur.

Exemples :

- Disponible
- En prêt
- Réservé
- Indisponible

Ne jamais inventer cet état si le contrat API ne le fournit pas.

---

# 25. Boutons

## Primary

Bleu nuit ou cuivre selon contexte.

Public CTA majeur :

```text
Cuivre
```

Actions système :

```text
Bleu nuit
```

## Secondary

Fond clair + bordure.

## Danger

Rouge.

## Text

Sans background.

---

# 26. Taille boutons

```text
Small  : 32px
Medium : 40px
Large  : 48px
```

Mobile — touch target minimum :

```text
44 × 44px
```

---

# 27. Liens

Les liens doivent être clairement identifiables.

Accent :

- bleu nuit ;
- cuivre pour CTA éditorial.

Underline au hover/focus si pertinent.

---

# 28. Badges / tags

Usage :

- statut ;
- catégorie ;
- genre ;
- état métier.

Style :

- compact ;
- radius 6–8px ;
- fond légèrement teinté ;
- jamais trop de pills décoratives.

---

# 29. Formulaires

Structure :

- label au-dessus ;
- input ;
- hint ;
- error.

Jamais placeholder comme seul label.

Inputs :

```text
height ~ 42px
border subtil
focus clairement visible
```

---

# 30. États de formulaire

## Success

```text
green
```

## Error

```text
red + message textuel
```

## Disabled

```text
gray + contraste adapté
```

## Pending

- bouton disabled ;
- spinner ;
- texte stable.

---

# 31. Dialogs

Desktop :

- width contrôlée ;
- header clair ;
- body scrollable ;
- footer fixe ou visible.

Mobile :

- presque full-width ;
- padding réduit ;
- boutons accessibles.

---

# 32. Tables Staff/Admin

Principes :

- densité moyenne ;
- ligne claire ;
- header contrasté ;
- zebra optionnel très subtil ;
- actions alignées à droite ;
- statuts visibles.

Desktop :

table complète.

Tablet :

scroll interne acceptable.

Mobile :

cartes ou liste condensée si nécessaire.

---

# 33. Sidebar Staff/Admin

Couleur :

```text
navy 900
```

Contenu :

- logo ;
- navigation ;
- section active cuivre ;
- quote / image observatoire en bas ;
- retour vers site public.

Largeur desktop :

```text
220–250px
```

Tablet :

peut rester visible.

Mobile :

drawer.

---

# 34. Topbar Staff/Admin

Contenu :

- recherche globale si réellement utile/disponible ;
- notifications ;
- profil ;
- aide si fonction réelle.

Fond :

```text
ivoire / blanc
```

Pas de hero massif sur chaque page métier.

---

# 35. Member navigation

Desktop :

sidebar ou navigation locale claire.

Mobile :

bottom nav ou tabs selon contexte.

Pages :

- Profil
- Prêts
- Réservations
- Amendes
- Notifications

---

# 36. Member page hero

Plus léger que Public.

Peut utiliser :

- bandeau astronomique ;
- observatoire ;
- quote ;
- titre de page.

Hauteur modérée.

---

# 37. Dashboard cards

Utiliser uniquement lorsque la donnée synthétique apporte de la valeur :

- prêts actifs ;
- réservations ;
- amendes ;
- statistiques réelles.

Pas de cardification systématique.

---

# 38. États de statut

## Active / Available

Vert.

## Pending

Cuivre / orange.

## Overdue / Critical

Rouge.

## Archived / Disabled

Gris.

---

# 39. Notifications

Structure :

- catégorie ;
- titre ;
- message ;
- timestamp ;
- état lu/non lu ;
- CTA éventuel.

Non lu :

- fond légèrement teinté ;
- bord ou indicateur.

---

# 40. Navigation mobile

Public :

- hamburger.

Member :

- bottom nav recommandée si cohérente avec l’implémentation.

Staff/Admin :

- drawer ;
- raccourcis uniquement si utiles.

---

# 41. Footer public

Doit être travaillé.

Contenu possible selon données réelles :

- logo ;
- liens ;
- services ;
- à propos ;
- citation ;
- mentions légales ;
- accessibilité.

Fond :

```text
navy
```

Ne pas inventer horaires, adresse, téléphone ou réseaux sociaux.

---

# 42. Footer Staff/Admin

Plus compact.

Contenu :

- copyright ;
- version si connue ;
- aide ;
- confidentialité ;
- accessibilité.

---

# 43. Citations

Usage ponctuel.

Style :

- serif italic ;
- bleu nuit ou blanc selon fond ;
- accent cuivre.

Ne jamais en mettre partout.

---

# 44. Icônes

Style cohérent :

- line icons ;
- taille 18–24px ;
- jamais emoji système.

PrimeIcons acceptable pour implémentation.

---

# 45. Recherche

Public :

grande barre visible.

Catalogue :

fonction dominante.

Staff/Admin :

plus compacte et uniquement si utile.

---

# 46. Catalogue public

Desktop :

- hero ;
- recherche ;
- filtres ;
- cartes ou liste ;
- pagination.

Mobile :

- filtres collapsibles ;
- résultats en liste ;
- couverture visible.

---

# 47. Détail livre

Structure desktop :

- hero léger ;
- couverture ;
- titre ;
- auteurs ;
- metadata ;
- disponibilité seulement si réelle ;
- CTA ;
- résumé ;
- suggestions seulement si données réelles.

Mobile :

- metadata condensée ;
- sections collapsibles si utile ;
- CTA visible.

---

# 48. Articles

Desktop :

- hero ;
- article vedette ;
- grille ;
- sidebar uniquement si les données le justifient.

Mobile :

- article vedette ;
- liste verticale ;
- filtres simples si réels.

---

# 49. Georges Lemaître

C’est la page vitrine principale.

Elle doit être :

- plus travaillée que toutes les autres ;
- éditoriale ;
- immersive ;
- historiquement sérieuse ;
- visuellement forte.

Éléments :

- hero portrait ;
- citation ;
- timeline ;
- Univers en expansion ;
- atome primitif ;
- Charleroi ;
- héritage ;
- ressources ;
- sources.

---

# 50. Login

Simple mais identitaire.

Desktop :

- background immersif ;
- formulaire central ;
- image / observatoire / portrait.

Mobile :

- forme compacte ;
- identité préservée.

---

# 51. Responsive breakpoints

Référence :

```text
mobile      < 768
tablet      >= 768
desktop     >= 1024
wide        >= 1440
```

Ne pas multiplier les breakpoints.

---

# 52. Mobile

Règles :

- pas de simple réduction desktop ;
- composants repensés ;
- contenu priorisé ;
- CTA accessibles ;
- pas de scroll horizontal global ;
- tables transformées si nécessaire.

---

# 53. Accessibilité

Objectif pragmatique :

```text
WCAG 2.2 AA
```

Exigences :

- contraste ;
- focus visible ;
- labels ;
- alt ;
- headings ;
- keyboard ;
- dialogs ;
- aria-live ;
- statuts textuels.

---

# 54. Animation

Très légère.

Autorisé :

- fade ;
- slide court ;
- hover subtil.

Interdit :

- parallax excessif ;
- animations décoratives permanentes ;
- étoiles animées ;
- effets flashy.

---

# 55. Loading

Skeleton recommandé pour :

- catalogue ;
- articles ;
- tables.

Spinner :

uniquement opérations courtes.

---

# 56. Empty states

Toujours contextualisés.

Exemple :

> Aucun prêt en cours.

Pas :

> No data.

---

# 57. Error states

Structure :

- titre ;
- message ;
- action retry si possible.

---

# 58. Tables responsive

768px :

scroll interne acceptable.

1024px :

actions importantes doivent rester visibles autant que possible.

1440px :

aucun vide excessif.

---

# 59. Densité par univers

## Public

Richesse visuelle élevée.

## Member

Densité moyenne.

## Staff

Densité moyenne à forte.

## Admin

Densité forte mais lisible.

---

# 60. Données réelles uniquement

Les maquettes peuvent illustrer un rendu idéal.

L’implémentation doit utiliser uniquement :

- routes réelles ;
- DTO réels ;
- champs réels ;
- fonctionnalités réelles.

Si une information n’existe pas :

- ne pas l’inventer ;
- adapter la composition.

---

# 61. Règle essentielle de fidélité

Claude Code ne doit jamais interpréter librement une maquette validée.

Pour chaque écran :

1. maquette de référence ;
2. inventaire des composants ;
3. mapping avec données réelles ;
4. implémentation ;
5. screenshot ;
6. comparaison ;
7. corrections ;
8. validation.

---

# 62. Visual Gate obligatoire

Aucune page n’est PASS sans :

- desktop screenshot ;
- mobile screenshot ;
- comparaison avec la maquette ;
- validation visuelle explicite.

---

# 63. Ce qui constitue un échec visuel

Même si les tests passent, une page est FAIL si :

- trop vide ;
- aucune hiérarchie ;
- branding absent ;
- hero pauvre ;
- composants génériques ;
- densité très inférieure à la maquette ;
- palette presque invisible ;
- identité Lemaître inexistante ;
- mise en page ressemblant à un prototype.

---

# 64. Ordre d’implémentation recommandé

## Phase 1 — Foundations

- tokens ;
- typography ;
- global layout ;
- buttons ;
- cards ;
- tables ;
- forms.

## Phase 2 — Public

1. Home
2. Catalogue
3. Détail livre
4. Articles
5. Georges Lemaître
6. Login

## Phase 3 — Member

1. Profil
2. Prêts
3. Réservations
4. Amendes
5. Notifications

## Phase 4 — Staff/Admin

1. Shell
2. Dashboard si données réelles
3. Catalogue
4. Copies
5. Loans
6. Reservations
7. Fines
8. Users
9. Articles
10. Tags
11. Settings
12. Notifications si périmètre réel

## Phase 5 — Visual Gate global

---

# 65. Principe final

Le Design System ne sert pas seulement à rendre PRIMATIS cohérent.

Il sert à empêcher que l’implémentation dérive visuellement des maquettes
validées.

La qualité visuelle est un critère de livraison à part entière.
