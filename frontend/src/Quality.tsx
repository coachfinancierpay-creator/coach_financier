import { useCallback, useEffect, useState } from 'react'
import {
  AlertTriangle,
  ArrowLeft,
  BadgeCheck,
  Bot,
  Download,
  Gauge,
  RefreshCw,
  Star,
  Wand2,
} from 'lucide-react'

import {
  fetchQualityOverview,
  fetchQualityReport,
  generateQualityDemoData,
  qualitySatisfactionCsvUrl,
  runQualityBatch,
} from './api'
import type { QualityAggregates, QualityPeriod, QualityReport } from './types.quality'
import { isoToday } from './dates'

const PERIODS: { id: QualityPeriod; label: string }[] = [
  { id: 'today', label: 'Today' },
  { id: 'yesterday', label: 'Yesterday' },
  { id: '7d', label: '7 days' },
  { id: '30d', label: '30 days' },
  { id: 'custom', label: 'Custom' },
]

function formatPercent(value: number | null | undefined, decimals = 0): string {
  return value === null || value === undefined ? '—' : `${(value * 100).toFixed(decimals)} %`
}

function formatNumber(value: number | null | undefined, decimals = 2): string {
  return value === null || value === undefined ? '—' : value.toFixed(decimals)
}

function evolutionLabel(value: number | null | undefined): string {
  if (value === null || value === undefined) return 'n/a'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)} %`
}

function evolutionClass(value: number | null | undefined, inverse = false): string {
  if (value === null || value === undefined) return 'mkt-evo flat'
  if (value === 0) return 'mkt-evo flat'
  const positive = inverse ? value < 0 : value > 0
  return positive ? 'mkt-evo up' : 'mkt-evo down'
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
 * Page <b>Qualité &amp; Satisfaction du Coach IA</b> (`#/quality`).
 * <p>
 * Deux familles d'indicateurs STRICTEMENT séparées : satisfaction client (avis) et conformité du
 * Coach (contrôles automatiques), puis leur croisement. La page n'affiche jamais un indicateur non
 * calculable : un contrôle non implémenté est Listé à part, jamais compté comme un « 0 ».
 */
export default function Quality() {
  const [period, setPeriod] = useState<QualityPeriod>('30d')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [ratingFilter, setRatingFilter] = useState<number | ''>('')
  const [severityFilter, setSeverityFilter] = useState('')
  /**
   * Journée analysée par le rapport IA : choisie EXPLICITEMENT (elle ne suit pas la fin de période
   * affichée), pour que l'analyse produite et la date affichée soient toujours la même journée.
   */
  const [reportDate, setReportDate] = useState(isoToday())
  const [data, setData] = useState<QualityAggregates | null>(null)
  const [report, setReport] = useState<QualityReport | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const overview = await fetchQualityOverview(period, from || undefined, to || undefined, {
        rating: ratingFilter === '' ? undefined : Number(ratingFilter),
        severity: severityFilter || undefined,
      })
      setData(overview)
      setReport(await fetchQualityReport(reportDate))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }, [period, from, to, ratingFilter, severityFilter, reportDate])

  useEffect(() => {
    void load()
  }, [load])

  async function regenerate() {
    if (!reportDate) return
    setNotice(null)
    try {
      await runQualityBatch(reportDate)
      setNotice(`Aggregates and AI report regenerated for ${reportDate}.`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error')
    }
  }

  async function seedDemo() {
    setNotice(null)
    try {
      const result = await generateQualityDemoData(14, 5)
      setNotice(
        `Demo data generated (${result.days} days, ${result.feedbackWritten} reviews, ${result.checksWritten} checks).`,
      )
      setPeriod('30d')
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error')
    }
  }

  const satisfaction = data?.satisfaction
  const conformity = data?.conformity
  const matrix = data?.satisfactionVsCompliance
  const hasFeedback = (satisfaction?.feedbackCount ?? 0) > 0
  const hasChecks = (conformity?.checksRun ?? 0) > 0

  return (
    <div className="logs-shell marketing-page">
      <header className="logs-header">
          <a className="logs-back" href="#/">
            <ArrowLeft size={16} /> Back to chat
          </a>
          <h1>
            <BadgeCheck size={20} /> AI Coach Quality &amp; Satisfaction
          </h1>
        </header>
        <p className="mkt-subtitle">
          Tracking of the customer experience <strong>and</strong> of compliance with the Coach rules. The two
          dimensions stay separate: a bad rating is never treated as a Coach error.
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
            aria-label="Filter by rating"
            value={ratingFilter}
            onChange={(event) => setRatingFilter(event.target.value === '' ? '' : Number(event.target.value))}
          >
            <option value="">All ratings</option>
            {[5, 4, 3, 2, 1].map((value) => (
              <option key={value} value={value}>
                {value} star{value > 1 ? 's' : ''}
              </option>
            ))}
          </select>
          <select
            aria-label="Filter by severity"
            value={severityFilter}
            onChange={(event) => setSeverityFilter(event.target.value)}
          >
            <option value="">All severities</option>
            <option value="HIGH">HIGH</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="LOW">LOW</option>
          </select>
          <button type="button" className="mkt-action" onClick={() => void load()} disabled={loading}>
            <RefreshCw size={14} /> {loading ? 'Loading…' : 'Refresh'}
          </button>
          <label className="mkt-report-date" title="Day analysed by the AI report (this day's aggregates are recalculated)">
            <span>Report of</span>
            <input
              type="date"
              aria-label="AI report date"
              value={reportDate}
              onChange={(event) => setReportDate(event.target.value)}
            />
          </label>
          <button
            type="button"
            className="mkt-action"
            onClick={() => void regenerate()}
            disabled={loading || !reportDate}
          >
            <Bot size={14} /> Regenerate AI report
          </button>
          <button type="button" className="mkt-action" onClick={() => void seedDemo()}>
            <Wand2 size={14} /> Demo data
          </button>
          <a className="mkt-action" href={qualitySatisfactionCsvUrl(period, from || undefined, to || undefined)}>
            <Download size={14} /> Export CSV
          </a>
        </div>

        {notice && <div className="mkt-notice">{notice}</div>}
        {error && <div className="mkt-notice warn">Error: {error}</div>}
        {data?.demo && (
          <div className="mkt-demo-banner">
            DEMONSTRATION data (source=DEMO) is present for this period: it does
            not represent real customer reviews.
          </div>
        )}
        {data && data.invalidLines > 0 && (
          <div className="mkt-notice warn">
            {data.invalidLines} unreadable line(s) ignored in the quality files.
          </div>
        )}

        {data && !hasFeedback && !hasChecks && (
          <div className="mkt-card">
            <h2>No data for the period</h2>
            <p className="mkt-empty">
              No review or check recorded between {data.dateFrom} and {data.dateTo}. No
              statistic is estimated: start a conversation then use "End the conversation", or
              generate demo data.
            </p>
          </div>
        )}

        {data && (hasFeedback || hasChecks) && (
          <>
            <div className="mkt-kpis">
              <Kpi label="Average rating" value={formatNumber(satisfaction?.averageRating, 2)} suffix="/ 5" />
              <Kpi label="Participation rate" value={formatPercent(satisfaction?.participationRate)} />
              <Kpi label="Positive reviews (4-5)" value={formatPercent(satisfaction?.positiveRate)} />
              <Kpi label="Negative reviews (1-2)" value={formatPercent(satisfaction?.negativeRate)} />
              <Kpi label="Coach issues" value={String(conformity?.anomalies ?? 0)} />
              <Kpi label="including HIGH issues" value={String(conformity?.highAnomalies ?? 0)} />
              <Kpi
                label="Rating change"
                value={evolutionLabel(satisfaction?.averageRatingEvolutionPercent)}
              />
              <Kpi label="Checks run" value={String(conformity?.checksRun ?? 0)} />
            </div>

            <p className="mkt-hint">
              {satisfaction?.feedbackCount ?? 0} reviews out of {satisfaction?.conversationsClosed ?? 0}{' '}
              completed conversation(s) —{' '}
              {satisfaction?.sufficientSample
                ? 'sufficient sample to interpret trends.'
                : 'still limited sample: preliminary observations (the AI report flags it).'}
            </p>

            <div className="mkt-grid">
              <section className="mkt-card">
                <h2>
                  <Star size={15} /> Rating distribution
                </h2>
                {data.ratingDistribution.every((bucket) => bucket.count === 0) ? (
                  <p className="mkt-empty">No reviews for the period.</p>
                ) : (
                  data.ratingDistribution.map((bucket) => (
                    <div key={bucket.rating} className="mkt-row column">
                      <div className="mkt-ranking-row">
                        <span className="mkt-ranking-name">
                          {'★'.repeat(bucket.rating)}
                          <span className="mkt-muted">{'★'.repeat(5 - bucket.rating)}</span>
                        </span>
                        <span className="mkt-ranking-value">{formatPercent(bucket.share)}</span>
                      </div>
                      <div className="mkt-ranking-bar" style={{ width: `${Math.max(bucket.share * 100, 1)}%` }} />
                      <span className="mkt-muted">{bucket.count} reviews</span>
                    </div>
                  ))
                )}
              </section>

              <section className="mkt-card">
                <h2>Main reasons for dissatisfaction</h2>
                {data.feedbackCategories.length === 0 ? (
                  <p className="mkt-empty">
                    No reason selected by customers for the period (reasons are only offered
                    from 3 stars or fewer).
                  </p>
                ) : (
                  data.feedbackCategories.slice(0, 6).map((reason) => (
                    <div key={reason.reason} className="mkt-row column">
                      <div className="mkt-ranking-row">
                        <span className="mkt-ranking-name">{reason.label}</span>
                        <span className="mkt-ranking-value">{reason.count}</span>
                      </div>
                      <div className="mkt-ranking-bar" style={{ width: `${Math.max(reason.share * 100, 1)}%` }} />
                      <span className={evolutionClass(reason.evolutionPercent, true)}>
                        <AlertTriangle size={12} /> {evolutionLabel(reason.evolutionPercent)} vs previous
                        period
                      </span>
                    </div>
                  ))
                )}
              </section>

              <section className="mkt-card">
                <h2>Themes detected in comments</h2>
                {data.commentThemes.length === 0 ? (
                  <p className="mkt-empty">No usable comment for the period.</p>
                ) : (
                  data.commentThemes.slice(0, 6).map((theme) => (
                    <div key={theme.theme} className="mkt-row column">
                      <div className="mkt-ranking-row">
                        <span className="mkt-ranking-name">{theme.label}</span>
                        <span className="mkt-ranking-value">{theme.count}</span>
                      </div>
                      <div className="mkt-ranking-bar" style={{ width: `${Math.max(theme.share * 100, 1)}%` }} />
                    </div>
                  ))
                )}
                <p className="mkt-hint">
                  Themes produced by a deterministic local heuristic on anonymised comments
                  (AI analysis of comments is not enabled in this version).
                </p>
              </section>

              <section className="mkt-card">
                <h2>
                  <Gauge size={15} /> Coach checks
                </h2>
                <div className="mkt-table-scroll dashboard-table-scroll quality-checks-table">
                  <table className="mkt-table compact">
                    <thead>
                      <tr>
                        <th>Check</th>
                        <th>Severity</th>
                        <th>Run</th>
                        <th>Issues</th>
                        <th>Change</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.qualityChecks.map((check) => (
                        <tr key={check.checkType}>
                          <td className="mkt-strong">{check.label}</td>
                          <td>{check.severity}</td>
                          <td>{check.checksRun}</td>
                          <td className={check.detected > 0 ? 'mkt-low' : ''}>{check.detected}</td>
                          <td>
                            <span className={evolutionClass(check.evolutionPercent, true)}>
                              {evolutionLabel(check.evolutionPercent)}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {data.notImplementedChecks.length > 0 && (
                  <p className="mkt-hint">
                    Checks described but NOT implemented in this version (so never displayed as
                    zero): {data.notImplementedChecks.map((type) => type).join(', ')}.
                  </p>
                )}
              </section>
            </div>

            <section className="mkt-card wide">
              <h2>Customer satisfaction vs Coach compliance</h2>
              <div className="qlt-matrix">
                <div className="qlt-cell ok">
                  <span className="mkt-muted">Satisfied customer + compliant Coach</span>
                  <strong>{matrix?.satisfiedCompliant ?? 0}</strong>
                  <span className="mkt-muted">Expected behaviour</span>
                </div>
                <div className="qlt-cell watch">
                  <span className="mkt-muted">Satisfied customer + issue</span>
                  <strong>{matrix?.satisfiedAnomaly ?? 0}</strong>
                  <span className="mkt-muted">
                    Problem invisible to the customer, to fix anyway
                  </span>
                </div>
                <div className="qlt-cell friction">
                  <span className="mkt-muted">Unsatisfied customer + compliant Coach</span>
                  <strong>{matrix?.unsatisfiedCompliant ?? 0}</strong>
                  <span className="mkt-muted">
                    Rule correctly applied: work on education, never remove the safeguard
                  </span>
                </div>
                <div className="qlt-cell critical">
                  <span className="mkt-muted">Unsatisfied customer + issue</span>
                  <strong>{matrix?.unsatisfiedAnomaly ?? 0}</strong>
                  <span className="mkt-muted">Priority case: experience AND compliance at stake</span>
                </div>
              </div>
              <p className="mkt-hint">
                Neutral ratings (3 stars) are excluded from the cross-tab. {(matrix?.unratedWithAnomaly ?? 0)}{' '}
                session(s) have an issue with no customer review.
              </p>
            </section>

            <section className="mkt-card wide">
              <h2>
                <Bot size={15} /> AI quality analysis
                {report?.reportDate ? ` — ${report.reportDate}` : ''}
              </h2>
              <p className="mkt-hint">
                Analysis generated from the statistics computed by the backend and from anonymised
                feedback. The AI has computed no figure and changes nothing automatically:
                it suggests, the team decides.
              </p>
              {report && reportDate && report.reportDate !== reportDate && (
                <div className="mkt-notice warn">
                  No analysis exists for {reportDate}: here is the most recent
                  {' '}({report.reportDate}). Use "Regenerate AI report" for the selected day.
                </div>
              )}
              {!report ? (
                <p className="mkt-empty">
                  No AI report available for {reportDate}. Use "Regenerate AI report".
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
                      <h3>Satisfaction</h3>
                      <p className="mkt-empty">{report.satisfactionAnalysis?.summary || '—'}</p>
                      {report.satisfactionAnalysis?.positivePoints?.length ? (
                        <ul className="mkt-report-list">
                          {report.satisfactionAnalysis.positivePoints.map((item) => (
                            <li key={item}>{item}</li>
                          ))}
                        </ul>
                      ) : null}
                      {report.satisfactionAnalysis?.mainIrritants?.length ? (
                        <>
                          <h3>Irritants</h3>
                          <ul className="mkt-report-list">
                            {report.satisfactionAnalysis.mainIrritants.map((item) => (
                              <li key={item}>{item}</li>
                            ))}
                          </ul>
                        </>
                      ) : null}
                    </div>
                    <div>
                      <h3>Quality / compliance</h3>
                      <p className="mkt-empty">{report.qualityAndCompliance?.summary || '—'}</p>
                      {report.qualityAndCompliance?.mainIssues?.length ? (
                        <ul className="mkt-report-list">
                          {report.qualityAndCompliance.mainIssues.map((item) => (
                            <li key={item}>{item}</li>
                          ))}
                        </ul>
                      ) : null}
                      {report.qualityAndCompliance?.criticalIssues?.length ? (
                        <>
                          <h3>Critical issues</h3>
                          <ul className="mkt-report-list">
                            {report.qualityAndCompliance.criticalIssues.map((item) => (
                              <li key={item}>{item}</li>
                            ))}
                          </ul>
                        </>
                      ) : null}
                    </div>
                    <div>
                      <h3>Satisfaction × compliance</h3>
                      <p className="mkt-empty">{report.satisfactionVsCompliance?.summary || '—'}</p>
                      {report.satisfactionVsCompliance?.notableCases?.length ? (
                        <ul className="mkt-report-list">
                          {report.satisfactionVsCompliance.notableCases.map((item) => (
                            <li key={item}>{item}</li>
                          ))}
                        </ul>
                      ) : null}
                    </div>
                    <div>
                      <h3>Compliant but frustrating rules</h3>
                      {report.ruleFriction.length === 0 ? (
                        <p className="mkt-empty">No compliant rule identified as frustrating.</p>
                      ) : (
                        <ul className="mkt-report-list">
                          {report.ruleFriction.map((item) => (
                            <li key={`${item.rule}-${item.observation}`}>
                              <strong>{item.rule}</strong> — {item.observation}
                              {item.recommendation ? ` → ${item.recommendation}` : ''}
                              {item.coachCompliant === true ? ' (Coach compliant)' : ''}
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  </div>

                  {report.priorityImprovements.length > 0 && (
                    <>
                      <h3>Priority improvements</h3>
                      <table className="mkt-table compact">
                        <thead>
                          <tr>
                            <th>Priority</th>
                            <th>Title</th>
                            <th>Observation</th>
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
                      <h3>Identified trends</h3>
                      <ul className="mkt-report-list">
                        {report.trends.map((trend) => (
                          <li key={`${trend.type}-${trend.topic}`}>
                            <strong>{trend.type}</strong> — {trend.topic}: {trend.observation}
                          </li>
                        ))}
                      </ul>
                    </>
                  )}

                  {report.alerts.length > 0 && (
                    <div className="mkt-alerts">
                      {report.alerts.map((alert) => (
                        <div
                          key={`${alert.level}-${alert.title}`}
                          className={
                            alert.level === 'IMPORTANT'
                              ? 'mkt-alert important'
                              : alert.level === 'WATCH'
                                ? 'mkt-alert watch'
                                : 'mkt-alert'
                          }
                        >
                          <div className="mkt-strong">{alert.title}</div>
                          <div className="mkt-muted">{alert.description}</div>
                        </div>
                      ))}
                    </div>
                  )}

                  {report.finalAssessment && <p className="mkt-summary">{report.finalAssessment}</p>}
                  <p className="mkt-hint">
                    Report of {report.reportDate} · model {report.model ?? '—'} ·{' '}
                    {report.aiGenerated ? 'AI generated' : 'AI analysis unavailable'}
                  </p>
                </>
              )}
            </section>
          </>
        )}
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
