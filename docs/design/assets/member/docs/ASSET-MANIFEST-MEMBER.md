# PRIMATIS — Asset Manifest — Espace membre

**Phase :** ASSET-RESET-03  
**Date :** 2026-09-08  
**État :** APPROVED — validations technique et visuelle acquises

## Nouveaux fichiers

| id | fichier | rôle | dimensions | page | source | status | integrationAllowed |
|---|---|---|---:|---|---|---|---|
| MEMBER-HERO-001 | `shared/member-hero-1920.webp` | décoratif | 1920×360 | quatre pages | HOME-FOOTER-001 retraité | `APPROVED` | yes |
| MEMBER-HERO-002 | `shared/member-hero-1280.webp` | décoratif | 1280×300 | quatre pages | MEMBER-HERO-001 | `APPROVED` | yes |
| MEMBER-HERO-003 | `shared/member-hero-768-mobile.webp` | décoratif | 768×300 | quatre pages | MEMBER-HERO-001 | `APPROVED` | yes |

## Réemplois obligatoires — ASSET-RESET-01

| besoin | fichier existant | usage |
|---|---|---|
| fallback compact | `assets/fallbacks/cover-compact.svg` | liste de prêts, réservations ou amendes seulement si la composition exige une vignette |
| aucun prêt | `assets/fallbacks/empty-states/loans.svg` | état vide Prêts |
| aucune réservation | `assets/fallbacks/empty-states/reservations.svg` | état vide Réservations |
| aucune amende | `assets/fallbacks/empty-states/fines.svg` | état vide Amendes |
| aucune notification | `assets/fallbacks/empty-states/notifications.svg` | état vide Notifications |

Les réemplois ne doivent pas être recopiés dans `assets/member/`.

## Éléments non produits

- couvertures individualisées : données image absentes des DTO concernés ;
- graphiques et compteurs : données et HTML/CSS ;
- avatars : initiales en CSS ;
- icônes fonctionnelles : PrimeIcons ;
- texte ou citation : HTML.
