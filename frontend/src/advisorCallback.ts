/**
 * Demande de rappel par un conseiller.
 * <p>
 * Le Coach écrit un jeton SANS URL — `[RAPPEL|Être rappelé par un conseiller]` — parce qu'aucune adresse de
 * rappel n'existe dans les données : le lien ne doit donc PAS naviguer, il ouvre une pop-in qui informe le
 * client qu'un conseiller le recontactera. Le clic émet un événement, écouté par la pop-in globale
 * ({@code AdvisorCallbackPopup}) montée par le routeur : ainsi le jeton fonctionne sur toutes les pages
 * (chat, historique de conversation) sans dépendre de l'état d'une page.
 */
export const ADVISOR_CALLBACK_EVENT = 'advisor-callback-request'
export const ADVISOR_APPOINTMENT_EVENT = 'advisor-appointment-request'
export const ADVISOR_INTENT_UPDATED_EVENT = 'advisor-intent-updated'
const ADVISOR_INTENTS_STORAGE_KEY = 'financial-coach-advisor-intents'

export interface AdvisorIntent {
  prisRDV: boolean
  etreRappele: boolean
}

const DEFAULT_ADVISOR_INTENT: AdvisorIntent = { prisRDV: false, etreRappele: false }

/** Retourne les actions commerciales mémorisées pour une session, avec false comme valeur par défaut. */
export function getAdvisorIntent(sessionId: string): AdvisorIntent {
  if (!sessionId || typeof localStorage === 'undefined') return { ...DEFAULT_ADVISOR_INTENT }
  try {
    const stored = JSON.parse(localStorage.getItem(ADVISOR_INTENTS_STORAGE_KEY) ?? '{}') as Record<string, Partial<AdvisorIntent>>
    const intent = stored[sessionId]
    return {
      prisRDV: intent?.prisRDV === true,
      etreRappele: intent?.etreRappele === true,
    }
  } catch {
    return { ...DEFAULT_ADVISOR_INTENT }
  }
}

function markAdvisorIntent(sessionId: string | undefined, key: keyof AdvisorIntent): void {
  if (!sessionId || typeof localStorage === 'undefined') return
  const intent = getAdvisorIntent(sessionId)
  intent[key] = true
  try {
    const stored = JSON.parse(localStorage.getItem(ADVISOR_INTENTS_STORAGE_KEY) ?? '{}') as Record<string, AdvisorIntent>
    stored[sessionId] = intent
    localStorage.setItem(ADVISOR_INTENTS_STORAGE_KEY, JSON.stringify(stored))
    window.dispatchEvent(new CustomEvent(ADVISOR_INTENT_UPDATED_EVENT, { detail: { sessionId, ...intent } }))
  } catch {
    // Le marquage reste facultatif dans le POC si le stockage navigateur est indisponible.
  }
}

/** Émet la demande de rappel (appelée par le rendu du jeton `[RAPPEL|…]`). */
export function requestAdvisorCallback(sessionId?: string): void {
  markAdvisorIntent(sessionId, 'etreRappele')
  window.dispatchEvent(new CustomEvent(ADVISOR_CALLBACK_EVENT))
}

/** Émet une demande de prise de rendez-vous simulée par le POC. */
export function requestAdvisorAppointment(sessionId?: string): void {
  markAdvisorIntent(sessionId, 'prisRDV')
  window.dispatchEvent(new CustomEvent(ADVISOR_APPOINTMENT_EVENT))
}
