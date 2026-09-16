# PRIMATIS — Contrat d’intégration — Espace membre

## Destination

```text
docs/design/assets/member/
```

## Hero partagé

Utiliser les trois fichiers via `<picture>` ou `image-set()`. Point focal : **82 % 52 %**. Le texte est posé dans la moitié gauche.

| viewport | fichier | hauteur cible indicative |
|---|---|---:|
| ≥ 1440 px | `shared/member-hero-1920.webp` | 270–300 px |
| 769–1439 px | `shared/member-hero-1280.webp` | 230–270 px |
| ≤ 768 px | `shared/member-hero-768-mobile.webp` | 150–190 px |

Le hero est décoratif : arrière-plan CSS ou `alt=""`. Les titres et sous-titres restent en HTML.

## PrimeIcons — navigation et synthèse

| fonction | classe recommandée |
|---|---|
| profil | `pi pi-user` |
| prêts | `pi pi-book` |
| réservations | `pi pi-bookmark` |
| amendes | `pi pi-receipt` |
| notifications | `pi pi-bell` |
| listes | `pi pi-heart` |
| préférences | `pi pi-cog` |
| retours à venir | `pi pi-calendar` |
| retard | `pi pi-exclamation-circle` |
| historique récent | `pi pi-history` |
| information | `pi pi-info-circle` |
| succès/payé | `pi pi-check-circle` |

Chaque icône de statut accompagne un texte visible. Ne jamais encoder un statut uniquement par la couleur.

## Intégrité métier

- Prêts : ne pas fabriquer de couverture par titre et ne pas afficher une statistique annuelle non fournie.
- Réservations : ne pas inventer une position FIFO ni une notification par e-mail.
- Amendes : ne pas inventer une couverture ou un contact direct non validé.
- Notifications : utiliser le pictogramme de type ; aucune image d’article ou de livre fictive.
- Le fallback compact est neutre et optionnel ; une liste sans vignette reste préférable si elle respecte mieux les données.
