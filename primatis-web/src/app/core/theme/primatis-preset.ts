import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * Design Tokens PRIMATIS v2 (DESIGN-V2-A) — palette institutionnelle
 * "bleu nuit + cuivre scientifique" sur base claire, alignée sur le
 * Design System Maître v2 (`docs/design/design-system-v2.md` §3/§5) et
 * les maquettes validées (`docs/design/`). Remplace la rampe primary/
 * surface exploratoire posée en DEV-15.2 (avant l'arrivée des maquettes)
 * — décision consolidée dans `claude-workspace/tracking/decisions.md`
 * (DESIGN-V2-A). `darkModeSelector: false` (voir `app.config.ts`)
 * garantit qu'aucune variante sombre n'est jamais générée : les valeurs
 * ci-dessous sont écrites en littéral (pas de `light-dark()`) pour
 * rester déterministes quel que soit le thème du système d'exploitation.
 *
 * Seuls `primary` (bleu nuit) et `surface` (ivoire institutionnel) sont
 * redéfinis ici : `success`/`info`/`warn`/`danger` restent la palette
 * Aura par défaut (vert/ciel/orange/rouge), déjà cohérente et accessible
 * dans tout PrimeNG (Tag/Button/Message partagent ces mêmes couleurs) —
 * les redéfinir sans besoin démontré aurait introduit une charte de
 * statut concurrente (décision DEV-15.2 reconduite : le Design System v2
 * ne demande pas de piloter ces sévérités via le préset, §12
 * angular-primeng-spec.md). Les couleurs métier v2 (§7) sont exposées à
 * part comme tokens CSS plats (`--primatis-success/warning/danger/info`,
 * `design-tokens.scss`) pour un usage hors composants PrimeNG.
 *
 * L'accent cuivre n'a volontairement aucun équivalent ici : ce n'est
 * jamais une couleur de composant PrimeNG par défaut (§4 design system),
 * il vit uniquement comme tokens CSS `--primatis-copper-*`
 * (`design-tokens.scss`) pour un usage ponctuel et éditorial (CTA public,
 * état actif, identité Georges Lemaître).
 *
 * Rampe `primary` : ancrée sur les 4 valeurs bleu nuit v2 (900-600,
 * §3 design-system-v2.md) aux échelons 900/800/700/600 ; les échelons
 * 50-500 et 950 sont interpolés dans la même teinte pour couvrir les
 * besoins PrimeNG (hover clair, focus ring, texte sur fond sombre) que
 * les 4 valeurs v2 ne couvrent pas.
 *
 * Rampe `surface` : échelons 0/50/100/200 alignés sur l'ivoire v2 (§5),
 * échelons 300-950 conservés de DEV-15.2 (déjà une teinte neutre chaude
 * cohérente avec l'ivoire, aucune redéfinition démontrée nécessaire —
 * ces échelons ne sont de toute façon quasiment jamais visibles, le mode
 * sombre étant désactivé).
 */
export const PrimatisPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#eaf1f5',
      100: '#cfe0e9',
      200: '#a8c7d6',
      300: '#7ba9c0',
      400: '#4a87a3',
      500: '#2c6a87',
      600: '#1a4f72', // Navy 600 (v2)
      700: '#123f5e', // Navy 700 (v2)
      800: '#0b3650', // Navy 800 (v2)
      900: '#082b40', // Navy 900 (v2)
      950: '#051c2a',
    },
    surface: {
      0: '#ffffff',
      50: '#faf9f6', // Ivory 50 (v2)
      100: '#f6f1e8', // Ivory 100 (v2)
      200: '#ece5da', // Ivory 200 (v2)
      300: '#cfc3a8',
      400: '#b0a17e',
      500: '#8f8163',
      600: '#6e624a',
      700: '#524939',
      800: '#38322a',
      900: '#211d18',
      950: '#14110e',
    },
  },
});
