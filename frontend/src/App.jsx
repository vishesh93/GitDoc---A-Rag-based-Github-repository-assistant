import { useState } from 'react'
import IngestPanel from './components/IngestPanel'
import ChatPanel from './components/ChatPanel'

const s = {
  app: { minHeight: '100vh', display: 'flex', flexDirection: 'column' },
  header: {
    borderBottom: '1px solid var(--border)', padding: '0 32px',
    height: 56, display: 'flex', alignItems: 'center', gap: 12,
    background: 'var(--surface)',
  },
  logo: { fontFamily: 'var(--mono)', fontWeight: 500, fontSize: 15, color: 'var(--text)' },
  logoDim: { color: 'var(--muted)' },
  badge: { background: 'var(--accent-dim)', color: 'var(--accent)', borderRadius: 4, padding: '2px 8px', fontSize: 11, fontWeight: 600 },
  body: { flex: 1, display: 'flex', flexDirection: 'column', maxWidth: 860, width: '100%', margin: '0 auto', padding: '32px 24px', gap: 24 },
  chatWrap: { flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 24 },
  locked: { flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--muted)', border: '1px dashed var(--border)', borderRadius: 'var(--radius)', minHeight: 260, fontSize: 13 },
}

export default function App() {
  const [repoId, setRepoId] = useState(null)

  return (
    <div style={s.app}>
      <header style={s.header}>
        <span style={s.logo}><span style={s.logoDim}>{'</>'}</span> Codebase Q&A</span>
        <span style={s.badge}>RAG</span>
      </header>

      <main style={s.body}>
        <IngestPanel onRepoReady={setRepoId} />

        {repoId
          ? <div style={s.chatWrap}><ChatPanel repoId={repoId} /></div>
          : <div style={s.locked}>← Index a repo first, then ask questions here</div>
        }
      </main>
    </div>
  )
}
