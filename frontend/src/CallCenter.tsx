import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ArrowLeft,
  BadgeCheck,
  Check,
  ChevronDown,
  ChevronRight,
  Download,
  History,
  MessageSquare,
  Paperclip,
  PhoneCall,
  RefreshCw,
  Search,
  Sparkles,
  X,
} from 'lucide-react'

import { fetchConversationDirectory, fetchConversationDirectoryDetail, updateDirectoryStatus } from './api'
import { ADVISOR_INTENT_UPDATED_EVENT, getAdvisorIntent } from './advisorCallback'
import { DOSSIER_STATUSES } from './types.directory'
import type {
  DirectoryDetail,
  DirectoryList,
  DirectoryOrder,
  DirectorySort,
} from './types.directory'
import { renderMessageContent } from './messageFormat'

/** Périodes proposées (jours) ; `0` = tout l'historique disponible. */
const PERIODS: { days: number; label: string }[] = [
  { days: 5, label: 'Last 5 days' },
  { days: 10, label: 'Last 10 days' },
  { days: 30, label: 'Last 30 days' },
  { days: 0, label: 'All' },
]

function wait(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

/** Délai par défaut, en secondes, entre la fin de la réponse IA et la question suivante du client (replay). */
const DEFAULT_REPLAY_DELAY_SECONDS = 3
const REPLAY_DELAY_STORAGE_KEY = 'coach-replay-next-question-delay'
const MIN_REPLAY_DELAY_SECONDS = 0
const MAX_REPLAY_DELAY_SECONDS = 60

function clampReplayDelaySeconds(value: number): number {
  if (!Number.isFinite(value)) return DEFAULT_REPLAY_DELAY_SECONDS
  return Math.min(MAX_REPLAY_DELAY_SECONDS, Math.max(MIN_REPLAY_DELAY_SECONDS, Math.round(value)))
}

const COLUMNS: { key: DirectorySort | 'agence' | 'statut' | 'rdv' | 'rappel'; label: string; sortable: boolean }[] = [
  { key: 'categorie', label: 'Category', sortable: true },
  { key: 'client', label: 'Customer', sortable: true },
  { key: 'agence', label: 'Agency', sortable: false },
  { key: 'titre', label: 'Conversation', sortable: true },
  { key: 'score', label: 'Sales score', sortable: true },
  { key: 'statut', label: 'Status', sortable: false },
  { key: 'rdv', label: 'Appointment booked', sortable: false },
  { key: 'rappel', label: 'Callback requested', sortable: false },
  { key: 'date', label: 'DATE', sortable: true },
]

/**
 * PAGE CENTRE D'APPELS (`#/centre-appels`) : annuaire des conversations clôturées pour l'équipe
 * commerciale. Tableau filtrable (période, catégorie, recherche) et triable (client, catégorie, titre,
 * score commercial, date), et pop-in de détail qui reprend la synthèse envoyée au conseiller par mail
 * (sans le brouillon destiné au client), avec le transcript repliable.
 * <p>
 * `initialSessionId` vient du lien « Ouvrir le dossier du client » du mail conseiller
 * (`#/centre-appels/<sessionId>`) : la pop-in de ce dossier est ouverte au chargement.
 */
export default function CallCenter({ initialSessionId }: { initialSessionId?: string }) {
  const [days, setDays] = useState(10)
  const [category, setCategory] = useState('')
  const [agency, setAgency] = useState('')
  const [status, setStatus] = useState('')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<DirectorySort>('date')
  const [order, setOrder] = useState<DirectoryOrder>('desc')
  const [list, setList] = useState<DirectoryList | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [openSession, setOpenSession] = useState<string | null>(initialSessionId ?? null)
  const [, setIntentVersion] = useState(0)

  useEffect(() => {
    const onIntentUpdated = () => setIntentVersion((version) => version + 1)
    window.addEventListener(ADVISOR_INTENT_UPDATED_EVENT, onIntentUpdated)
    return () => window.removeEventListener(ADVISOR_INTENT_UPDATED_EVENT, onIntentUpdated)
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await fetchConversationDirectory({
        days,
        category: category || undefined,
        agency: agency || undefined,
        status: status || undefined,
        q: query.trim() || undefined,
        sort,
        order,
      })
      setList(data)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to load')
    } finally {
      setLoading(false)
    }
  }, [days, category, agency, status, query, sort, order])

  useEffect(() => {
    void load()
  }, [load])

  /** Clic sur un en-tête : bascule le sens du tri (ou passe sur la nouvelle colonne). */
  function toggleSort(key: DirectorySort) {
    if (sort === key) {
      setOrder((current) => (current === 'desc' ? 'asc' : 'desc'))
      return
    }
    setSort(key)
    setOrder(key === 'date' || key === 'score' ? 'desc' : 'asc')
  }

  const rows = list?.rows ?? []
  const categories = list?.categories ?? []
  const agencies = list?.agencies ?? []
  const statuses = list?.statuses ?? []
  const byPriority = list?.byPriority ?? {}

  return (
    <div className="logs-shell marketing-page cc-page">
      <header className="logs-header">
        <a className="logs-back" href="#/">
          <ArrowLeft size={16} /> Back to chat
        </a>
        <h1>
          <PhoneCall size={20} /> Call center — conversations
        </h1>
        <span className="logs-status ok">
          <span className="status-dot" /> {rows.length} conversation(s)
        </span>
      </header>

      <p className="mkt-subtitle">
        All conversations closed with the AI Coach, with their <strong>sales score</strong>{' '}
        (used to prioritise follow-ups). The detail shows the same summary as the email sent to the advisor, its
        <strong> attachment</strong> (the email draft prepared for the customer) and the full
        exchange.
      </p>

      <div className="mkt-toolbar">
        <div className="mkt-periods">
          {PERIODS.map((item) => (
            <button
              key={item.days}
              type="button"
              className={days === item.days ? 'mkt-period active' : 'mkt-period'}
              onClick={() => setDays(item.days)}
            >
              {item.label}
            </button>
          ))}
        </div>
        <select
          aria-label="Filter by category"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
        >
          <option value="">All categories</option>
          {categories.map((item) => (
            <option key={item.code} value={item.code}>
              {item.label} ({item.count})
            </option>
          ))}
        </select>
        <select
          aria-label="Filter by agency"
          title="Agency"
          value={agency}
          onChange={(event) => setAgency(event.target.value)}
        >
          <option value="">All agencies</option>
          {agencies.map((item) => (
            <option key={item.code} value={item.code}>
              {item.name} ({item.code}) ({item.count})
            </option>
          ))}
        </select>
        <select
          aria-label="Filter by status"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
        >
          <option value="">All statuses</option>
          {DOSSIER_STATUSES.map((item) => (
            <option key={item.code} value={item.code}>
              {item.label} ({statuses.find((entry) => entry.code === item.code)?.count ?? 0})
            </option>
          ))}
        </select>
        <label className="cc-search">
          <Search size={15} />
          <input
            type="search"
            placeholder="Customer, title, project, product…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
        <button type="button" className="mkt-action" onClick={() => void load()} disabled={loading}>
          <RefreshCw size={15} /> Refresh
        </button>
      </div>

      {(Object.keys(byPriority).length > 0 || list) && (
        <div className="cc-kpis">
          <span className="mkt-kpi">
            <strong>{list?.total ?? 0}</strong> conversation(s)
          </span>
          {['VERY_HIGH', 'HIGH', 'MEDIUM', 'LOW'].map((priority) => (
            <span key={priority} className={`cc-kpi ${scoreClass(priority)}`}>
              {priorityLabel(priority)}: <strong>{byPriority[priority] ?? 0}</strong>
            </span>
          ))}
        </div>
      )}

      {loading && <div className="logs-empty">Loading conversations…</div>}
      {error && <div className="logs-error">{error}</div>}

      {!loading && !error && rows.length === 0 && (
        <div className="logs-empty">
          No conversation closed for this period. Conversations appear here after they are
          closed ("End the conversation" button in the chat), including in demo mode.
        </div>
      )}

      {!loading && !error && rows.length > 0 && (
        <div className="mkt-table-scroll">
          <table className="mkt-table compact">
            <thead>
              <tr>
                {COLUMNS.map((column) => (
                  <th key={column.key}>
                    {column.sortable ? (
                      <button
                        type="button"
                        className="cc-sort"
                        onClick={() => toggleSort(column.key as DirectorySort)}
                        title={`Sort by ${column.label.toLowerCase()}`}
                      >
                        {column.label}
                        {sort === column.key ? (order === 'asc' ? ' ▲' : ' ▼') : ''}
                      </button>
                    ) : (
                      column.label
                    )}
                  </th>
                ))}
                <th className="cc-offers">Offers</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr
                  key={row.sessionId}
                  className="cc-clickable-row"
                  onClick={() => setOpenSession(row.sessionId)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault()
                      setOpenSession(row.sessionId)
                    }
                  }}
                  role="button"
                  tabIndex={0}
                >
                  <td>
                    <span className="cc-chip">{row.categoryLabel}</span>
                  </td>
                  <td>{row.customerId ?? '—'}</td>
                  <td>
                    {row.agencyCode ? <span title={row.agencyName ?? undefined}>{row.agencyCode}</span> : '—'}
                  </td>
                  <td className="cc-title">{row.title}</td>
                  <td>
                    <span className={`cc-score ${scoreClass(row.priority)}`}>
                      {row.score === null ? '—' : `${row.score}/100`}
                    </span>
                    <span className="cc-score-label">{row.priorityLabel ?? ''}</span>
                  </td>
                  <td>
                    <span
                      className={`cc-status ${statusClass(effectiveStatusCode(row.status, getAdvisorIntent(row.sessionId)))}`}
                      title={row.statusUpdatedAt ? `Status changed on ${formatDateTime(row.statusUpdatedAt)}` : undefined}
                    >
                      {statusLabelOf(effectiveStatusCode(row.status, getAdvisorIntent(row.sessionId)))}
                    </span>
                    {row.noteCount > 0 && (
                      <span className="cc-note-count" title={`${row.noteCount} message(s) left on this case file`}>
                        <MessageSquare size={12} /> {row.noteCount}
                      </span>
                    )}
                  </td>
                  <td>
                    {getAdvisorIntent(row.sessionId).prisRDV && (
                      <Check className="cc-intent-icon positive" size={16} aria-label="Yes" />
                    )}
                  </td>
                  <td>
                    {getAdvisorIntent(row.sessionId).etreRappele && (
                      <Check className="cc-intent-icon positive" size={16} aria-label="Yes" />
                    )}
                  </td>
                  <td>{formatDateTime(row.closedAt)}</td>
                  <td className="cc-offers">
                    {row.productCount > 0
                      ? row.topProduct
                        ? `${row.topProduct}${row.productCount > 1 ? ', ....' : ''}`
                        : `${row.productCount} offer(s)`
                      : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {openSession && (
        <ConversationPopup
          sessionId={openSession}
          onClose={() => setOpenSession(null)}
          onStatusChanged={() => void load()}
        />
      )}
    </div>
  )
}

/** Pop-in de détail : synthèse conseiller, score expliqué, statut, actions et transcript repliable. */
function ConversationPopup({ sessionId, onClose, onStatusChanged }: {
  sessionId: string
  onClose: () => void
  onStatusChanged: () => void
}) {
  const [detail, setDetail] = useState<DirectoryDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showTranscript, setShowTranscript] = useState(false)
  const [showCriteria, setShowCriteria] = useState(true)
  /** Synthèse conseiller : PILABLE, **repliée par défaut** comme la conversation complète : la pop-in
   *  s'ouvre sur le score, le suivi du dossier et les offres, et le conseiller déplie la synthèse (le
   *  contenu est déjà chargé, aucun appel réseau). */
  const [showSynthesis, setShowSynthesis] = useState(false)
  /** PIÈCE JOINTE du mail conseiller (brouillon d'email client) : même bloc pilable, repliée par défaut. */
  const [showAttachment, setShowAttachment] = useState(false)
  const [statusDraft, setStatusDraft] = useState('')
  const [statusComment, setStatusComment] = useState('')
  const [savingStatus, setSavingStatus] = useState(false)
  const [exportingPdf, setExportingPdf] = useState(false)
  const [replayDelaySeconds, setReplayDelaySeconds] = useState(() => {
    const stored = sessionStorage.getItem(REPLAY_DELAY_STORAGE_KEY)
    const parsed = stored === null ? Number.NaN : Number(stored)
    return Number.isFinite(parsed) && parsed >= MIN_REPLAY_DELAY_SECONDS && parsed <= MAX_REPLAY_DELAY_SECONDS
      ? parsed : DEFAULT_REPLAY_DELAY_SECONDS
  })
  const popupRef = useRef<HTMLDivElement>(null)

  /** Lance la démonstration en mémorisant le délai choisi entre deux questions du client. */
  function startReplay() {
    sessionStorage.setItem(REPLAY_DELAY_STORAGE_KEY, String(clampReplayDelaySeconds(replayDelaySeconds)))
    window.location.hash = `#/replay/${encodeURIComponent(sessionId)}`
  }

  useEffect(() => {
    void (async () => {
      setLoading(true)
      try {
        const loaded = await fetchConversationDirectoryDetail(sessionId)
        setDetail(loaded)
        setStatusDraft(effectiveStatusCode(loaded.row.status, getAdvisorIntent(sessionId)))
        setError(null)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Case file unavailable')
      } finally {
        setLoading(false)
      }
    })()
  }, [sessionId])

  /** Animation de lecture après le retour automatique d’une démonstration Coach. */
  useEffect(() => {
    if (!detail || sessionStorage.getItem('coach-replay-return-session') !== sessionId) return undefined
    sessionStorage.removeItem('coach-replay-return-session')
    let cancelled = false

    void (async () => {
      await wait(4000)
      if (cancelled) return
      setShowSynthesis(true)
      await wait(100)
      if (cancelled) return
      popupRef.current?.querySelector<HTMLElement>('.cc-synthesis-body')?.scrollIntoView({
        behavior: 'smooth',
        block: 'end',
      })
      await wait(2000)
      if (cancelled || !detail.customerEmailBody) return
      setShowAttachment(true)
      await wait(100)
      if (cancelled) return
      popupRef.current?.querySelector<HTMLElement>('.cc-attachment-body')?.scrollIntoView({
        behavior: 'smooth',
        block: 'end',
      })
    })()

    return () => {
      cancelled = true
    }
  }, [detail, sessionId])

  /** Enregistre le nouveau statut d'avancement du dossier (action du centre d'appels). */
  async function saveStatus() {
    if (!statusDraft) return
    setSavingStatus(true)
    try {
      const updated = await updateDirectoryStatus(sessionId, statusDraft, statusComment.trim() || undefined)
      setDetail(updated)
      setStatusDraft(updated.row.status)
      setStatusComment('')
      setError(null)
      onStatusChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Status not saved')
    } finally {
      setSavingStatus(false)
    }
  }

  /** Exporte le même rendu que la popup sur une seule page PDF très longue. */
  async function downloadConversationPdf() {
    if (!detail || exportingPdf) return
    const previousState = { showCriteria, showSynthesis, showAttachment, showTranscript }
    setExportingPdf(true)
    setShowCriteria(true)
    setShowSynthesis(true)
    setShowAttachment(Boolean(detail.customerEmailBody))
    setShowTranscript(true)

    try {
      const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
        import('html2canvas'),
        import('jspdf'),
      ])
      // Laisser React rendre les sections avant la capture du DOM.
      await new Promise<void>((resolve) => window.setTimeout(resolve, 100))
      const popup = popupRef.current
      if (!popup) throw new Error('Popup unavailable')

      const canvas = await html2canvas(popup, {
        backgroundColor: '#ffffff',
        scale: 2,
        useCORS: true,
        logging: false,
        onclone: (clonedDocument) => {
          const clonedPopup = clonedDocument.querySelector('.cc-popup') as HTMLElement | null
          const clonedBody = clonedDocument.querySelector('.cc-popup-body') as HTMLElement | null
          if (clonedPopup) {
            clonedPopup.style.maxHeight = 'none'
            clonedPopup.style.height = 'auto'
          }
          if (clonedBody) {
            clonedBody.style.maxHeight = 'none'
            clonedBody.style.overflow = 'visible'
          }
          clonedDocument.querySelectorAll('.cc-transcript, .cc-mail').forEach((element) => {
            const block = element as HTMLElement
            block.style.maxHeight = 'none'
            block.style.overflow = 'visible'
          })
          const clonedFooter = clonedDocument.querySelector('.cc-popup-foot') as HTMLElement | null
          if (clonedFooter) clonedFooter.style.display = 'none'
        },
      })

      const margin = 10
      const pageWidth = 210
      const imageWidth = pageWidth - margin * 2
      const imageHeight = (canvas.height * imageWidth) / canvas.width
      const pdf = new jsPDF({
        orientation: 'portrait',
        unit: 'mm',
        format: [pageWidth, imageHeight + margin * 2],
        compress: true,
      })
      const image = canvas.toDataURL('image/png')
      pdf.addImage(image, 'PNG', margin, margin, imageWidth, imageHeight, undefined, 'FAST')

      pdf.setProperties({
        title: `Conversation ${row?.customerId ?? sessionId}`,
        subject: 'Conversation detail — Call center',
      })
      pdf.save(`conversation-${sessionId.replace(/[^a-z0-9_-]/gi, '_')}.pdf`)
    } catch (e) {
      setError(e instanceof Error ? `PDF export failed: ${e.message}` : 'PDF export failed')
    } finally {
      setShowCriteria(previousState.showCriteria)
      setShowSynthesis(previousState.showSynthesis)
      setShowAttachment(previousState.showAttachment)
      setShowTranscript(previousState.showTranscript)
      setExportingPdf(false)
    }
  }

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const row = detail?.row
  const messages = detail?.transcript ?? []

  return (
    <div className="qlt-popup-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div ref={popupRef} className="cc-popup" onClick={(event) => event.stopPropagation()}>
        <div className="cc-popup-head">
          <div>
            <h2>{row?.title ?? 'Conversation'}</h2>
            <p className="mkt-subtitle">
              {row?.customerId ?? 'Unknown customer'} · {row?.categoryLabel ?? '—'} · closed on{' '}
              {formatDateTime(row?.closedAt ?? null)}
            </p>
          </div>
          <button type="button" className="icon-button" onClick={onClose} title="Close">
            <X size={18} />
          </button>
        </div>

        {loading && <div className="logs-empty">Loading case file…</div>}
        {error && <div className="logs-error">{error}</div>}

        {!loading && !error && detail && (
          <div className="cc-popup-body">
            <div className="cc-score-box">
              <span className={`cc-score big ${scoreClass(row?.priority ?? null)}`}>
                {row?.score === null || row?.score === undefined ? '—' : `${row.score}/100`}
              </span>
              <div>
                <strong>Sales score — {row?.priorityLabel ?? 'not calculated'}</strong>
                <ul className="cc-list">
                  {(detail.scoreReasons ?? []).map((reason, index) => (
                    <li key={`reason-${index}`}>{reason}</li>
                  ))}
                  {(detail.scoreReasons ?? []).length === 0 && (
                    <li>No explanation recorded for this case file.</li>
                  )}
                </ul>
                {(detail.scoreCriteria ?? []).length > 0 && (
                  <>
                    <button type="button" className="cc-toggle" onClick={() => setShowCriteria((v) => !v)}>
                      {showCriteria ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                      Criteria measured ({detail.scoreCriteria.length})
                      {detail.scoreProposedByAi ? ' — score refined by AI' : ' — score calculated by the system'}
                    </button>
                    {showCriteria && (
                      <ul className="cc-list small">
                        {detail.scoreCriteria.map((criterion, index) => (
                          <li key={`criterion-${index}`}>{criterion}</li>
                        ))}
                      </ul>
                    )}
                  </>
                )}
              </div>
              {detail.contactPhone ? (
                <a className="mkt-action cc-call" href={`tel:${detail.contactPhone}`}>
                  <PhoneCall size={15} /> Call customer ({detail.contactPhone})
                </a>
              ) : null}
            </div>

            <section className="cc-section cc-status-box">
              <h3>
                <MessageSquare size={16} /> Case file follow-up
              </h3>
              <div className="cc-status-row">
                <span
                  className={`cc-status big ${statusClass(
                    effectiveStatusCode(row?.status ?? '', getAdvisorIntent(sessionId)),
                  )}`}
                >
                  {statusLabelOf(effectiveStatusCode(row?.status ?? '', getAdvisorIntent(sessionId)))}
                </span>
                <select
                  aria-label="New status"
                  value={statusDraft}
                  disabled={savingStatus}
                  onChange={(event) => setStatusDraft(event.target.value)}
                >
                  {DOSSIER_STATUSES.map((option) => (
                    <option key={option.code} value={option.code}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <textarea
                rows={2}
                className="cc-status-message"
                placeholder="Message: call report, customer objection, next action…"
                value={statusComment}
                disabled={savingStatus}
                onChange={(event) => setStatusComment(event.target.value)}
              />
              <div className="cc-status-row">
                <button
                  type="button"
                  className="mkt-action"
                  disabled={savingStatus
                    || (statusDraft === row?.status && statusComment.trim().length === 0)}
                  onClick={() => void saveStatus()}
                >
                  {savingStatus ? 'Saving…' : 'Save follow-up'}
                </button>
                <span className="cc-subject">
                  The status can be changed on its own, or with a message: a message on its own is
                  logged without changing the status.
                </span>
              </div>
              {(detail.statusHistory ?? []).length > 0 ? (
                <ul className="cc-list small cc-journal">
                  {detail.statusHistory.slice().reverse().map((event, index) => {
                    const changed = event.previousStatus !== event.status
                    return (
                      <li key={`status-${index}`}>
                        <strong>{formatDateTime(event.timestamp)}</strong>
                        {changed
                          ? <> — status: {statusLabelOf(event.previousStatus)} → <strong>{statusLabelOf(event.status)}</strong></>
                          : <> — {statusLabelOf(event.status)}, unchanged</>}
                        {event.comment ? <> · “{event.comment}”</> : null}
                      </li>
                    )
                  })}
                </ul>
              ) : (
                <p className="cc-subject">No follow-up recorded: this case file has not been handled yet.</p>
              )}
            </section>

            {(detail.nextActions ?? []).length > 0 && (
              <section className="cc-section">
                <h3>Next follow-up actions</h3>
                <ul className="cc-list">
                  {detail.nextActions.map((action, index) => (
                    <li key={`action-${index}`}>{action}</li>
                  ))}
                </ul>
              </section>
            )}

            {(detail.products ?? []).length > 0 && (
              <section className="cc-section">
                <h3>Offers of interest</h3>
                <table className="mkt-table compact">
                  <thead>
                    <tr>
                      <th>Offer</th>
                      <th>Family</th>
                      <th>Interest</th>
                      <th>Why</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.products.map((product, index) => (
                      <tr key={`product-${index}`}>
                        <td>{product.name ?? '—'}</td>
                        <td>{product.category ?? '—'}</td>
                        <td>
                          <span className={`cc-chip interest-${(product.interestLevel ?? '').toLowerCase()}`}>
                            {product.interestLevel ?? '—'}
                          </span>
                        </td>
                        <td>{product.interestReason ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            )}

            <section className="cc-section">
              {/* Même bloc pilable que « Conversation complète » : aucun chargement, le contenu est déjà là. */}
              <button
                type="button"
                className="cc-toggle"
                aria-expanded={showSynthesis}
                onClick={() => setShowSynthesis((v) => !v)}
              >
                {showSynthesis ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                <Sparkles size={15} /> Summary sent to the advisor
              </button>
              {showSynthesis && (
                <div className="cc-toggle-body cc-synthesis-body">
                  <p className="cc-subject">{detail.advisorSubject ?? '—'}</p>
                  <div className="cc-mail">
                    {renderMessageContent(`dossier-${sessionId}`, detail.advisorBody ?? '', sessionId)}
                  </div>
                </div>
              )}
            </section>

            {detail.customerEmailBody && (
              <section className="cc-section">
                {/* PIÈCE JOINTE du mail conseiller : le brouillon d'email préparé pour le client, tel qu'il
                    a été transmis (jamais envoyé automatiquement). */}
                <button
                  type="button"
                  className="cc-toggle"
                  aria-expanded={showAttachment}
                  onClick={() => setShowAttachment((v) => !v)}
                >
                  {showAttachment ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                  <Paperclip size={15} /> Attachment — prepared customer email
                </button>
                {showAttachment && (
                  <div className="cc-toggle-body cc-attachment-body">
                    <p className="cc-subject">{detail.customerEmailSubject ?? '—'}</p>
                    <div className="cc-mail">
                      {renderMessageContent(`client-${sessionId}`, detail.customerEmailBody, sessionId)}
                    </div>
                    <p className="cc-hint">
                      Draft prepared by the Coach: it is never sent automatically — review and
                      adapt it before sending anything to the customer.
                    </p>
                  </div>
                )}
              </section>
            )}

            <section className="cc-section">
              <button
                type="button"
                className="cc-toggle"
                aria-expanded={showTranscript}
                onClick={() => setShowTranscript((v) => !v)}
              >
                {showTranscript ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                <History size={15} /> Full conversation ({messages.length} message(s))
              </button>
              {showTranscript && (
                <div className="cc-transcript">
                  {messages.length === 0 && (
                    <div className="logs-empty">No exchange recorded for this case file.</div>
                  )}
                  {messages.map((message, index) => (
                    <div key={`msg-${index}`} className={`message-row ${message.role ?? 'user'}`}>
                      {message.role === 'assistant' && (
                        <div className="avatar assistant-avatar">
                          <Sparkles size={15} />
                        </div>
                      )}
                      <div className={`message-bubble ${message.role ?? 'user'}`}>
                        <div className="message-meta">
                          <span>{message.role === 'user' ? 'Customer' : 'AI Coach'}</span>
                          {message.timestamp ? <span>{formatTime(message.timestamp)}</span> : null}
                        </div>
                        <div className="message-text">
                          {message.role === 'assistant'
                            ? renderMessageContent(`dossier-${sessionId}-${index}`, message.content ?? '', sessionId)
                            : message.content}
                        </div>
                      </div>
                      {message.role === 'user' && <div className="avatar user-avatar">C</div>}
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}

        <div className="cc-popup-foot">
          <label
            className="cc-replay-delay"
            htmlFor="cc-replay-delay-input"
            title="Time between the end of the AI answer and the customer's next question (0 = manual: press Space to continue)"
          >
            <span>Next question delay</span>
            <input
              id="cc-replay-delay-input"
              type="number"
              min={MIN_REPLAY_DELAY_SECONDS}
              max={MAX_REPLAY_DELAY_SECONDS}
              step={1}
              value={replayDelaySeconds}
              onChange={(event) => setReplayDelaySeconds(Number(event.target.value))}
              onBlur={() => setReplayDelaySeconds((current) => clampReplayDelaySeconds(current))}
            />
            <span>s</span>
          </label>
          <button
            type="button"
            className="mkt-action cc-call-client"
            onClick={startReplay}
            disabled={loading || !detail}
            title="Replay this conversation in the Coach for a demo"
          >
            Replay
          </button>
          <button
            type="button"
            className="mkt-action cc-export"
            onClick={() => void downloadConversationPdf()}
            disabled={loading || !detail || exportingPdf}
          >
            <Download size={15} /> {exportingPdf ? 'Preparing PDF…' : 'Download as PDF'}
          </button>
          {detail?.feedbackUrl ? (
            <a className="logs-back" href={`#/advisor-feedback/session/${encodeURIComponent(sessionId)}`}>
              <BadgeCheck size={15} /> {detail.evaluated ? 'Advisor feedback saved' : 'Rate the follow-up'}
            </a>
          ) : null}
          <button type="button" className="mkt-action" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  )
}

/** Classe CSS du badge de statut d'avancement à partir du code. */
function statusClass(status: string): string {
  return (status ?? '').toLowerCase()
}

/** Statut initial issu des actions client, sans écraser un suivi déjà saisi par le conseiller. */
function effectiveStatusCode(status: string, intent: ReturnType<typeof getAdvisorIntent>): string {
  if (status !== 'NOUVEAU') return status
  if (intent.prisRDV) return 'RDV'
  if (intent.etreRappele) return 'A_CONTACTER'
  return status
}

/** Libellé d'un code de statut (même liste que le backend, ordre du cycle de vie). */
function statusLabelOf(code: string | null): string {
  return DOSSIER_STATUSES.find((option) => option.code === code)?.label ?? (code ?? '—')
}

/** Classe CSS du badge de score à partir de la priorité (ou du score). */
function scoreClass(priority: string | null): string {
  switch ((priority ?? '').toUpperCase()) {
    case 'VERY_HIGH':
      return 'very-high'
    case 'HIGH':
      return 'high'
    case 'MEDIUM':
      return 'medium'
    case 'LOW':
      return 'low'
    default:
      return 'unknown'
  }
}

function priorityLabel(priority: string): string {
  switch (priority) {
    case 'VERY_HIGH':
      return 'Very high'
    case 'HIGH':
      return 'High'
    case 'MEDIUM':
      return 'Medium'
    default:
      return 'Low'
  }
}

function formatDateTime(timestamp: string | null): string {
  if (!timestamp) return '—'
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return timestamp
  return date.toLocaleString('en-GB', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatTime(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}
