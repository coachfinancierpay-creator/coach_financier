import { useEffect, useState } from 'react'
import { ArrowLeft, Bot, Braces, Database, Eye, FileSearch, History, Mail, MessageSquare, Trash2, Type } from 'lucide-react'
import { clearLogs, fetchConversation, fetchLogAnswer, fetchLogDataSent, fetchLogPrompt, fetchLogs } from './api'
import type { AiLog, ConversationData } from './types'

/**
 * État d'envoi du mail de notification au conseiller (trace de clôture « dossier de suivi »).
 * Le statut vient du backend (bloc [SUIVI] de la clôture) : SENT = envoyé sans erreur ;
 * PREPARED = envoi volontairement désactivé (send=false, dry-run) ; les autres = non envoyé.
 */
const MAIL_STATUS_LABELS: Record<string, { label: string; kind: 'ok' | 'warn' | 'ko' }> = {
  SENT: { label: 'Advisor email: sent without error', kind: 'ok' },
  PREPARED: { label: 'Advisor email: not sent (preparation mode)', kind: 'warn' },
  MAIL_UNAVAILABLE: { label: 'Advisor email: NOT sent (email service unavailable)', kind: 'ko' },
  SEND_FAILED: { label: 'Advisor email: NOT sent (send failed)', kind: 'ko' },
  AI_FAILED: { label: 'Advisor email: NOT sent (AI summary failed)', kind: 'ko' },
}

function mailStatusInfo(status: string | undefined) {
  if (!status) return null
  const key = status.toUpperCase()
  return MAIL_STATUS_LABELS[key] ?? { label: `Advisor email: ${status}`, kind: 'warn' as const }
}

export default function Logs() {
  const [logs, setLogs] = useState<AiLog[]>([])
  const [error, setError] = useState<string | null>(null)
  const [connected, setConnected] = useState(false)
  const [openPrompts, setOpenPrompts] = useState<Set<number>>(new Set())
  const [openDataSent, setOpenDataSent] = useState<Set<number>>(new Set())
  const [openDebug, setOpenDebug] = useState<Set<number>>(new Set())
  const [openAnswers, setOpenAnswers] = useState<Set<number>>(new Set())
  const [openHistory, setOpenHistory] = useState<Set<number>>(new Set())
  const [prompts, setPrompts] = useState<Record<number, string>>({})
  const [dataSent, setDataSent] = useState<Record<number, { content: string; charCount: number }>>({})
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const [histories, setHistories] = useState<Record<number, ConversationData>>({})
  const [loadingPromptId, setLoadingPromptId] = useState<number | null>(null)
  const [loadingDataSentId, setLoadingDataSentId] = useState<number | null>(null)
  const [loadingAnswerId, setLoadingAnswerId] = useState<number | null>(null)
  const [loadingHistoryId, setLoadingHistoryId] = useState<number | null>(null)

  useEffect(() => {
    let active = true
    async function refresh() {
      try {
        const data = await fetchLogs()
        if (!active) return
        setLogs(data)
        setError(null)
        setConnected(true)
      } catch (err) {
        if (!active) return
        setError(err instanceof Error ? err.message : 'Backend connection error.')
        setConnected(false)
      }
    }
    refresh()
    const timer = setInterval(refresh, 2000)
    return () => {
      active = false
      clearInterval(timer)
    }
  }, [])

  async function handleClear() {
    try {
      await clearLogs()
      setLogs([])
      setPrompts({})
      setDataSent({})
      setAnswers({})
      setHistories({})
      setOpenPrompts(new Set())
      setOpenDataSent(new Set())
      setOpenDebug(new Set())
      setOpenAnswers(new Set())
      setOpenHistory(new Set())
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error while deleting the logs.')
    }
  }

  async function toggleDataSent(id: number) {
    if (openDataSent.has(id)) {
      setOpenDataSent((prev) => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
      return
    }
    setOpenDataSent((prev) => new Set(prev).add(id))
    if (dataSent[id] === undefined) {
      setLoadingDataSentId(id)
      try {
        const value = await fetchLogDataSent(id)
        setDataSent((prev) => ({ ...prev, [id]: value }))
      } catch (err) {
        const detail = err instanceof Error ? err.message : String(err)
        setDataSent((prev) => ({ ...prev, [id]: { content: `(Unable to load the attached data. ${detail})`, charCount: 0 } }))
      } finally {
        setLoadingDataSentId(null)
      }
    }
  }

  function toggleDebug(id: number) {
    setOpenDebug((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function togglePrompt(id: number) {
    if (openPrompts.has(id)) {
      setOpenPrompts((prev) => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
      return
    }
    setOpenPrompts((prev) => new Set(prev).add(id))
    if (prompts[id] === undefined) {
      setLoadingPromptId(id)
      try {
        const prompt = await fetchLogPrompt(id)
        setPrompts((prev) => ({ ...prev, [id]: prompt }))
      } catch (err) {
        const detail = err instanceof Error ? err.message : String(err)
        setPrompts((prev) => ({ ...prev, [id]: `(Unable to load the prompt for this trace. ${detail})` }))
      } finally {
        setLoadingPromptId(null)
      }
    }
  }

  async function toggleAnswer(id: number) {
    if (openAnswers.has(id)) {
      setOpenAnswers((prev) => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
      return
    }
    setOpenAnswers((prev) => new Set(prev).add(id))
    if (answers[id] === undefined) {
      setLoadingAnswerId(id)
      try {
        const answer = await fetchLogAnswer(id)
        setAnswers((prev) => ({ ...prev, [id]: answer }))
      } catch (err) {
        const detail = err instanceof Error ? err.message : String(err)
        setAnswers((prev) => ({ ...prev, [id]: `(Unable to load the answer for this trace. ${detail})` }))
      } finally {
        setLoadingAnswerId(null)
      }
    }
  }

  async function toggleHistory(id: number, sessionId: string) {
    if (openHistory.has(id)) {
      setOpenHistory((prev) => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
      return
    }
    setOpenHistory((prev) => new Set(prev).add(id))
    if (histories[id] === undefined) {
      setLoadingHistoryId(id)
      try {
        const data = await fetchConversation(sessionId)
        setHistories((prev) => ({ ...prev, [id]: data }))
      } catch (err) {
        const detail = err instanceof Error ? err.message : String(err)
        setHistories((prev) => ({
          ...prev,
          [id]: { sessionId, messages: [], summary: `(Unable to load the history. ${detail})` },
        }))
      } finally {
        setLoadingHistoryId(null)
      }
    }
  }

  return (
    <div className="logs-shell">
      <header className="logs-header">
        <a href="#/" className="logs-back"><ArrowLeft size={18} /> Back to chat</a>
        <h1><Bot size={22} /> AI call logs</h1>
        <span className={`logs-status ${connected ? 'ok' : 'ko'}`}>
          <span className="status-dot" /> {connected ? 'Backend connected' : 'Offline'}
        </span>
      </header>

      {error && <div className="logs-error">Unable to load the logs: {error}</div>}

      <div className="logs-toolbar">
        <span className="logs-count">{logs.length} trace(s) · refresh every 2 s</span>
        <button className="logs-clear" type="button" onClick={handleClear} disabled={logs.length === 0}>
          <Trash2 size={16} /> Clear logs
        </button>
      </div>

      <div className="logs-list">
        {!error && logs.length === 0 && (
          <div className="logs-empty">No AI calls yet. Send a message in the chat.</div>
        )}

        {logs.map((log) => (
          <article key={log.id} className="logs-log">
            <div className="logs-log-head">
              <span className="logs-id">#{log.id}</span>
              <span className={`logs-status-badge ${log.status.toLowerCase()}`}>{log.status}</span>
              {log.agent ? (
                <span className="logs-status-badge agent" title="AI agent used">
                  <Bot size={12} /> {log.agent}
                </span>
              ) : null}
              {mailStatusInfo(log.mailStatus) ? (
                <span
                  className={`logs-status-badge mail-${mailStatusInfo(log.mailStatus)!.kind}`}
                  title="Send the notification email to the advisor (follow-up case file)"
                >
                  <Mail size={12} /> {mailStatusInfo(log.mailStatus)!.label}
                </span>
              ) : null}
              <time>{new Date(log.timestamp).toLocaleString('en-GB')}</time>
              <button
                className="logs-prompt-btn"
                type="button"
                onClick={() => togglePrompt(log.id)}
                disabled={loadingPromptId === log.id}
                title="Show the prompt sent to the AI (without the attached data)"
              >
                <Eye size={14} />
                {openPrompts.has(log.id) ? 'Hide prompt' : 'Show prompt'}
              </button>
              <button
                className="logs-prompt-btn"
                type="button"
                onClick={() => toggleDataSent(log.id)}
                disabled={loadingDataSentId === log.id}
                title="Show the full content of the attached data sent to the AI"
              >
                <Database size={14} />
                {openDataSent.has(log.id) ? 'Hide attached data' : 'Show attached data'}
              </button>
              {log.debug ? (
                <button
                  className="logs-prompt-btn"
                  type="button"
                  onClick={() => toggleDebug(log.id)}
                  title="Show the INTENT / PRODUCT_FILTER / COACH details"
                >
                  <Braces size={14} />
                  {openDebug.has(log.id) ? 'Hide filtering' : 'Show filtering'}
                </button>
              ) : null}
              <button
                className="logs-prompt-btn"
                type="button"
                onClick={() => toggleAnswer(log.id)}
                disabled={loadingAnswerId === log.id}
                title="Show the answer sent by the AI"
              >
                <MessageSquare size={14} />
                {openAnswers.has(log.id) ? 'Hide answer' : 'Show answer'}
              </button>
              <button
                className="logs-prompt-btn"
                type="button"
                onClick={() => toggleHistory(log.id, log.sessionId)}
                disabled={loadingHistoryId === log.id}
                title="Show the conversation history with the customer"
              >
                <History size={14} />
                {openHistory.has(log.id) ? 'Hide history' : 'History'}
              </button>
              <span className="logs-session">{log.sessionId}</span>
            </div>

            <div className="logs-log-body">
              <p className="logs-message"><strong>Customer&nbsp;:</strong> {log.clientMessage}</p>
              <div className="logs-line">
                <History size={15} /> <strong>History&nbsp;:</strong> {log.historyCount} message(s)
              </div>
              <div className="logs-line">
                <Type size={15} /> <strong>Characters sent&nbsp;:</strong> {log.charCount.toLocaleString('en-GB')}
              </div>
              <div className="logs-line">
                <Type size={15} /> <strong>AI answer characters&nbsp;:</strong>{' '}
                {log.answerCharCount.toLocaleString('en-GB')}
              </div>
              <div className="logs-line">
                <Type size={15} /> <strong>AI response time&nbsp;:</strong>{' '}
                {log.responseTimeMs > 0 ? `${log.responseTimeMs.toLocaleString('en-GB')} ms` : 'not measured'}
              </div>

              <div className="logs-sub"><Database size={15} /> Data sent ({log.dataSent.length})</div>
              <div className="chips">
                {log.dataSent.length === 0 && <span className="chip empty">none</span>}
                {log.dataSent.map((d, i) => <span key={i} className="chip">{d}</span>)}
              </div>

              <div className="logs-sub"><FileSearch size={15} /> Data requested by the AI</div>
              <div className="chips">
                {log.requestedData.length === 0 && <span className="chip empty">none</span>}
                {log.requestedData.map((r, i) => <span key={i} className="chip accent">{r}</span>)}
              </div>
            </div>

            {openPrompts.has(log.id) && (
              <div className="logs-prompt-box">
                {loadingPromptId === log.id && !prompts[log.id] ? (
                  <div className="logs-prompt-loading">Loading prompt…</div>
                ) : (
                  <pre className="logs-prompt">{prompts[log.id] ?? ''}</pre>
                )}
              </div>
            )}

            {openDataSent.has(log.id) && (
              <div className="logs-prompt-box">
                <div className="logs-sub">
                  <Database size={15} /> Attached data content ({(dataSent[log.id]?.charCount ?? 0).toLocaleString('en-GB')} characters)
                </div>
                {loadingDataSentId === log.id && dataSent[log.id] === undefined ? (
                  <div className="logs-prompt-loading">Loading attached data…</div>
                ) : (
                  <pre className="logs-prompt">{dataSent[log.id]?.content ?? ''}</pre>
                )}
              </div>
            )}

            {openDebug.has(log.id) && log.debug && (
              <div className="logs-prompt-box">
                <div className="logs-sub">Business classification &amp; filtering</div>
                <pre className="logs-prompt">{log.debug}</pre>
              </div>
            )}

            {openAnswers.has(log.id) && (
              <div className="logs-prompt-box">
                <div className="logs-sub">AI answer</div>
                {loadingAnswerId === log.id && answers[log.id] === undefined ? (
                  <div className="logs-prompt-loading">Loading answer…</div>
                ) : (
                  <pre className="logs-prompt">{answers[log.id] ?? ''}</pre>
                )}
              </div>
            )}

            {openHistory.has(log.id) && (
              <div className="logs-prompt-box">
                <div className="logs-sub">Conversation history with the customer</div>
                {loadingHistoryId === log.id && histories[log.id] === undefined ? (
                  <div className="logs-prompt-loading">Loading history…</div>
                ) : (
                  <div className="history-list">
                    {histories[log.id]?.summary ? (
                      <div className="history-summary">Summary: {histories[log.id]?.summary}</div>
                    ) : null}
                    {histories[log.id]?.messages.length === 0 && (
                      <span className="chip empty">no messages</span>
                    )}
                    {histories[log.id]?.messages.map((msg, index) => (
                      <div key={index} className={`history-line ${msg.role}`}>
                        <span className="history-role">{msg.role === 'user' ? 'Customer' : 'Coach'}</span>
                        <span className="history-content">{msg.content}</span>
                        {msg.timestamp && (
                          <span className="history-time">
                            {new Date(msg.timestamp).toLocaleString('en-GB')}
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </article>
        ))}
      </div>
    </div>
  )
}
