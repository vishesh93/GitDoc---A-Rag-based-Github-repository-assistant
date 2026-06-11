const BASE =
  import.meta.env.VITE_API_BASE_URL + '/api/v1'

export async function ingestRepo(repoUrl, branch = 'main') {
  const res = await fetch(`${BASE}/ingest`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ repoUrl, branch })
  })
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}

export async function getIngestionStatus(repoId) {
  const res = await fetch(`${BASE}/ingest/${repoId}/status`)
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}

export async function queryRepo(repoId, question) {
  const res = await fetch(`${BASE}/query`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ repoId, question })
  })
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}
