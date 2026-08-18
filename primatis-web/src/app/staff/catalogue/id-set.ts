/**
 * Compare deux collections par ensemble d'`id`, jamais par ordre — un
 * réordonnancement seul (sans ajout/retrait réel) n'est jamais considéré
 * comme une modification (même principe que `sameRoleSet`, DEV-05.12).
 */
export function sameIdSet(a: readonly { id: number }[], b: readonly { id: number }[]): boolean {
  const setA = new Set(a.map((item) => item.id));
  const setB = new Set(b.map((item) => item.id));
  return setA.size === setB.size && [...setA].every((id) => setB.has(id));
}
