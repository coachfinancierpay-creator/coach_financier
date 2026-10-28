import { useEffect, useState } from 'react'
import { CalendarDays, PhoneCall, X } from 'lucide-react'

import { ADVISOR_APPOINTMENT_EVENT, ADVISOR_CALLBACK_EVENT } from './advisorCallback'

/**
 * POP-IN « être rappelé par un conseiller » : ouverte par le jeton `[RAPPEL|Être rappelé par un conseiller]`
 * que le Coach place à côté du lien de rendez-vous. Elle rassure le client : un conseiller le recontactera
 * dans les plus brefs délais.
 * <p>
 * Montée par le routeur ({@code main.tsx}) : le jeton est donc actif sur toutes les pages qui affichent une
 * réponse du Coach (chat, historique de conversation). Aucune donnée personnelle n'est demandée ni transmise,
 * et aucun créneau n'est annoncé — le POC n'enregistre pas de demande de rappel côté serveur.
 */
export default function AdvisorCallbackPopup() {
  const [open, setOpen] = useState(false)
  const [kind, setKind] = useState<'callback' | 'appointment'>('callback')

  useEffect(() => {
    const onCallbackRequest = () => {
      setKind('callback')
      setOpen(true)
    }
    const onAppointmentRequest = () => {
      setKind('appointment')
      setOpen(true)
    }
    window.addEventListener(ADVISOR_CALLBACK_EVENT, onCallbackRequest)
    window.addEventListener(ADVISOR_APPOINTMENT_EVENT, onAppointmentRequest)
    return () => {
      window.removeEventListener(ADVISOR_CALLBACK_EVENT, onCallbackRequest)
      window.removeEventListener(ADVISOR_APPOINTMENT_EVENT, onAppointmentRequest)
    }
  }, [])

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  if (!open) return null

  return (
    <div
      className="qlt-popup-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="callback-popup-title"
      onClick={() => setOpen(false)}
    >
      <div className="qlt-popup" onClick={(event) => event.stopPropagation()}>
        <div className="callback-popup-head">
          <span className="callback-popup-icon">
            {kind === 'appointment' ? <CalendarDays size={18} /> : <PhoneCall size={18} />}
          </span>
          <h2 id="callback-popup-title">
            {kind === 'appointment' ? 'Simulated appointment booking' : 'Your callback request has been received'}
          </h2>
          <button
            type="button"
            className="callback-popup-close"
            aria-label="Close"
            onClick={() => setOpen(false)}
          >
            <X size={16} />
          </button>
        </div>

        {kind === 'appointment' ? (
          <>
            <p className="callback-popup-text">
              In a full version, you would be redirected to the page for booking an appointment with an
              advisor.
            </p>
            <p className="callback-popup-text">
              As this is a POC, this message simulates the appointment booking. No time slot
              is actually reserved.
            </p>
          </>
        ) : (
          <>
            <p className="callback-popup-text">
              An advisor will contact you <strong>as soon as possible</strong> to review
              your project.
            </p>
            <p className="callback-popup-text">
              Keep your phone close at hand. No appointment is booked yet: the
              advisor will offer you a time slot during the call.
            </p>
          </>
        )}

        <div className="qlt-popup-actions">
          <button type="button" className="callback-popup-ok" onClick={() => setOpen(false)}>
            Got it
          </button>
        </div>
      </div>
    </div>
  )
}
