import { useCallback, useEffect, useMemo, useState } from 'react'
import { ArrowLeft, Bot, Download, RefreshCw, TrendingDown, TrendingUp, Wand2 } from 'lucide-react'
import {
  generateMarketingDemoData,
  fetchMarketingOverview,
  fetchMarketingProduct,
  fetchMarketingReport,
  fetchMarketingStatus,
  marketingProductsCsvUrl,
  regenerateMarketingReport,
} from './api'
import type {
  MarketingAggregates,
  MarketingPeriod,
  MarketingProductDetail,
  MarketingReport,
  MarketingStatus,
} from './types'
import { isoDaysAgo, isoToday } from './dates'

type SortKey = 'interestedSessions' | 'recommendedSessions' | 'interestRate' | 'score' | 'evolutionPercent'

const PERIODS: { key: MarketingPeriod; label: string }[] = [
  { key: 'today', label: 'Today' },
  { key: 'yesterday', label: 'Yesterday' },
  { key: '7d', label: '7 days' },
  { key: '30d', label: '30 days' },
  { key: 'custom', label: 'Custom' },
]

function formatNumber(value: number | null | undefined): string {
  return value == null ? '—' : value.toLocaleString('en-GB')
}

function formatPercent(rate: number | null | undefined, digits = 1): string {
  return rate == null ? '—' : `${(rate * 100).toLocaleString('en-GB', { maximumFractionDigits: digits })}%`
}

function formatEvolution(value: number | null | undefined): string {
  if (value == null) return '—'
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toLocaleString('en-GB', { maximumFractionDigits: 1 })}%`
}

/**
 * Libellés métier : la page ne doit jamais afficher un code technique brut
 * comme « REAL_ESTATE · NO_SUITABLE_PRODUCT ».
 * La source est le backend (`GET /api/marketing/status` → `*Labels`) ; ce repli local ne sert
 * qu'avant le chargement du statut. Un code inconnu est « humanisé », jamais affiché tel quel.
 */
const FALLBACK_LABELS: Record<string, string> = {
  VEHICLE: 'Vehicle purchase',
  REAL_ESTATE: 'Real estate project',
  HOME_WORK: 'Renovation / home improvement',
  ELECTRONICS: 'Electronics purchase',
  FURNITURE: 'Furniture purchase',
  TRAVEL: 'Travel',
  EDUCATION: 'Studies / training',
  CASH_NEED: 'Cash flow need',
  SAVINGS: 'Savings / investment',
  BUDGET: 'Budget management',
  OTHER: 'Other',
  UNKNOWN: 'Not identified',
  MORTGAGE: 'Mortgage',
  PERSONAL_LOAN: 'Personal loan',
  AUTO_LOAN: 'Car loan',
  SAVINGS_PRODUCT: 'Savings product',
  INSURANCE_AUTO: 'Car insurance',
  INSURANCE_HOME: 'Home insurance',
  INSURANCE_BORROWER: 'Mortgage protection insurance',
  NO_SUITABLE_PRODUCT: 'No offer matching the need',
  PRICE: 'Price / cost too high',
  RATE: 'Rate considered too high',
  PREFERS_CASH: 'Prefers to pay cash',
  DOES_NOT_WANT_CREDIT: 'Does not want a loan',
  MISSING_COVERAGE_INFORMATION: 'Coverage not documented',
  MISSING_PRICING_INFORMATION: 'Pricing not documented',
  MISSING_CONDITIONS_INFORMATION: 'Conditions not documented',
}

/* Le code technique UNKNOWN ne doit jamais s'afficher tel quel dans la page. */
const UNKNOWN_CODES = new Set(['UNKNOWN', 'NONE', 'NULL', 'UNDEFINED', '-', 'N/A', 'NA'])

function isUnknown(code?: string | null): boolean {
  return !code || !code.trim() || UNKNOWN_CODES.has(code.trim().toUpperCase())
}

function humanizeCode(code: string): string {
  const text = code.replace(/_/g, ' ').toLowerCase()
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : code
}

function readableLabel(map: Record<string, string>, code?: string | null): string {
  if (!code || !code.trim()) return ''
  const key = code.trim().toUpperCase()
  return map[key] ?? FALLBACK_LABELS[key] ?? humanizeCode(key)
}

export default function Marketing() {
  const [period, setPeriod] = useState<MarketingPeriod>('30d')
  const [from, setFrom] = useState(isoDaysAgo(29))
  const [to, setTo] = useState(isoDaysAgo(0))
  /** Journée du rapport IA : choisie explicitement (elle ne dépend PAS de la période affichée). */
  const [reportDate, setReportDate] = useState(isoToday())
  const [projectType, setProjectType] = useState('')
  const [productFamily] = useState('')
  const [interestLevel, setInterestLevel] = useState('')

  const [data, setData] = useState<MarketingAggregates | null>(null)
  const [status, setStatus] = useState<MarketingStatus | null>(null)
  const [report, setReport] = useState<MarketingReport | null>(null)
  const [detail, setDetail] = useState<MarketingProductDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const [search, setSearch] = useState('')
  const [sortKey, setSortKey] = useState<SortKey>('interestedSessions')

  const filters = useMemo(() => ({
    projectType: projectType || undefined,
    productFamily: productFamily || undefined,
    interestLevel: interestLevel || undefined,
  }), [projectType, productFamily, interestLevel])

  /** Tables de libellés métier fournies par le backend (source unique). */
  const labels = useMemo(() => ({
    project: status?.projectTypeLabels ?? {},
    family: status?.productFamilyLabels ?? {},
    rejection: status?.rejectionReasonLabels ?? {},
    interest: status?.interestReasonLabels ?? {},
    unmet: status?.unmetReasonLabels ?? {},
    missingInfo: status?.missingInfoReasonLabels ?? {},
  }), [status])

  /* Les valeurs des filtres restent les codes techniques : seuls les libellés affichés changent. */
  const projectLabel = useCallback((code?: string | null) => readableLabel(labels.project, code), [labels])
  const familyLabel = useCallback((code?: string | null) => readableLabel(labels.family, code), [labels])
  const rejectionLabel = useCallback((code?: string | null) => readableLabel(labels.rejection, code), [labels])
  const unmetLabel = useCallback((code?: string | null) => readableLabel(labels.unmet, code), [labels])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const aggregates = await fetchMarketingOverview(period, from, to, filters)
      setData(aggregates)
      setStatus(await fetchMarketingStatus())
      setReport(await fetchMarketingReport(reportDate))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Loading error.')
    } finally {
      setLoading(false)
    }
  }, [period, from, to, filters, reportDate])

  useEffect(() => {
    void load()
  }, [load])

  async function runAction(action: () => Promise<unknown>, successMessage: string) {
    setWorking(true)
    setError(null)
    setNotice(null)
    try {
      await action()
      setNotice(successMessage)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Action failed.')
    } finally {
      setWorking(false)
    }
  }

  async function openProduct(productId: string) {
    try {
      setDetail(await fetchMarketingProduct(productId, period, from, to))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Details unavailable.')
    }
  }

  const products = useMemo(() => {
    const rows = (data?.products ?? []).filter((product) =>
      !search || product.productName.toLowerCase().includes(search.toLowerCase()))
    return [...rows].sort((a, b) => {
      const av = (a[sortKey] ?? 0) as number
      const bv = (b[sortKey] ?? 0) as number
      return bv - av
    })
  }, [data, search, sortKey])

  const topProducts = (data?.products ?? []).filter((p) => p.interestedSessions > 0).slice(0, 8)
  const maxInterested = Math.max(1, ...topProducts.map((p) => p.interestedSessions))
  const maxSeries = Math.max(1, ...(data?.series ?? []).map((point) => point.conversationCount))
  const alerts = (report?.alerts ?? []).slice(0, 6)
  const hasDemoData = Boolean(data?.demo) || Boolean(status?.demoMode)

  return (
    <div className="logs-shell marketing-page">
      <header className="logs-header">
        <a href="#/" className="logs-back"><ArrowLeft size={18} /> Back to chat</a>
        <h1><TrendingUp size={22} /> Marketing Intelligence</h1>
        <span className={`logs-status ${data ? 'ok' : 'ko'}`}>
          <span className="status-dot" /> {data ? `${data.dateFrom} → ${data.dateTo}` : '—'}
        </span>
      </header>

      <p className="mkt-subtitle">
        Analysis of needs, interests and behaviours from conversations with the AI Financial Coach.
        The figures are calculated by the analytics engine&nbsp;; the AI only interprets them.
      </p>

      <div className="mkt-toolbar">
        <div className="mkt-periods">
          {PERIODS.map((entry) => (
            <button
              key={entry.key}
              type="button"
              className={`mkt-period${period === entry.key ? ' active' : ''}`}
              onClick={() => setPeriod(entry.key)}
            >
              {entry.label}
            </button>
          ))}
        </div>
        {period === 'custom' && (
          <div className="mkt-dates">
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            <span>→</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
        )}
        <select value={projectType} onChange={(e) => setProjectType(e.target.value)} title="Filter by project">
          <option value="">All projects</option>
          {(data?.projects ?? []).map((project) => (
            <option key={project.projectType} value={project.projectType}>{projectLabel(project.projectType)}</option>
          ))}
        </select>
        <select value={interestLevel} onChange={(e) => setInterestLevel(e.target.value)} title="Filter by interest level">
          <option value="">All levels</option>
          <option value="HIGH">High interest</option>
          <option value="MEDIUM">Medium interest</option>
          <option value="LOW">Low interest</option>
        </select>
        <button type="button" className="mkt-action" onClick={() => void load()} disabled={loading || working}>
          <RefreshCw size={15} /> Refresh
        </button>
        <a className="mkt-action" href={marketingProductsCsvUrl(period, from, to)}>
          <Download size={15} /> Export CSV
        </a>
        <label className="mkt-report-date" title="Day analysed by the AI report (that day's aggregates are recalculated)">
          <span>Report for</span>
          <input
            type="date"
            aria-label="AI report date"
            value={reportDate}
            onChange={(e) => setReportDate(e.target.value)}
          />
        </label>
        <button
          type="button"
          className="mkt-action"
          disabled={loading || working || !reportDate}
          onClick={() => void runAction(
            () => regenerateMarketingReport(reportDate),
            `Aggregates and AI report regenerated for ${reportDate}.`,
          )}
          title="Recalculates the aggregates and regenerates the AI report for the selected date"
        >
          <Wand2 size={15} /> Regenerate AI report
        </button>
        <button
          type="button"
          className="mkt-action"
          disabled={working}
          onClick={() => void runAction(() => generateMarketingDemoData(7, 12), 'Demo data generated (demo=true).')}
          title="Generates clearly identified demo events"
        >
          <Bot size={15} /> Demo data
        </button>
      </div>

      {error && <div className="logs-error">{error}</div>}
      {notice && <div className="mkt-notice">{notice}</div>}
      {hasDemoData && (
        <div className="mkt-demo-banner" role="status">
          Demo data detected (events with <code>demo=true</code>): these do not represent
          real conversations.
        </div>
      )}
      {loading && !data ? (
        <div className="logs-empty">Loading marketing data…</div>
      ) : !data ? (
        <div className="logs-empty">No data for this period. Generate demo data or close a conversation.</div>
      ) : (
        <>
          {alerts.length > 0 && (
            <section className="mkt-alerts">
              {alerts.map((alert, index) => (
                <div key={`${alert.title}-${index}`} className={`mkt-alert ${(alert.level ?? 'INFO').toLowerCase()}`}>
                  <strong>{alert.title}</strong>
                  <span>{alert.description}</span>
                </div>
              ))}
            </section>
          )}

          <section className="mkt-kpis">
            <Kpi label="Conversations analysed" value={formatNumber(data.overview.conversationCount)} />
            <Kpi label="Sessions with interest" value={formatNumber(data.overview.sessionsWithInterest)} />
            <Kpi label="High interest (HIGH)" value={formatNumber(data.overview.highInterestCount)} />
            <Kpi label="Subscription intents" value={formatNumber(data.overview.subscriptionIntentCount)} />
            <Kpi label="Appointment requests" value={formatNumber(data.overview.appointmentRequestCount)} />
            <Kpi label="Unmet needs" value={formatNumber(data.overview.unmetNeedCount)} />
            <Kpi label="Missing information" value={formatNumber(data.overview.missingInformationCount)} />
          </section>

          <section className="mkt-chart">
            <h2>Trends over the period</h2>
            <div className="mkt-bars">
              {data.series.map((point) => (
                <div key={point.date} className="mkt-bar-col" title={`${point.date} · ${point.conversationCount} conversation(s), ${point.interestCount} interest(s)`}>
                  <div className="mkt-bar" style={{ height: `${Math.max(4, (point.conversationCount / maxSeries) * 100)}%` }} />
                  <span>{point.date.slice(5)}</span>
                </div>
              ))}
            </div>
            <p className="mkt-hint">Bars = conversations per day · hover for details (interests, high interest, appointments).</p>
          </section>

          <section className="mkt-grid">
            <div className="mkt-card">
              <h2>Most interesting products</h2>
              {topProducts.length === 0 && <p className="mkt-empty">No product interest in this period.</p>}
              {topProducts.map((product) => (
                <button key={product.productId} type="button" className="mkt-ranking-row" onClick={() => void openProduct(product.productId)}>
                  <span className="mkt-ranking-name">{product.productName}</span>
                  <span className="mkt-ranking-bar" style={{ width: `${(product.interestedSessions / maxInterested) * 100}%` }} />
                  <span className="mkt-ranking-value">
                    {product.interestedSessions} <small>(incl. {product.highCount} at high interest)</small>
                  </span>
                  <EvolutionBadge value={product.evolutionPercent} />
                </button>
              ))}
            </div>

            <div className="mkt-card">
              <h2>Recommendation vs actual interest</h2>
              <table className="mkt-table compact">
                <thead>
                  <tr><th>Product</th><th>Recommended</th><th>Interest</th><th>High</th><th>Rate</th></tr>
                </thead>
                <tbody>
                  {data.products.filter((p) => p.recommendedSessions > 0).slice(0, 8).map((product) => (
                    <tr key={product.productId} onClick={() => void openProduct(product.productId)}>
                      <td>{product.productName}</td>
                      <td>{product.recommendedSessions}</td>
                      <td>{product.interestedSessions}</td>
                      <td>{product.highCount}</td>
                      <td className={(product.interestRate ?? 1) < 0.05 ? 'mkt-low' : ''}>{formatPercent(product.interestRate)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mkt-card">
              <h2>Customer projects</h2>
              {data.projects.map((project) => (
                <button
                  key={project.projectType}
                  type="button"
                  className="mkt-row"
                  onClick={() => setProjectType(project.projectType === projectType ? '' : project.projectType)}
                >
                  <span title={project.projectType}>{projectLabel(project.projectType)}</span>
                  <span>{formatNumber(project.volume)} <small>({formatPercent(project.share)})</small></span>
                  <EvolutionBadge value={project.evolutionPercent} />
                </button>
              ))}
            </div>

            <div className="mkt-card">
              <h2>Why do customers decline?</h2>
              {data.rejections.length === 0 && <p className="mkt-empty">No decline recorded.</p>}
              {data.rejections.map((rejection) => (
                <div key={rejection.reasonCategory} className="mkt-row">
                  <span title={rejection.reasonCategory}>{rejectionLabel(rejection.reasonCategory)}</span>
                  <span>{formatNumber(rejection.count)} <small>({formatPercent(rejection.share)})</small></span>
                </div>
              ))}
            </div>

            <div className="mkt-card">
              <h2>Frequently associated products</h2>
              {data.crossSell.length === 0 && <p className="mkt-empty">No significant association.</p>}
              {data.crossSell.slice(0, 6).map((pair) => (
                <div key={`${pair.sourceProductId}-${pair.targetProductId}`} className="mkt-row">
                  <span>{pair.sourceProductName} → {pair.targetProductName}</span>
                  <span>{formatNumber(pair.commonSessions)} <small>({formatPercent(pair.rate, 0)})</small></span>
                </div>
              ))}
            </div>

            <div className="mkt-card">
              <h2>Unmet needs</h2>
              {data.unmetNeeds.length === 0 && <p className="mkt-empty">No unmet need detected.</p>}
              {data.unmetNeeds.slice(0, 6).map((need, index) => (
                <div key={`${need.projectType}-${need.reasonCategory}-${index}`} className="mkt-row column">
                  <span className="mkt-strong" title={[need.projectType, need.reasonCategory].filter(Boolean).join(' · ')}>
                    {isUnknown(need.projectType) ? 'Unidentified project' : projectLabel(need.projectType)}
                    {' — '}
                    {isUnknown(need.reasonCategory) ? 'Reason not specified' : unmetLabel(need.reasonCategory)}
                  </span>
                  <span className="mkt-muted">
                    {need.reason?.trim() && `"${need.reason.trim()}" · `}
                    {formatNumber(need.count)} {need.count > 1 ? 'requests' : 'request'}
                    {need.averageConfidence != null && ` · AI confidence ${formatPercent(need.averageConfidence, 0)}`}
                  </span>
                </div>
              ))}
            </div>

          </section>

          <section className="mkt-card wide">
            <h2>Full product table</h2>
            <div className="mkt-table-tools">
              <input
                type="search"
                placeholder="Search for a product…"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
              <select value={sortKey} onChange={(event) => setSortKey(event.target.value as SortKey)}>
                <option value="interestedSessions">Sort: interested sessions</option>
                <option value="recommendedSessions">Sort: recommendations</option>
                <option value="interestRate">Sort: interest rate</option>
                <option value="score">Sort: score</option>
                <option value="evolutionPercent">Sort: change</option>
              </select>
              <span className="mkt-muted">{products.length} product(s)</span>
            </div>
            <div className="mkt-table-scroll">
              <table className="mkt-table">
                <thead>
                  <tr>
                    <th>Product</th><th>Family</th><th>Recommendations</th><th>Interests</th>
                    <th>High</th><th>Medium</th><th>Declines</th><th>Subscription</th><th>Appointments</th>
                    <th>Interest rate</th><th>Score</th><th>Change</th>
                  </tr>
                </thead>
                <tbody>
                  {products.slice(0, 40).map((product) => (
                    <tr key={product.productId} onClick={() => void openProduct(product.productId)}>
                      <td>{product.productName}</td>
                      <td>{isUnknown(product.productFamily) ? '—' : familyLabel(product.productFamily)}</td>
                      <td>{product.recommendedSessions}</td>
                      <td>{product.interestedSessions}</td>
                      <td>{product.highCount}</td>
                      <td>{product.mediumCount}</td>
                      <td>{product.rejectedCount}</td>
                      <td>{product.subscriptionIntentCount}</td>
                      <td>{product.appointmentRequestCount}</td>
                      <td>{formatPercent(product.interestRate)}</td>
                      <td>{product.score.toLocaleString('en-GB')}</td>
                      <td><EvolutionBadge value={product.evolutionPercent} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}

      {detail && (
        <div className="mkt-drawer" role="dialog" aria-label="Product details">
          <div className="mkt-drawer-head">
            <h2>{detail.metric?.productName ?? detail.productId}</h2>
            <button type="button" onClick={() => setDetail(null)}>Close</button>
          </div>
          {detail.metric ? (
            <div className="mkt-drawer-grid">
              <Metric label="Recommendations" value={formatNumber(detail.metric.recommendedSessions)} />
              <Metric label="Interested sessions" value={formatNumber(detail.metric.interestedSessions)} />
              <Metric label="Unique customers" value={formatNumber(detail.metric.uniqueInterestedCustomers)} />
              <Metric label="High / medium interest" value={`${detail.metric.highCount} / ${detail.metric.mediumCount}`} />
              <Metric label="Declines" value={formatNumber(detail.metric.rejectedCount)} />
              <Metric label="Subscription / appointments" value={`${detail.metric.subscriptionIntentCount} / ${detail.metric.appointmentRequestCount}`} />
              <Metric label="Interest rate" value={formatPercent(detail.metric.interestRate)} />
              <Metric label="Score" value={detail.metric.score.toLocaleString('en-GB')} />
            </div>
          ) : (
            <p className="mkt-empty">No metric for this period.</p>
          )}
          {detail.rejections.length > 0 && (
            <>
              <h3>Decline reasons</h3>
              <ul className="mkt-report-list">
                {detail.rejections.map((rejection) => (
                  <li key={rejection.reasonCategory}><strong>{rejectionLabel(rejection.reasonCategory)}</strong> — {rejection.count} ({formatPercent(rejection.share)})</li>
                ))}
              </ul>
            </>
          )}
          {detail.crossSell.length > 0 && (
            <>
              <h3>Associated products</h3>
              <ul className="mkt-report-list">
                {detail.crossSell.map((pair) => (
                  <li key={pair.targetProductId}><strong>{pair.targetProductName}</strong> — {pair.commonSessions} common session(s) ({formatPercent(pair.rate, 0)})</li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </div>
  )
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="mkt-kpi">
      <span className="mkt-kpi-label">{label}</span>
      <span className="mkt-kpi-value">{value}</span>
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="mkt-metric">
      <span className="mkt-muted">{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function EvolutionBadge({ value }: { value: number | null | undefined }) {
  if (value == null) return <span className="mkt-evo flat">—</span>
  const up = value > 0
  return (
    <span className={`mkt-evo ${up ? 'up' : value < 0 ? 'down' : 'flat'}`}>
      {up ? <TrendingUp size={13} /> : value < 0 ? <TrendingDown size={13} /> : null}
      {formatEvolution(value)}
    </span>
  )
}

