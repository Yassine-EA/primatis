"""DEV-17.3 — micro-nettoyage déterministe des textes du catalogue consolidé.

Périmètre volontairement minimal (HD-5) : le nettoyage ne touche ni à l'ordre
des mots, ni à l'orthographe, ni à l'identité d'un auteur. Il retire
uniquement :

- l'échappement source d'une apostrophe / d'un guillemet (`\\'` → `'`, `\\"` → `"`) ;
- les espaces répétés (ou blancs de contrôle) ramenés à une seule espace ;
- les espaces en début et fin de chaîne.

La fonction est idempotente : `clean_text(clean_text(x)) == clean_text(x)`.
"""

from __future__ import annotations

import re

_WHITESPACE_RUN = re.compile(r"\s+")


def clean_text(value: str) -> str:
    unescaped = value.replace("\\'", "'").replace('\\"', '"')
    return _WHITESPACE_RUN.sub(" ", unescaped).strip()
