import { useState, useEffect, useRef } from 'react'
import { ingestRepo, getIngestionStatus } from '../api'

const s = {
  panel: {
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius)',
    padding: '24px',
  },
  label: { display: 'block', color: 'var(--muted)', fontSize: 12, fontWeight: 500, marginBottom: 6, letterSpacing: '0.05em', textTransform: 'uppercase' },
  row: { display: 'flex', gap: 10, marginBottom: 16 },
  input: {
    flex: 1, padding: '10px 14px',
    background: 'var(--bg)', border: '1px solid var(--border)',
    borderRadius: 'var(--radius)', color: 'var(--text)',
    transition: 'border-color .15s',
  },
  inputSmall: { width: 110 },
  btn: {
    padding: '10px 20px', borderRadius: 'var(--radius)',
    background: 'var(--accent)', color: '#fff', fontWeight: 600,
    transition: 'opacity .15s',
  },
  btnDisabled: { opacity: 0.5, pointerEvents: 'none' },
  status: { marginTop: 16, padding: '12px 16px', borderRadius: 'var(--radius)', fontSize: 13 },
  statusProcessing: { background: 'var(--accent-dim)', color: 'var(--accent)', border: '1px solid var(--accent)' },
  statusDone: { background: '#0d2e20', color: 'var(--green)', border: '1px solid var(--green)' },
  statusError: { background: '#2e1515', color: 'var(--red)', border: '1px solid var(--red)' },
  dot: { display: 'inline-block', width: 7, height: 7, borderRadius: '50%', marginRight: 8 },
}

export default function IngestPanel({ onRepoReady }) {
  const [url, setUrl] = useState('')
  const [branch, setBranch] = useState('main')
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState(null)   // null | { type, message }
  const [repoId, setRepoId] = useState(null)
  const pollRef = useRef(null)

  const startPoll = (id) => {
    pollRef.current = setInterval(async () => {
      try {
        const data = await getIngestionStatus(id)
        if (data.status === 'DONE') {
          clearInterval(pollRef.current)
          setStatus({ type: 'done', message: `Ready — ${data.totalChunks} code chunks indexed` })
          onRepoReady(id)
          setLoading(false)
        } else if (data.status === 'FAILED') {
          clearInterval(pollRef.current)
          setStatus({ type: 'error', message: 'Ingestion failed: ' + data.message })
          setLoading(false)
        }
      } catch (e) {
        clearInterval(pollRef.current)
        setStatus({ type: 'error', message: e.message })
        setLoading(false)
      }
    }, 2500)
  }

  const handleIngest = async () => {
    if (!url.trim()) return
    setLoading(true)
    setStatus({ type: 'processing', message: 'Cloning and indexing repo…' })
    try {
      const data = await ingestRepo(url.trim(), branch.trim() || 'main')
      setRepoId(data.repoId)
      startPoll(data.repoId)
    } catch (e) {
      setStatus({ type: 'error', message: e.message })
      setLoading(false)
    }
  }

  useEffect(() => () => clearInterval(pollRef.current), [])

  const dotColor = status?.type === 'done' ? 'var(--green)' : status?.type === 'error' ? 'var(--red)' : 'var(--accent)'
  const statusStyle = status?.type === 'done' ? s.statusDone : status?.type === 'error' ? s.statusError : s.statusProcessing

  return (
    <div style={s.panel}>
      <label style={s.label}>GitHub Repository</label>
      <div style={s.row}>
        <input
          style={s.input}
          placeholder="https://github.com/owner/repo"
          value={url}
          onChange={e => setUrl(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleIngest()}
          disabled={loading}
        />
        <input
          style={{ ...s.input, ...s.inputSmall }}
          placeholder="branch"
          value={branch}
          onChange={e => setBranch(e.target.value)}
          disabled={loading}
        />
        <button
          style={{ ...s.btn, ...(loading || !url.trim() ? s.btnDisabled : {}) }}
          onClick={handleIngest}
        >
          {loading ? 'Indexing…' : 'Index Repo'}
        </button>
      </div>

      {status && (
        <div style={{ ...s.status, ...statusStyle }}>
          <span style={{ ...s.dot, background: dotColor }} />
          {status.message}
          {status.type === 'processing' && (
            <span style={{ marginLeft: 8, opacity: 0.7 }}>This takes 1–3 min depending on repo size</span>
          )}
        </div>
      )}
    </div>
  )
}
