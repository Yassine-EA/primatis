# Contrat d'intégration — Home

Ce document fixe la manière d'utiliser les assets sans transformer la maquette en simple inspiration.

## 1. Hero

Ordre des couches :

1. fond responsive ;
2. overlay navy en CSS ;
3. portrait transparent ;
4. ornements SVG du socle global ;
5. texte, recherche et CTA en HTML.

### Sources responsives

| viewport | fond | portrait |
|---|---|---|
| ≥ 1280 px | `hero-background-1920.webp` | `/assets/shared/portraits/georges-lemaitre-1024.webp` |
| 768–1279 px | `hero-background-1280.webp` | `/assets/shared/portraits/georges-lemaitre-768.webp` |
| < 768 px | `hero-background-768-mobile.webp` | `/assets/shared/portraits/georges-lemaitre-480.webp` |

```html
<picture class="home-hero__background" aria-hidden="true">
  <source media="(max-width: 767px)" srcset="/assets/public/home/hero/hero-background-768-mobile.webp">
  <source media="(max-width: 1279px)" srcset="/assets/public/home/hero/hero-background-1280.webp">
  <img src="/assets/public/home/hero/hero-background-1920.webp" alt="">
</picture>
```

```scss
.home-hero {
  position: relative;
  overflow: hidden;
  min-height: clamp(38rem, 48vw, 48rem);
  background: #082b40;
}

.home-hero::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgb(3 27 43 / 78%) 0%, rgb(3 27 43 / 28%) 52%, rgb(3 27 43 / 10%) 100%);
  pointer-events: none;
}
```

Le portrait conserve son alpha, n'est jamais utilisé comme `background-image` et reçoit `alt="Illustration de Georges Lemaître"`. Sur mobile, le contenu textuel passe avant le portrait dans l'ordre du DOM et la zone de recherche ne doit pas recouvrir le visage.

## 2. Articles

Les six fichiers constituent trois paires 640/960. Ils sont décoratifs :

```html
<picture aria-hidden="true">
  <source media="(max-width: 767px)" srcset="/assets/shared/editorial/editorial-cosmos-640.webp">
  <img src="/assets/shared/editorial/editorial-cosmos-960.webp" alt="">
</picture>
```

Utiliser une variante déterministe et stable, sans prétendre qu'elle décrit le contenu : par exemple `article.id % 3`. Ne jamais dériver le choix du titre, d'un faux tag ou d'une catégorie absente. Si un vrai champ image est ajouté plus tard, il devient prioritaire et le fallback reste la solution d'absence.

## 3. Bandeau Lemaître

- desktop : `/assets/shared/editorial/lemaitre-observatory-banner-1920.webp` ;
- mobile/tablette étroite : `/assets/shared/editorial/lemaitre-observatory-banner-960-mobile.webp` ;
- texte et CTA au centre/droite, en HTML ;
- aucun attribut `alt` informatif si le même portrait est déjà annoncé dans la section.

## 4. Footer institutionnel

- desktop : `footer-observatory-1920.webp` ;
- mobile : `footer-observatory-960-mobile.webp` ;
- placer l'observatoire à gauche et les contenus validés à droite ;
- ajouter si nécessaire un gradient navy en CSS, jamais du texte dans l'image.

Les métriques de la maquette (`25 000 ouvrages`, espaces, événements) ne sont pas autorisées tant qu'elles ne sont pas validées par une source métier/institutionnelle.

## 5. Géométrie cible

- hero pleine largeur, contenu interne aligné sur le nouveau container public ;
- fond hero ratio desktop ≈ 2,5:1 ;
- cartes Article ratio 16:9 ;
- bandeau Lemaître ratio desktop 4:1 ;
- footer visuel ratio desktop 16:3 ;
- `object-fit: cover` et points focaux du manifeste obligatoires ;
- aucun étirement non proportionnel des assets runtime.
