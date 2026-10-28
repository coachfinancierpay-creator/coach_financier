import { useCallback, useEffect, useState } from 'react'
import {
  ArrowLeft,
  BadgeCheck,
  Bot,
  Download,
  MessageSquareQuote,
  RefreshCw,
  ThumbsDown,
  ThumbsUp,
  UserCheck,
  Wand2,
} from 'lucide-react'

import {
  ADVISOR_AREAS,
  ADVISOR_EMAIL_ASSESSMENTS,
  ADVISOR_INTEREST_LEVELS,
  ADVISOR_PRODUCT_REASONS,
  ADVISOR_REASONS,
  advisorSummaryCsvUrl,
  fetchAdvisorCandidates,
  fetchAdvisorOverview,
  fetchAdvisorReport,
  generateAdvisorDemoData,
  generateAdvisorReport,
  submitAdvisorFeedback,
  type AdvisorFilters,
} from './api'
import type {
  AdvisorAggregates,
  AdvisorAssessment,
  AdvisorCandidateSession,
  AdvisorCatalogueProduct,
  AdvisorIssueInput,
  AdvisorPeriod,
  AdvisorProductFeedbackInput,
  AdvisorReport,
} from './types.advisor'
import { isoToday } from './dates'

const PERIODS: { id: AdvisorPeriod; label: string }[] = [
  { id: 'today', label: 'Today' },
  { id: 'yesterday', label: 'Yesterday' },
  { id: '7d', label: '7 days' },
  { id: '30d', label: '30 days' },
  { id: 'custom', label: 'Custom' },
]

const ASSESSMENTS: { code: AdvisorAssessment; label: string; icon: typeof ThumbsUp }[] = [
  { code: 'RELEVANT', label: 'Relevant', icon: ThumbsUp },
  { code: 'NEEDS_IMPROVEMENT', label: 'Needs improvement', icon: MessageSquareQuote },
  { code: 'INCORRECT', label: 'Incorrect', icon: ThumbsDown },
]

function percent(value: number | null | undefined, decimals = 0): string {
  return value === null || value === undefined ? '—' : `${(value * 100).toFixed(decimals)} %`
}

function evolution(value: number | null | undefined): string {
  if (value === null || value === undefined) return 'n/a'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)} %`
}

function evolutionClass(value: number | null | undefined): string {
  if (value === null || value === undefined || value === 0) return 'mkt-evo flat'
  return value > 0 ? 'mkt-evo up' : 'mkt-evo down'
}

function statusClass(status: string | undefined): string {
  switch ((status ?? '').toUpperCase()) {
    case 'GOOD':
      return 'mkt-importance low'
    case 'WATCH':
      return 'mkt-importance medium'
    case 'ATTENTION':
      return 'mkt-importance high'
    default:
      return 'mkt-importance'
  }
}

/**
 * Page <b>Feedback Conseillers</b> (`#/advisor-feedback`) : mesure la pertinence du travail du Coach
 * à partir de l'expertise des conseillers (saisie rapide) et restitue les KPI, les corrections
 * récurrentes, la pertinence produit, la qualité des emails et l'analyse IA de la période.
 */
export default function AdvisorFeedback() {
  const [period, setPeriod] = useState<AdvisorPeriod>('30d')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [filters, setFilters] = useState<AdvisorFilters>({})
  /**
   * Journée analysée par le rapport IA : choisie EXPLICITEMENT (elle ne suit pas la fin de période
   * affichée). L'analyse IA est générée pour CETTE journée : la date affichée est donc toujours celle
   * du rapport, jamais celle de la période sélectionnée.
   */
  const [reportDate, setReportDate] = useState(isoToday())
  const [data, setData] = useState<AdvisorAggregates | null>(null)
  const [report, setReport] = useState<AdvisorReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const overview = await fetchAdvisorOverview(period, from || undefined, to || undefined, filters)
      setData(overview)
      setReport(await fetchAdvisorReport(reportDate))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }, [period, from, to, filters, reportDate])

  useEffect(() => {
    void load()
  }, [load])

  /**
   * Génère le rapport IA de la JOURNÉE choisie : période « custom » d'un seul jour (from = to), afin que
   * le rapport produit porte exactement la date demandée.
   */
  async function generate() {
    if (!reportDate) return
    setNotice(null)
    try {
      setReport(await generateAdvisorReport('custom', reportDate, reportDate))
      setNotice(`AI analysis of advisor feedback generated for ${reportDate}.`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error')
    }
  }

  async function seedDemo() {
    setNotice(null)
    try {
      const result = await generateAdvisorDemoData(14, 4)
      setNotice(`Demo data generated (${result.days} days, ${result.written} feedback entries).`)
      setPeriod('30d')
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error')
    }
  }

  const kpis = data?.kpis
  const hasFeedback = (kpis?.feedbackCount ?? 0) > 0

  return (
    <div className="logs-shell marketing-page">
      <header className="logs-header">
        <a className="logs-back" href="#/">
          <ArrowLeft size={16} /> Back to chat
        </a>
        <h1>
          <UserCheck size={20} /> Advisor Feedback
        </h1>
      </header>
      <p className="mkt-subtitle">
        Measure the Coach&rsquo;s relevance from the advisors&rsquo; expertise. The advisor gives feedback, the
        system measures, the AI analyses trends: <strong>no change to the Coach is applied
        automatically</strong>.
      </p>

      <div className="mkt-toolbar">
        <div className="mkt-periods">
          {PERIODS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={period === item.id ? 'mkt-period active' : 'mkt-period'}
              onClick={() => setPeriod(item.id)}
            >
              {item.label}
            </button>
          ))}
        </div>
        {period === 'custom' && (
          <div className="mkt-dates">
            <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
            <span>→</span>
            <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </div>
        )}
        <select
          aria-label="Filter by assessment"
          value={filters.assessment ?? ''}
          onChange={(event) => setFilters({ ...filters, assessment: event.target.value || undefined })}
        >
          <option value="">All assessments</option>
          <option value="RELEVANT">Relevant</option>
          <option value="NEEDS_IMPROVEMENT">Needs improvement</option>
          <option value="INCORRECT">Incorrect</option>
        </select>
        <select
          aria-label="Filter by email usability"
          value={filters.emailAssessment ?? ''}
          onChange={(event) => setFilters({ ...filters, emailAssessment: event.target.value || undefined })}
        >
          <option value="">All emails</option>
          {ADVISOR_EMAIL_ASSESSMENTS.map((item) => (
            <option key={item.code} value={item.code}>
              {item.label}
            </option>
          ))}
        </select>
        <button type="button" className="mkt-action" onClick={() => void load()} disabled={loading}>
          <RefreshCw size={14} /> {loading ? 'Loading…' : 'Refresh'}
        </button>
        <label className="mkt-report-date" title="Day analysed by the AI analysis (the report will carry this date)">
          <span>Report of</span>
          <input
            type="date"
            aria-label="AI report date"
            value={reportDate}
            onChange={(event) => setReportDate(event.target.value)}
          />
        </label>
        <button type="button" className="mkt-action" onClick={() => void generate()} disabled={loading || !reportDate}>
          <Bot size={14} /> Generate the AI analysis
        </button>
        <button type="button" className="mkt-action" onClick={() => void seedDemo()}>
          <Wand2 size={14} /> Demo data
        </button>
        <a className="mkt-action" href={advisorSummaryCsvUrl(period, from || undefined, to || undefined)}>
          <Download size={14} /> CSV export
        </a>
      </div>

      {notice && <div className="mkt-notice">{notice}</div>}
      {error && <div className="mkt-notice warn">Error: {error}</div>}
      {data?.demo && (
        <div className="mkt-demo-banner">
          DEMONSTRATION feedback (source=DEMO) is present: it does not represent real advisor
          feedback.
        </div>
      )}
      {data && data.invalidLines > 0 && (
        <div className="mkt-notice warn">
          {data.invalidLines} unreadable line(s) ignored in the feedback files.
        </div>
      )}

      {data && !hasFeedback && (
        <div className="mkt-card">
          <h2>No advisor feedback for the period</h2>
          <p className="mkt-empty">
            No case file assessed between {data.dateFrom} and {data.dateTo}. No statistic is
            estimated: use the “Evaluate a case file” block below or generate demo data.
          </p>
        </div>
      )}

      {data && hasFeedback && (
        <>
          <div className="mkt-kpis">
            <Kpi label="Case files assessed" value={String(kpis?.sessionsEvaluated ?? 0)} />
            <Kpi label="Relevant analyses" value={percent(kpis?.relevantRate)} />
            <Kpi label="Needs improvement" value={percent(kpis?.needsImprovementRate)} />
            <Kpi label="Incorrect" value={percent(kpis?.incorrectRate)} />
            <Kpi label="Products judged relevant" value={percent(kpis?.productRelevanceRate)} />
            <Kpi label="Emails ready or minor" value={percent(kpis?.emailReadyOrMinorRate)} />
            <Kpi label="Interest corrections" value={String(kpis?.interestCorrections ?? 0)} />
            <Kpi label="“Relevant” trend" value={evolution(kpis?.relevantRateEvolutionPercent)} />
          </div>

          <p className="mkt-hint">
            {kpis?.feedbackCount} feedback on {kpis?.sessionsEvaluated} case file(s)
            {kpis?.participationRate === null || kpis?.participationRate === undefined
              ? ' — feedback rate not computable (no conversation closed in the period).'
              : ` — feedback rate ${percent(kpis.participationRate)} of closed conversations.`}{' '}
            {kpis?.sufficientSample
              ? 'Sufficient sample to interpret.'
              : 'Sample still limited: preliminary conclusions.'}
          </p>

          <div className="mkt-grid">
            <section className="mkt-card">
              <h2>Overall assessments</h2>
              {data.assessments.map((item) => (
                <div key={item.code} className="mkt-row column">
                  <div className="mkt-ranking-row">
                    <span className="mkt-ranking-name">{item.label}</span>
                    <span className="mkt-ranking-value">{item.count}</span>
                  </div>
                  <div className="mkt-ranking-bar" style={{ width: `${Math.max(item.share * 100, 1)}%` }} />
                  <span className={evolutionClass(item.evolutionPercent)}>
                    {evolution(item.evolutionPercent)} vs previous period
                  </span>
                </div>
              ))}
            </section>

            <section className="mkt-card">
              <h2>Needs improvement — most corrected areas</h2>
              {data.areas.length === 0 ? (
                <p className="mkt-empty">No correction reported for the period.</p>
              ) : (
                data.areas.map((area) => (
                  <div key={area.area} className="mkt-row column">
                    <div className="mkt-ranking-row">
                      <span className="mkt-ranking-name">{area.label}</span>
                      <span className="mkt-ranking-value">{area.count}</span>
                    </div>
                    <div className="mkt-ranking-bar" style={{ width: `${Math.max(area.share * 100, 1)}%` }} />
                    <span className={evolutionClass(area.evolutionPercent)}>
                      {evolution(area.evolutionPercent)} — share {percent(area.share)}
                    </span>
                  </div>
                ))
              )}
              {data.reasons.length > 0 && (
                <>
                  <h3>Structured reasons</h3>
                  <ul className="mkt-report-list">
                    {data.reasons.slice(0, 6).map((reason) => (
                      <li key={reason.reason}>
                        {reason.label}: {reason.count} ({evolution(reason.evolutionPercent)})
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </section>

            <section className="mkt-card">
              <h2>Product relevance</h2>
              {data.products.length === 0 ? (
                <p className="mkt-empty">No product assessed for the period.</p>
              ) : (
                <div className="mkt-table-scroll dashboard-table-scroll product-relevance-table">
                  <table className="mkt-table compact">
                    <thead>
                      <tr>
                        <th>Product</th>
                        <th>Assess.</th>
                        <th>Relevant</th>
                        <th>Not relevant</th>
                        <th>Rate</th>
                        <th>Corr.</th>
                        <th>Added</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.products.slice(0, 12).map((product) => (
                        <tr key={product.productId}>
                          <td className="mkt-strong">{product.productName ?? product.productId}</td>
                          <td>{product.assessments}</td>
                          <td>{product.relevant}</td>
                          <td className={product.notRelevant > 0 ? 'mkt-low' : ''}>{product.notRelevant}</td>
                          <td>{percent(product.relevanceRate)}</td>
                          <td>{product.interestCorrections}</td>
                          <td>{product.addedByAdvisor}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className="mkt-card">
              <h2>Corrected interest levels</h2>
              {data.interestCorrections.length === 0 ? (
                <p className="mkt-empty">
                  No interest level correction: the AI value and the advisor value are kept
                  separately when there is one.
                </p>
              ) : (
                <div className="mkt-table-scroll dashboard-table-scroll corrected-interest-table">
                  <table className="mkt-table compact">
                    <thead>
                      <tr>
                        <th>Product</th>
                        <th>AI</th>
                        <th>Advisor</th>
                        <th>Cases</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.interestCorrections.slice(0, 10).map((correction) => (
                        <tr key={`${correction.productId}-${correction.fromLevel}-${correction.toLevel}`}>
                          <td className="mkt-strong">{correction.productName ?? correction.productId}</td>
                          <td>{correction.fromLevel}</td>
                          <td>{correction.toLevel}</td>
                          <td>{correction.count}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <h3>Quality of prepared emails</h3>
              {data.emailQuality.length === 0 ? (
                <p className="mkt-empty">No email assessed for the period.</p>
              ) : (
                data.emailQuality.map((email) => (
                  <div key={email.code} className="mkt-row column">
                    <div className="mkt-ranking-row">
                      <span className="mkt-ranking-name">{email.label}</span>
                      <span className="mkt-ranking-value">{email.count}</span>
                    </div>
                    <div className="mkt-ranking-bar" style={{ width: `${Math.max(email.share * 100, 1)}%` }} />
                    <span className={evolutionClass(email.evolutionPercent)}>
                      {percent(email.share)} — {evolution(email.evolutionPercent)}
                    </span>
                  </div>
                ))
              )}
            </section>
          </div>
        </>
      )}

      <CaptureForm
        candidatesPeriodDays={period === '30d' ? 30 : period === 'custom' ? 30 : 7}
        onSubmitted={(message) => {
          setNotice(message)
          void load()
        }}
      />

      <section className="mkt-card wide">
        <h2>
          <Bot size={15} /> AI analysis of advisor feedback
          {report?.reportDate ? ` — ${report.reportDate}` : ''}
        </h2>
        <p className="mkt-hint">
          Analysis generated from the KPIs computed by the backend and anonymised comments. The AI has
          not computed any figure and changes nothing: it proposes, the team decides.
        </p>
        {report && reportDate && report.reportDate !== reportDate && (
          <div className="mkt-notice warn">
            No analysis exists for {reportDate}: here is the most recent one
            {' '}({report.reportDate}). Use “Generate the AI analysis” for the chosen day.
          </div>
        )}
        {!report ? (
          <p className="mkt-empty">
            No analysis available for {reportDate}: use “Generate the AI analysis”
            (generation is manual in the POC).
          </p>
        ) : (
          <>
            {report.executiveSummary && (
              <p className="mkt-summary">
                <span className={statusClass(report.executiveSummary.status)}>
                  {report.executiveSummary.status}
                </span>{' '}
                {report.executiveSummary.summary}
              </p>
            )}
            {report.error && <div className="mkt-notice warn">AI analysis unavailable: {report.error}</div>}
            <div className="mkt-report-cols">
              <div>
                <h3>What works</h3>
                {report.strengths.length === 0 ? (
                  <p className="mkt-empty">No strength identified in the provided data.</p>
                ) : (
                  <ul className="mkt-report-list">
                    {report.strengths.map((item) => (
                      <li key={item.title}>
                        <strong>{item.title}</strong> — {item.observation}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div>
                <h3>Main issues</h3>
                {report.mainIssues.length === 0 ? (
                  <p className="mkt-empty">No recurring issue identified.</p>
                ) : (
                  <ul className="mkt-report-list">
                    {report.mainIssues.map((item) => (
                      <li key={`${item.area}-${item.observation}`}>
                        <strong>{item.severity}</strong> — {item.observation}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div>
                <h3>Interest levels</h3>
                <p className="mkt-empty">{report.interestLevelAnalysis?.summary || '—'}</p>
                {report.interestLevelAnalysis?.overestimationSignals?.length ? (
                  <ul className="mkt-report-list">
                    {report.interestLevelAnalysis.overestimationSignals.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                ) : null}
              </div>
              <div>
                <h3>Customer email &amp; follow-up</h3>
                <p className="mkt-empty">{report.clientEmailAnalysis?.summary || '—'}</p>
                <p className="mkt-empty">{report.nextActionAnalysis?.summary || '—'}</p>
              </div>
            </div>

            {report.productAnalysis.length > 0 && (
              <>
                <h3>Analysis by product</h3>
                <table className="mkt-table compact">
                  <thead>
                    <tr>
                      <th>Product</th>
                      <th>Signal</th>
                      <th>Observation</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.productAnalysis.map((item) => (
                      <tr key={item.productId}>
                        <td className="mkt-strong">{item.productName || item.productId}</td>
                        <td>{item.signal}</td>
                        <td>{item.observation}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}

            {report.priorityImprovements.length > 0 && (
              <>
                <h3>Improvement priorities</h3>
                <table className="mkt-table compact">
                  <thead>
                    <tr>
                      <th>Priority</th>
                      <th>Title</th>
                      <th>Finding</th>
                      <th>Recommendation</th>
                      <th>Expected benefit</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.priorityImprovements.map((item) => (
                      <tr key={`${item.priority}-${item.title}`}>
                        <td>
                          <span
                            className={
                              item.priority === 'HIGH'
                                ? 'mkt-importance high'
                                : item.priority === 'LOW'
                                  ? 'mkt-importance low'
                                  : 'mkt-importance medium'
                            }
                          >
                            {item.priority}
                          </span>
                        </td>
                        <td className="mkt-strong">{item.title}</td>
                        <td>{item.observation}</td>
                        <td>{item.recommendation}</td>
                        <td>{item.expectedBenefit}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}

            {report.trends.length > 0 && (
              <>
                <h3>Trends</h3>
                <ul className="mkt-report-list">
                  {report.trends.map((trend) => (
                    <li key={`${trend.type}-${trend.topic}`}>
                      <strong>{trend.type}</strong> — {trend.topic}: {trend.observation}
                    </li>
                  ))}
                </ul>
              </>
            )}

            {report.watchPoints.length > 0 && (
              <>
                <h3>Watch points</h3>
                <ul className="mkt-report-list">
                  {report.watchPoints.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </>
            )}

            {report.finalAssessment && <p className="mkt-summary">{report.finalAssessment}</p>}
            <p className="mkt-hint">
              Report of {report.reportDate} · model {report.model ?? '—'} ·{' '}
              {report.aiGenerated ? 'AI generated' : 'AI analysis unavailable'} — the module never modifies
              the Coach automatically (prompt, rules, thresholds, catalogue, code).
            </p>
          </>
        )}
      </section>
    </div>
  )
}

function Kpi({ label, value, suffix }: { label: string; value: string; suffix?: string }) {
  return (
    <div className="mkt-kpi">
      <span className="mkt-kpi-label">{label}</span>
      <span className="mkt-kpi-value">
        {value}
        {suffix ? <span className="mkt-muted"> {suffix}</span> : null}
      </span>
    </div>
  )
}

/**
 * SAISIE RAPIDE du feedback conseiller (§3/§4/§5/§7/§8/§9/§10/§11) : l'évaluation globale suffit,
 * tout le reste est facultatif et n'apparaît que si nécessaire.
 */
function CaptureForm({ candidatesPeriodDays, onSubmitted }: {
  candidatesPeriodDays: number
  onSubmitted: (message: string) => void
}) {
  const [candidates, setCandidates] = useState<AdvisorCandidateSession[]>([])
  const [catalogue, setCatalogue] = useState<AdvisorCatalogueProduct[]>([])
  const [sessionId, setSessionId] = useState('')
  const [assessment, setAssessment] = useState<AdvisorAssessment | null>(null)
  const [comment, setComment] = useState('')
  const [areaReasons, setAreaReasons] = useState<Record<string, string>>({})
  const [products, setProducts] = useState<Record<string, AdvisorProductFeedbackInput>>({})
  const [missing, setMissing] = useState<string[]>([])
  const [nextAction, setNextAction] = useState('')
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (!open) return
    void fetchAdvisorCandidates(candidatesPeriodDays, true)
      .then((result) => {
        setCandidates(result.candidates)
        setCatalogue(result.catalogue)
      })
      .catch(() => setCandidates([]))
  }, [open, candidatesPeriodDays])

  const selected = candidates.find((item) => item.sessionId === sessionId) ?? null

  function reset() {
    setAssessment(null)
    setComment('')
    setAreaReasons({})
    setProducts({})
    setMissing([])
    setNextAction('')
    setEmail('')
  }

  async function submit() {
    if (!sessionId || !assessment) return
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
      setMessage(
        response.status === 'SAVED'
          ? `Feedback saved (version ${response.version})${response.event === 'UPDATED' ? ' — revision' : ''}.`
          : `Feedback not saved (${response.status})${response.message ? `: ${response.message}` : ''}`,
      )
      onSubmitted(response.status === 'SAVED'
        ? `Advisor feedback saved for ${sessionId}.`
        : `Advisor feedback: ${response.status}.`)
      reset()
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Unknown error')
    }
  }

  return (
    <section className="mkt-card wide">
      <h2>
        <BadgeCheck size={15} /> Evaluate a case file
      </h2>
      <p className="mkt-hint">
        A few seconds are enough: the overall assessment is the only required field. Negative feedback
        opens the details (areas, reasons, products, interests, follow-up, email).
      </p>
      {!open ? (
        <button type="button" className="mkt-action" onClick={() => setOpen(true)}>
          Open quick capture
        </button>
      ) : (
        <>
          <div className="qlt-popup-actions" style={{ justifyContent: 'flex-start' }}>
            <select
              aria-label="Case file to evaluate"
              value={sessionId}
              onChange={(event) => {
                setSessionId(event.target.value)
                setProducts({})
                setMissing([])
              }}
            >
              <option value="">— choose a case file —</option>
              {candidates.map((candidate) => (
                <option key={candidate.sessionId} value={candidate.sessionId}>
                  {candidate.sessionId}
                  {candidate.projectType ? ` · ${candidate.projectType}` : ''}
                  {candidate.evaluated ? ` · already evaluated (v${candidate.currentVersion})` : ''}
                </option>
              ))}
            </select>
            <button type="button" className="mkt-action" onClick={() => void submit()} disabled={!sessionId || !assessment}>
              Send feedback
            </button>
            <button type="button" className="mkt-action" onClick={() => setOpen(false)}>
              Close
            </button>
          </div>

          <h3>Does this analysis seem relevant to you&nbsp;?</h3>
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
                        onChange={(event) => {
                          const updated = { ...areaReasons }
                          if (event.target.checked) updated[area.code] = ''
                          else delete updated[area.code]
                          setAreaReasons(updated)
                        }}
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

          {selected && selected.products.length > 0 && (
            <>
              <h3>Detected products — relevance and interest level</h3>
              <table className="mkt-table compact">
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>AI interest</th>
                    <th>Assessment</th>
                    <th>Corrected interest</th>
                    <th>Reason</th>
                  </tr>
                </thead>
                <tbody>
                  {selected.products.map((product) => {
                    const entry = products[product.productId] ?? { productId: product.productId }
                    const update = (patch: Partial<AdvisorProductFeedbackInput>) =>
                      setProducts({
                        ...products,
                        [product.productId]: {
                          ...entry,
                          productId: product.productId,
                          productName: product.productName ?? undefined,
                          aiInterestLevel: product.aiInterestLevel ?? undefined,
                          ...patch,
                        },
                      })
                    return (
                      <tr key={product.productId}>
                        <td className="mkt-strong">{product.productName ?? product.productId}</td>
                        <td>{product.aiInterestLevel ?? '—'}</td>
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
            </>
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
                  {catalogue.find((product) => product.productId === id)?.productName ?? id}{' '}
                  <button type="button" className="mkt-action" onClick={() => setMissing(missing.filter((item) => item !== id))}>
                    remove
                  </button>
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

          <textarea
            className="qlt-comment"
            rows={3}
            maxLength={1000}
            placeholder="Comment (optional)"
            value={comment}
            onChange={(event) => setComment(event.target.value)}
          />
          {message && <p className="mkt-hint">{message}</p>}
          <p className="mkt-hint">
            No feedback modifies the Coach automatically: it feeds the KPIs and the AI analysis.
            Your name is not stored (technical identifier only).
          </p>
        </>
      )}
    </section>
  )
}
