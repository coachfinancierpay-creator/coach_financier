import { useEffect, useState } from 'react'
import { ArrowLeft, Bot, Check, Save, X } from 'lucide-react'
import { fetchAgentPrompt, fetchAgents, saveAgentPrompt, type AgentEntry } from './api'

export default function Agents() {
  const [entries, setEntries] = useState<AgentEntry[]>([])
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [file, setFile] = useState('')
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  // Chargement de la liste ; agent générique sélectionné par défaut.
  useEffect(() => {
    let active = true
    fetchAgents()
      .then((list) => {
        if (!active) return
        setEntries(list)
        const generic = list.find((entry) => entry.key === 'generic') ?? list[0]
        if (generic) setSelectedKey(generic.key)
      })
      .catch((err) => {
        if (!active) return
        setError(err instanceof Error ? err.message : 'Loading error.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  // Chargement du prompt quand l'agent sélectionné change.
  useEffect(() => {
    if (!selectedKey) return
    let active = true
    setLoading(true)
    setSaved(false)
    setError(null)
    fetchAgentPrompt(selectedKey)
      .then((data) => {
        if (!active) return
        setContent(data.content ?? '')
        setFile(entries.find((entry) => entry.key === selectedKey)?.file ?? '')
      })
      .catch((err) => {
        if (!active) return
        setError(err instanceof Error ? err.message : 'Loading error.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedKey])

  async function handleSave() {
    if (!selectedKey) return
    setSaving(true)
    setSaved(false)
    setError(null)
    try {
      const data = await saveAgentPrompt(selectedKey, content)
      setContent(data.content ?? content)
      setSaved(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error while saving.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="logs-shell agents-page">
      <header className="logs-header">
        <a href="#/" className="logs-back"><ArrowLeft size={18} /> Back to chat</a>
        <h1><Bot size={22} /> Agents (prompts)</h1>
        <span className="logs-status ok"><span className="status-dot" /> Saved in agent/ — active immediately</span>
      </header>

      {error && <div className="logs-error">{error}</div>}

      <div className="prompt-body">
        <label className="prompt-field">
          <span className="prompt-label">Agent</span>
          <select
            className="prompt-select"
            value={selectedKey ?? ''}
            onChange={(event) => {
              setSelectedKey(event.target.value)
              setContent('')
              setSaved(false)
            }}
          >
            {entries.map((entry) => (
              <option key={entry.key} value={entry.key}>{entry.libelle}</option>
            ))}
          </select>
          {file && <span className="prompt-file">agent/{file}</span>}
        </label>

        <p className="prompt-hint">
          Each agent has its own system prompt. The <strong>generic</strong> agent{' '}
          includes the content of the <strong>main agent</strong> through the{' '}
          <code>[agent_principal]</code> tag. The <strong>follow-up agent</strong> is used at the end
          of a conversation (advisor case file + customer email draft) and the analyst agents{' '}
          <strong>marketing</strong>, <strong>quality &amp; satisfaction</strong> and{' '}
          <strong>advisor feedback</strong> write the reports for the{' '}
          <a href="#/marketing">Marketing</a>, <a href="#/quality">Quality</a> and{' '}
          <a href="#/advisor-feedback">Advisor Feedback</a> pages from the statistics computed by the
          backend. Saving takes effect{' '}
          <strong>immediately</strong>, without restarting the server.
        </p>

        {loading ? (
          <div className="prompt-loading">Loading…</div>
        ) : (
          <textarea
            className="prompt-textarea"
            value={content}
            onChange={(event) => {
              setContent(event.target.value)
              setSaved(false)
            }}
            placeholder="Enter the agent prompt…"
            spellCheck={false}
          />
        )}

        <div className="prompt-actions">
          <span className={`prompt-saved ${saved ? 'visible' : ''}`}>
            {saved && <><Check size={16} /> Prompt “{selectedKey}” saved and active.</>}
          </span>
          <button className="prompt-save" type="button" onClick={handleSave} disabled={loading || saving}>
            {saving ? <X size={16} /> : <Save size={16} />}
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  )
}
