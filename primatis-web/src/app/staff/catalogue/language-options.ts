import { Language } from '../../catalogue/models/language';

export interface LanguageOption {
  label: string;
  value: Language;
}

/**
 * Libellés FR pour l'enum `Language` (staff uniquement — le catalogue
 * public, DEV-06.8, a sa propre copie locale incluant l'option "Toutes les
 * langues"). Valeur réseau toujours l'enum exact, jamais le libellé.
 */
export const LANGUAGE_OPTIONS: LanguageOption[] = [
  { label: 'Français', value: 'FR' },
  { label: 'Anglais', value: 'EN' },
  { label: 'Néerlandais', value: 'NL' },
  { label: 'Allemand', value: 'DE' },
  { label: 'Espagnol', value: 'ES' },
  { label: 'Italien', value: 'IT' },
  { label: 'Latin', value: 'LA' },
];
