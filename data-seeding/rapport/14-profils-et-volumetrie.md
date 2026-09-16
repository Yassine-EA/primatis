# Profils et volumétrie — data-seeding

[Retour au rapport général](./general.md)

## 1. `small`

```text
Objectif    : jeu de données minimal pour développement quotidien
Titles      : 100     Copies : 160 (59×1, 28×2, 10×3, 3×5)
Users       : 25       Scénarios : non
Base        : primatis_dev
Pipeline    : pipeline/acquisition.py + pipeline/bundle.py (--profile small)
Sortie      : data/bundles/small/*.csv, bundle_report.json
Réel produit: 100 Titles, 90 Authors (après dédup), 96/100 ISBN présents,
              répartition langues FR75/EN10/NL8/DE3/ES2/IT1/LA1 (exacte)
```

## 2. `medium`

```text
Objectif    : jeu de données de développement/démonstration à volume x10
Titles      : 1 000   Copies : 1 600 (599×1, 268×2, 100×3, 33×5)
Users       : 100      Scénarios : non
Base        : primatis_dev
Pipeline    : identique à small, mêmes points d'entrée
Réel produit: 1 000 Titles, 768 Authors (après dédup), 947/1000 ISBN,
              22 auteurs enrichis (dates/bio), 25 avec nationalité,
              30 covers, 804/1000 page_count présents (SEUL profil où
              page_count_present > 0, voir 20-incoherences-et-risques.md),
              39/1000 summary présents. Audit de complétude exhaustif
              disponible (data/validated/medium/completeness_audit.csv).
```

## 3. `large`

```text
Objectif    : valider le passage à l'échelle (x5 vs medium) avant Full
Titles      : 5 000   Copies : 8 000 (3001×1, 1332×2, 500×3, 167×5)
Users       : 500 déclarés dans config/profiles.toml — AUCUNE trace de
              génération réelle (0 fichier users.csv dans data/bundles/large/)
Scénarios   : non
Base        : primatis_dev
Pipeline    : scripts/dev164_build_large.py -> pipeline/large_build.py
              (29 lots initiaux + 8 lots de complément après un premier
              run à 3307/5000, shortfall=1693, documenté sans dissimulation)
Réel produit: total_received=5413, total_accepted_before_trim=5402,
              trimmed_surplus_count=402, accepted_final=5000 (exact),
              isbn_coverage=0.7338, publisher_coverage=0.99,
              year_coverage=0.9894, genre_coverage=0.6978,
              page_count_coverage=0.0, summary_coverage=0.0,
              cover_coverage=0.0 (aucun pipeline de covers pour ce profil),
              quarantined=12 (TEXT_ENCODING_SUSPECT)
```

## 4. `full`

```text
Objectif    : volumétrie cible finale du TFE (15000/24000), démonstration
Titles      : 15 000  Copies : 24 000 (9000×1, 4000×2, 1500×3, 500×5)
Users       : 1 500   Scénarios : oui (Loan/Reservation/Fine/Notification)
Base        : primatis_preview
Pipeline    : scripts/dev165_build_full.py --with-enrichment --with-covers
              -> pipeline/full_build.py, puis
              scripts/dev1651_build_full_scenarios.py -> pipeline/full_scenarios.py
Réel produit (funnel exact, full_build_report.json) :
    source_records_received        198 801
    unique_candidates_inter_batch    15 187
    accepted_pre_trim                15 171
    trimmed                             171
    accepted_final                   15 000
    isbn_coverage        0.6727   publisher_coverage  0.9807
    year_coverage        0.9858   page_count_coverage 0.0
    summary_coverage     0.1697   genre_coverage      0.65
    cover_coverage       0.1179   biography_coverage  0.0002
    nationality_coverage 0.0
    quarantined              16  (TEXT_ENCODING_SUSPECT)
Scénarios (scenarios_build_report.json) :
    users=1500 loans=160 (80 ACTIVE/20 OVERDUE/60 RETURNED)
    reservations=60 (40 WAITING/20 READY)  fines=60 (20 UNPAID/20 PAID/20 CANCELLED)
    notifications=270  copy_states=120
    settings lus en direct sur primatis_preview : loan_duration_days=21,
    reservation_ready_hold_hours=48, loan_due_soon_days=3,
    fine_weekly_rate=0.80, fine_max_amount=25.00
Chargement réel confirmé (.claude/logs/DEV-16.8, requêtes SQL réelles) :
    primatis_preview : title=15000 copy=24000 author=11518(bio=2) genre=26
    title_author=17111 title_genre=13681 app_user=1500 loan=160
    reservation=60 fine=60 notification=270 — distribution copies EXACTE
```

## 5. Cible métier théorique (data-seeding.md, non un profil au sens strict)

`data-seeding.md` §« Target volumes » fixe une cible finale de 15 000 Titles / 24 000 Copies avec la même répartition 9000×1/4000×2/1500×3/500×5 — c'est exactement `full`, confirmé être la cible atteinte à la fois par le bundle (`copy_distribution`) et par l'audit SQL réel post-chargement (DEV-16.8 §P).

## 6. Répartition linguistique — comparaison des 4 profils

| Langue | small | medium | large | full |
|---|---|---|---|---|
| FR | 75 | 750 | 3 074 | 6 727 |
| EN | 10 | 100 | 738 | 3 525 |
| NL | 8 | 80 | 592 | 2 120 |
| DE | 3 | 30 | 261 | 1 102 |
| ES | 2 | 20 | 184 | 849 |
| IT | 1 | 15 | 126 | 497 |
| LA | 1 | 5 | 25 | 180 |

La proportion FR (~67 % à 75 %) diminue légèrement à grande échelle car FR a été volontairement plafonné après un phénomène mesuré de recouvrement inter-catégories documentaire (voir [06-acquisition.md](./06-acquisition.md) §3.4) — les 6 autres langues ont, elles, dépassé leur proportion canonique small/medium sans effort particulier (rendement 1:1 quasi systématique sur Large, confirmé par les scripts).

## Rapports liés

- [Configuration](./04-configuration.md)
- [Bundles](./15-bundles.md)
- [État réel actuel](./19-etat-reel-actuel.md)
