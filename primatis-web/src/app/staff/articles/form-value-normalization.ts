/**
 * `''` (champ vidé) devient `null` (efface), sinon la valeur trimée — même
 * fonction exacte que `staff/catalogue/form-value-normalization.ts`,
 * dupliquée volontairement ici (feature-first, DEV-11.12) plutôt qu'un
 * import cross-feature pour un utilitaire aussi trivial.
 */
export function normalizeOptional(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}
