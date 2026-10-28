import { useEffect, useState } from 'react'
import { ArrowLeft, BadgeCheck, Check, Send, ThumbsDown, ThumbsUp } from 'lucide-react'

import {
  ADVISOR_AREAS,
  ADVISOR_EMAIL_ASSESSMENTS,
  ADVISOR_INTEREST_LEVELS,
  ADVISOR_PRODUCT_REASONS,
  ADVISOR_REASONS,
  fetchAdvisorCandidates,
  fetchAdvisorDossier,
  submitAdvisorFeedback,
} from './api'
import type {
  AdvisorAssessment,
  AdvisorCatalogueProduct,
  AdvisorDossierView,
  AdvisorIssueInput,
  AdvisorProductFeedbackInput,
} from './types.advisor'

const ASSESSMENTS: { code: AdvisorAssessment; label: string; icon: typeof ThumbsUp }[] = [
  { code: 'RELEVANT', label: 'Relevant', icon: ThumbsUp },
  { code: 'NEEDS_IMPROVEMENT', label: 'Needs improvement', icon: ThumbsUp },
  { code: 'INCORRECT', label: 'Incorrect', icon: ThumbsDown },
]

/**
 * VUE « DOSSIER » ciblée par le lien du mail conseiller (§42/§43/§44) : le conseiller arrive
 * directement sur le dossier de la conversation (projet, résumé, produits, suivi, email préparé)
 * puis donne son avis en quelques secondes. Aucun dashboard statistique n'est affiché ici.
 */
export default function DossierFeedback({ sessionId }: { sessionId: string }) {
  const [view, setView] = useState<AdvisorDossierView | null>(null)
  const [loading, setLoading] = useState(true)
  const [available, setAvailable] = useState(true)
  const [assessment, setAssessment] = useState<AdvisorAssessment | null>(null)
  const [comment, setComment] = useState('')
  const [areaReasons, setAreaReasons] = useState<Record<string, string>>({})
  const [products, setProducts] = useState<Record<string, AdvisorProductFeedbackInput>>({})
  const [missing, setMissing] = useState<string[]>([])
  const [catalogue, setCatalogue] = useState<AdvisorCatalogueProduct[]>([])
  const [nextAction, setNextAction] = useState('')
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    void (async () => {
      try {
        const dossier = await fetchAdvisorDossier(sessionId)
        setView(dossier)
        setAvailable(dossier !== null)
      } catch {
        setAvailable(false)
      } finally {
        setLoading(false)
      }
    })()
  }, [sessionId])

  // Catalogue réel (produit oublié, §8) : chargé une fois à l'ouverture du dossier.
  useEffect(() => {
    void fetchAdvisorCandidates(30, true)
      .then((result) => setCatalogue(result.catalogue))
      .catch(() => undefined)
  }, [])

  function toggleArea(code: string, checked: boolean) {
    const updated = { ...areaReasons }
    if (checked) updated[code] = ''
    else delete updated[code]
    setAreaReasons(updated)
  }

  async function submit() {
    if (!assessment) return
    const issues: AdvisorIssueInput[] = Object.entries(areaReasons)
      .filter(([, reason]) => reason !== '')
      .map(([area, reason]) => ({ area, reason: reason || undefined }))
    const productFeedback: AdvisorProductFeedbackInput[] = Object.entries(products)
      .filter(([, value]) => Boolean(value.advisorAssessment) || Boolean(value.advisorInterestLevel))
      .map(([productId, value]) => ({ ...value, productId }))
    try {
      const response = await submitAdvisorFeedback({
        sessionId,
        overallAssessment: assessment,
        issues,
        productFeedback,
        missingProductIds: missing,
        nextActionAssessment: nextAction || undefined,
        clientEmailAssessment: email || undefined,
        comment: comment || undefined,
      })
      if (response.status === 'SAVED') {
        setSent(true)
        setMessage(null)
      } else {
        setMessage(response.message ?? `Feedback not saved (${response.status}).`)
      }
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Unknown error')
    }
  }

  if (loading) {
    return (
      <div className="logs-shell marketing-page">
        <p className="mkt-empty">Loading the case file…</p>
      </div>
    )
  }

  if (!available || !view) {
    return (
      <div className="logs-shell marketing-page">
        <header className="logs-header">
          <a className="logs-back" href="#/advisor-feedback">
            <ArrowLeft size={16} /> Follow-up dashboard
          </a>
          <h1>
            <BadgeCheck size={20} /> Advisor feedback
          </h1>
        </header>
        <div className="mkt-card">
          <h2>This case file is no longer available.</h2>
          <p className="mkt-empty">
            The corresponding conversation is no longer accessible (unknown session, backend restarted or
            case file purged). No data is lost: the follow-up dashboard remains available.
          </p>
        </div>
      </div>
    )
  }

  const dossier = view.dossier

  return (
    <div className="logs-shell marketing-page">
      <header className="logs-header">
        <a className="logs-back" href="#/advisor-feedback">
          <ArrowLeft size={16} /> Follow-up dashboard
        </a>
        <h1>
          <BadgeCheck size={20} /> Advisor feedback
        </h1>
        <span className="mkt-muted">
          {view.feedbackStatus === 'COMPLETED'
            ? `Feedback already given (version ${view.feedback?.version ?? 1})`
            : 'Awaiting your feedback'}
        </span>
      </header>

      <div className="mkt-card wide">
        <h2>Conversation of {dossier.timestamp.slice(0, 10)}</h2>
        <div className="mkt-report-cols">
          <div>
            <h3>Detected project</h3>
            <p className="mkt-empty">{dossier.mainProject || 'Not identified'}</p>
            {dossier.otherProjects.length > 0 && (
              <p className="mkt-muted">Other topics: {dossier.otherProjects.join(' · ')}</p>
            )}
            {dossier.preferences.length > 0 && (
              <>
                <h3>Preferences identified</h3>
                <ul className="mkt-report-list">
                  {dossier.preferences.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </>
            )}
          </div>
          <div>
            <h3>Recommended follow-up</h3>
            {dossier.nextActions.length === 0 ? (
              <p className="mkt-empty">No follow-up action extracted from the advisor email.</p>
            ) : (
              <ul className="mkt-report-list">
                {dossier.nextActions.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            )}
          </div>
        </div>
        <h3>Summary prepared by the Coach</h3>
        <pre className="afb-summary">{dossier.advisorEmail.body || '(empty)'}</pre>
      </div>

      {sent ? (
        <div className="mkt-card wide">
          <h2>
            <Check size={16} /> Thank you, your feedback has been recorded.
          </h2>
          <p className="mkt-empty">
            It feeds the relevance indicators and the AI analysis. No change to the Coach is applied
            automatically: the team decides on evolutions.
          </p>
          <a className="mkt-action" href="#/advisor-feedback">
            Back to the follow-up dashboard
          </a>
        </div>
      ) : (
        <div className="mkt-card wide">
          <h2>Your feedback on this analysis</h2>
          <div className="mkt-periods" style={{ width: 'fit-content' }}>
            {ASSESSMENTS.map((item) => {
              const Icon = item.icon
              return (
                <button
                  key={item.code}
                  type="button"
                  className={assessment === item.code ? 'mkt-period active' : 'mkt-period'}
                  onClick={() => setAssessment(item.code)}
                >
                  <Icon size={13} /> {item.label}
                </button>
              )
            })}
          </div>

          {(assessment === 'NEEDS_IMPROVEMENT' || assessment === 'INCORRECT') && (
            <>
              <h3>What needs to be improved&nbsp;?</h3>
              <div className="afb-areas">
                {ADVISOR_AREAS.map((area) => (
                  <div key={area.code} className="afb-area">
                    <label className="qlt-reason">
                      <input
                        type="checkbox"
                        checked={area.code in areaReasons}
                        onChange={(event) => toggleArea(area.code, event.target.checked)}
                      />
                      <span>{area.label}</span>
                    </label>
                    {area.code in areaReasons && (
                      <select
                        aria-label={`Reason for ${area.label}`}
                        value={areaReasons[area.code]}
                        onChange={(event) =>
                          setAreaReasons({ ...areaReasons, [area.code]: event.target.value })
                        }
                      >
                        <option value="">Reason (optional)</option>
                        {ADVISOR_REASONS.map((reason) => (
                          <option key={reason.code} value={reason.code}>
                            {reason.label}
                          </option>
                        ))}
                      </select>
                    )}
                  </div>
                ))}
              </div>
            </>
          )}

          <h3>Products identified</h3>
          {dossier.productsOfInterest.length === 0 ? (
            <p className="mkt-empty">No product of interest in this case file.</p>
          ) : (
            <table className="mkt-table compact">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>AI interest</th>
                  <th>Coach reason</th>
                  <th>Your assessment</th>
                  <th>Corrected interest</th>
                  <th>Reason</th>
                </tr>
              </thead>
              <tbody>
                {dossier.productsOfInterest.map((product) => {
                  const entry = products[product.productId] ?? { productId: product.productId }
                  const update = (patch: Partial<AdvisorProductFeedbackInput>) =>
                    setProducts({
                      ...products,
                      [product.productId]: {
                        ...entry,
                        productId: product.productId,
                        productName: product.name ?? undefined,
                        aiInterestLevel: product.interestLevel ?? undefined,
                        ...patch,
                      },
                    })
                  return (
                    <tr key={product.productId}>
                      <td className="mkt-strong">{product.name ?? product.productId}</td>
                      <td>{product.interestLevel ?? '—'}</td>
                      <td className="mkt-muted">{product.interestReason ?? '—'}</td>
                      <td>
                        <button
                          type="button"
                          className={entry.advisorAssessment === 'RELEVANT_PRODUCT' ? 'mkt-period active' : 'mkt-period'}
                          onClick={() => update({ advisorAssessment: 'RELEVANT_PRODUCT' })}
                        >
                          <ThumbsUp size={12} /> Relevant
                        </button>{' '}
                        <button
                          type="button"
                          className={entry.advisorAssessment === 'NOT_RELEVANT' ? 'mkt-period active' : 'mkt-period'}
                          onClick={() => update({ advisorAssessment: 'NOT_RELEVANT' })}
                        >
                          <ThumbsDown size={12} /> Not relevant
                        </button>
                      </td>
                      <td>
                        <select
                          aria-label={`Corrected interest level for ${product.productId}`}
                          value={entry.advisorInterestLevel ?? ''}
                          onChange={(event) => update({ advisorInterestLevel: event.target.value || undefined })}
                        >
                          <option value="">(unchanged)</option>
                          {ADVISOR_INTEREST_LEVELS.map((level) => (
                            <option key={level} value={level}>
                              {level}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        {entry.advisorAssessment === 'NOT_RELEVANT' ? (
                          <select
                            aria-label={`Reason for ${product.productId}`}
                            value={entry.reason ?? ''}
                            onChange={(event) => update({ reason: event.target.value || undefined })}
                          >
                            <option value="">Reason (optional)</option>
                            {ADVISOR_PRODUCT_REASONS.map((reason) => (
                              <option key={reason.code} value={reason.code}>
                                {reason.label}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <span className="mkt-muted">—</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}

          <h3>Was a relevant product missed&nbsp;?</h3>
          <select
            aria-label="Missing product (catalogue)"
            value=""
            onChange={(event) => {
              if (event.target.value && !missing.includes(event.target.value)) {
                setMissing([...missing, event.target.value])
              }
            }}
          >
            <option value="">— add a product from the catalogue —</option>
            {catalogue.map((product) => (
              <option key={product.productId} value={product.productId}>
                {product.productName}
              </option>
            ))}
          </select>
          {missing.length > 0 && (
            <ul className="mkt-report-list">
              {missing.map((id) => (
                <li key={id}>
                  {catalogue.find((product) => product.productId === id)?.productName ?? id}
                </li>
              ))}
            </ul>
          )}

          <h3>Recommended follow-up and prepared email</h3>
          <div className="mkt-report-cols">
            <div>
              <select
                aria-label="Recommended follow-up assessment"
                value={nextAction}
                onChange={(event) => setNextAction(event.target.value)}
              >
                <option value="">Recommended follow-up: not assessed</option>
                <option value="RELEVANT">Relevant</option>
                <option value="NEEDS_IMPROVEMENT">Needs improvement</option>
                <option value="INCORRECT">Incorrect</option>
              </select>
            </div>
            <div>
              <select
                aria-label="Prepared email assessment"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              >
                <option value="">Customer email: not assessed</option>
                {ADVISOR_EMAIL_ASSESSMENTS.map((item) => (
                  <option key={item.code} value={item.code}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <h3>Optional comment</h3>
          <textarea
            className="qlt-comment"
            rows={3}
            maxLength={1000}
            placeholder="Comment (optional)"
            value={comment}
            onChange={(event) => setComment(event.target.value)}
          />

          {message && <div className="mkt-notice warn">{message}</div>}
          <div className="qtl-send-row">
            <button type="button" className="qlt-send" onClick={() => void submit()} disabled={!assessment}>
              <Send size={14} /> Send my feedback
            </button>
            <p className="mkt-hint">
              Already recorded feedback can be revised: a new version is created and the history is kept. No
              personal data is stored.
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
