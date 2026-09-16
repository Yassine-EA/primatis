# PRIMATIS — Rapport de déduplication

## Résultat

```text
Assets normalisés analysés : 88
Assets autorisés à l’intégration : 84
Assets sociaux désactivés : 4
Groupes de doublons binaires restants : 0
```

## Mutualisations appliquées

| anciennes copies | destination canonique |
|---|---|
| portraits Home, Georges Lemaître et Login | `shared/portraits/georges-lemaitre-{480|768|1024}.webp` |
| fallbacks d’articles Home et Articles | `shared/editorial/editorial-{cosmos|library|observatory}-{640|960}.webp` |
| Univers en expansion | `shared/editorial/editorial-cosmos-{640|960}.webp` |
| décor Charleroi | `shared/editorial/editorial-observatory-960.webp` |
| bandeaux Home, Articles et Pourquoi PRIMATIS | `shared/editorial/lemaitre-observatory-banner-*` |

Les previews et masters de provenance ne sont pas des assets runtime et ne participent pas au calcul de duplication réseau.
