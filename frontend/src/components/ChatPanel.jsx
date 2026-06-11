import { useState, useRef, useEffect } from 'react'
import { queryRepo } from '../api'

const s = {
  wrap: { display: 'flex', flexDirection: 'column', gap: 0, height: '100%' },
  messages: {
    flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column',
    gap: 20, padding: '20px 0', minHeight: 0,
  },
  empty: { color: 'var(--muted)', textAlign: 'center', marginTop: 60, lineHeight: 2 },
  emptyCode: { fontFamily: 'var(--mono)', fontSize: 12, color: 'var(--accent)', display: 'block', marginTop: 4 },
  msgWrap: { display: 'flex', flexDirection: 'column', gap: 4 },
  question: { alignSelf: 'flex-end', background: 'var(--accent-dim)', border: '1px solid var(--accent)', borderRadius: 'var(--radius)', padding: '10px 14px', maxWidth: '80%', color: 'var(--text)' },
  answer: { alignSelf: 'flex-start', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: '14px 16px', maxWidth: '100%' },
  answerText: { lineHeight: 1.8, whiteSpace: 'pre-wrap' },
  citations: { marginTop: 14, display: 'flex', flexDirection: 'column', gap: 8 },
  citLabel: { fontSize: 11, color: 'var(--muted)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 },
  citCard: { background: 'var(--bg)', border: '1px solid var(--border)', borderRadius: 6, overflow: 'hidden' },
  citHeader: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px', borderBottom: '1px solid var(--border)' },
  citFile: { fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--accent)' },
  citMeta: { fontSize: 11, color: 'var(--muted)' },
  citCode: { padding: '10px 12px', fontFamily: 'var(--mono)', fontSize: 12, color: '#c9d1d9', lineHeight: 1.7, whiteSpace: 'pre-wrap', overflowX: 'auto', maxHeight: 160, overflowY: 'auto' },
  score: { display: 'inline-block', background: 'var(--accent-dim)', color: 'var(--accent)', borderRadius: 4, padding: '1px 6px', fontSize: 11, marginLeft: 8 },
  meta: { fontSize: 11, color: 'var(--muted)', marginTop: 10, display: 'flex', gap: 16 },
  inputRow: { display: 'flex', gap: 10, paddingTop: 16, borderTop: '1px solid var(--border)' },
  input: { flex: 1, padding: '12px 16px', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', color: 'var(--text)', resize: 'none', lineHeight: 1.5 },
  btn: { padding: '12px 22px', background: 'var(--accent)', color: '#fff', borderRadius: 'var(--radius)', fontWeight: 600, alignSelf: 'flex-end', transition: 'opacity .15s' },
  btnDisabled: { opacity: 0.4, pointerEvents: 'none' },
  thinking: { alignSelf: 'flex-start', color: 'var(--muted)', fontStyle: 'italic', fontSize: 13 },
}

function Citation({ c, i }) {
  const [open, setOpen] = useState(false)
  return (
    <div style={s.citCard}>
      <div style={{ ...s.citHeader, cursor: 'pointer' }} onClick={() => setOpen(o => !o)}>
        <span>
          <span style={s.citFile}>{c.filePath}</span>
          {c.functionName && <span style={{ ...s.citMeta, marginLeft: 10 }}>fn: {c.functionName}</span>}
        </span>
        <span style={s.citMeta}>
          lines {c.startLine}–{c.endLine}
          <span style={s.score}>{(c.relevanceScore * 100).toFixed(0)}%</span>
          <span style={{ marginLeft: 10 }}>{open ? '▲' : '▼'}</span>
        </span>
      </div>
      {open && <pre style={s.citCode}>{c.codeSnippet}</pre>}
    </div>
  )
}

export default function ChatPanel({ repoId }) {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef(null)

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages])

  const send = async () => {
    const q = input.trim()
    if (!q || loading) return
    setInput('')
    setLoading(true)
    setMessages(m => [...m, { type: 'q', text: q }])

    try {
      const data = await queryRepo(repoId, q)
      setMessages(m => [...m, { type: 'a', data }])
    } catch (e) {
      setMessages(m => [...m, { type: 'a', data: { answer: 'Error: ' + e.message, citations: [] } }])
    }
    setLoading(false)
  }

  const handleKey = e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }

  return (
    <div style={s.wrap}>
      <div style={s.messages}>
        {messages.length === 0 && (
          <div style={s.empty}>
            Repo is indexed. Ask anything about it.
            <span style={s.emptyCode}>"How does authentication work?" · "Where is rate limiting handled?"</span>
          </div>
        )}
        {messages.map((m, i) =>
          m.type === 'q'
            ? <div key={i} style={s.msgWrap}><div style={s.question}>{m.text}</div></div>
            : (
              <div key={i} style={s.msgWrap}>
                <div style={s.answer}>
                  <div style={s.answerText}>{m.data.answer}</div>
                  {m.data.citations?.length > 0 && (
                    <div style={s.citations}>
                      <div style={s.citLabel}>Sources ({m.data.citations.length})</div>
                      {m.data.citations.map((c, j) => <Citation key={j} c={c} i={j} />)}
                    </div>
                  )}
                  {m.data.latencyMs && (
                    <div style={s.meta}>
                      <span>model: {m.data.model}</span>
                      <span>{m.data.latencyMs}ms</span>
                    </div>
                  )}
                </div>
              </div>
            )
        )}
        {loading && <div style={s.thinking}>Thinking…</div>}
        <div ref={bottomRef} />
      </div>

      <div style={s.inputRow}>
        <textarea
          rows={2}
          style={s.input}
          placeholder="Ask a question about the codebase… (Enter to send)"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKey}
          disabled={loading}
        />
        <button
          style={{ ...s.btn, ...(loading || !input.trim() ? s.btnDisabled : {}) }}
          onClick={send}
        >
          Ask
        </button>
      </div>
    </div>
  )
}
