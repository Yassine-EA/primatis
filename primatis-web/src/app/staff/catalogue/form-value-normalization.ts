/** `''` (champ vidé) devient `null` (efface), sinon la valeur trimée. */
export function normalizeOptional(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

/** `''` devient `null`, sinon un nombre — jamais `NaN` propagé. */
export function parseOptionalInt(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}
