# Données locales

Les répertoires suivants sont créés/peuplés uniquement lorsque les étapes correspondantes du pipeline existent :

```text
data/raw/
data/normalized/
data/validated/
```

Ils sont ignorés par le `.gitignore` racine afin d'éviter de versionner automatiquement des datasets volumineux ou soumis à des conditions de réutilisation particulières.

Aucune donnée externe n'est acquise pendant DEV-13.3.
