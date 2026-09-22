/**
 * Dates « métier » de l'IHM, au format ISO `AAAA-MM-JJ` attendu par le backend.
 * <p>
 * Les dates sont construites à partir de l'horloge LOCALE (jamais via `toISOString()`, qui passe par
 * UTC) : le backend raisonne en `LocalDate`, donc une IHM en UTC+2 ne doit pas afficher — ni demander —
 * la veille du jour courant en début de matinée.
 */

/** Date locale → `AAAA-MM-JJ`. */
export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** Aujourd'hui (date locale) → `AAAA-MM-JJ`. */
export function isoToday(): string {
  return isoDate(new Date())
}

/** Il y a {@code days} jours (date locale) → `AAAA-MM-JJ`. */
export function isoDaysAgo(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return isoDate(date)
}
