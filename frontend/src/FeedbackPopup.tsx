import { useState } from 'react'
import { Star } from 'lucide-react'

import { QUALITY_REASONS } from './api'
import type { QualityFeedbackRequest } from './types.quality'

interface FeedbackPopupProps {
  /** Envoie l'avis (au mieux) puis clôture la conversation. */
  onSubmit: (payload: QualityFeedbackRequest) => void
  /** Ferme la pop-in sans avis : la conversation se clôture normalement. */
  onSkip: () => void
  submitting?: boolean
}

/**
 * POP-IN de fin de conversation (§3/§4/§6/§7) : quelques secondes suffisent.
 * <ul>
 *   <li>note 1 à 5 (envoi possible dès la sélection) ;</li>
 *   <li>motifs d'insatisfaction proposés uniquement à partir de 3 étoiles ou moins ;</li>
 *   <li>commentaire toujours facultatif ;</li>
 *   <li>« Passer » clôture sans rien enregistrer.</li>
 * </ul>
 */
export default function FeedbackPopup({ onSubmit, onSkip, submitting }: FeedbackPopupProps) {
  const [rating, setRating] = useState<number | null>(null)
  const [reasons, setReasons] = useState<string[]>([])
  const [comment, setComment] = useState('')

  const showReasons = rating !== null && rating <= 3

  function toggleReason(code: string) {
    setReasons((current) =>
      current.includes(code) ? current.filter((item) => item !== code) : [...current, code],
    )
  }

  function send() {
    if (rating === null) return
    onSubmit({ rating, selectedReasons: reasons, comment: comment.trim() || undefined })
  }

  return (
    <div className="qlt-popup-backdrop" role="dialog" aria-modal="true" aria-labelledby="qlt-popup-title">
      <div className="qlt-popup">
        <h2 id="qlt-popup-title">How was your conversation with the Coach?</h2>
        <p className="mkt-subtitle">Your feedback helps us improve the Coach.</p>

        <div className="qlt-stars" role="radiogroup" aria-label="Rating from 1 to 5 stars">
          {[1, 2, 3, 4, 5].map((value) => (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={rating === value}
              aria-label={`${value} star${value > 1 ? 's' : ''}`}
              className={rating !== null && value <= rating ? 'qlt-star active' : 'qlt-star'}
              onClick={() => setRating(value)}
            >
              <Star size={28} fill={rating !== null && value <= rating ? 'currentColor' : 'none'} />
            </button>
          ))}
        </div>

        {rating !== null && rating >= 4 && <p className="qlt-thanks">Thank you for your feedback.</p>}

        {showReasons && (
          <div className="qlt-reasons">
            <p className="qlt-reasons-title">What could be improved?</p>
            {QUALITY_REASONS.map((reason) => (
              <label key={reason.code} className="qlt-reason">
                <input
                  type="checkbox"
                  checked={reasons.includes(reason.code)}
                  onChange={() => toggleReason(reason.code)}
                />
                <span>{reason.label}</span>
              </label>
            ))}
          </div>
        )}

        <textarea
          className="qlt-comment"
          rows={3}
          maxLength={1000}
          placeholder="Comment (optional)"
          value={comment}
          onChange={(event) => setComment(event.target.value)}
        />

        <div className="qlt-popup-actions">
          <button type="button" className="qlt-send" onClick={send} disabled={rating === null || submitting}>
            Send my feedback
          </button>
          <button type="button" className="mkt-action" onClick={onSkip} disabled={submitting}>
            Skip
          </button>
        </div>
        <p className="mkt-hint">
          Optional and anonymous feedback: no personal data is recorded. If saving
          fails, the conversation is closed as usual.
        </p>
      </div>
    </div>
  )
}
