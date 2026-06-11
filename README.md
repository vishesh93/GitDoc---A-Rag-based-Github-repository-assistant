# Codebase Q&A Bot — Free Cloud Stack

Ask natural language questions about any GitHub repo. Get answers with exact code citations. **Zero downloads. Zero local GPU. Everything runs in the cloud on free tiers.**

## Free Services (sign up takes 2 minutes each)

| What | Service | Sign up | Free limit |
|------|---------|---------|------------|
| LLM | [Groq](https://console.groq.com) | No credit card | 14,400 req/day |
| Embeddings | [Cohere](https://dashboard.cohere.com) | No credit card | 1000 req/month |
| Reranking | Cohere (same key) | — | Same trial key |
| Vector DB | [Qdrant Cloud](https://cloud.qdrant.io) | No credit card | 1GB forever |
| Cache | Redis (Docker, local) | — | Free |

**Only thing running locally:** Redis + the Python AST chunker (no ML models, just tree-sitter)

## Architecture

```
User → Spring Boot (:8080)
           │
    ┌──────┼──────────────┐
    │      │              │
    ▼      ▼              ▼
 Groq   Cohere      Qdrant Cloud
(chat)  (embed       (vector DB,
         + rerank)    free 1GB)
    │
    ▼
Python Chunker (:8000)
tree-sitter AST parsing
(local, no ML models)
```

## Setup (10 minutes)

### 1. Get your free API keys

- **Groq:** https://console.groq.com → API Keys → Create
- **Cohere:** https://dashboard.cohere.com → API Keys → Trial Key
- **Qdrant Cloud:** https://cloud.qdrant.io → Create Cluster (free tier) → copy URL + API key

### 2. Configure

```bash
cp .env.example .env
# Fill in your 4 keys
```

### 3. Run

```bash
docker compose up --build
```

That's it. API is live at `http://localhost:8080`.

## API

### Ingest a repo
```bash
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{"repoUrl": "https://github.com/spring-projects/spring-petclinic"}'
# → {"repoId": "...", "status": "PROCESSING"}
```

### Poll status
```bash
curl http://localhost:8080/api/v1/ingest/{repoId}/status
# → {"status": "DONE", "totalChunks": 342}
```

### Ask a question
```bash
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"repoId": "...", "question": "How is the Owner entity mapped to the database?"}'
```

**Response:**
```json
{
  "answer": "The Owner entity is mapped using JPA @Entity and @Table(name='owners')...",
  "citations": [
    {
      "filePath": "src/main/java/.../owner/Owner.java",
      "functionName": "Owner",
      "startLine": 42,
      "endLine": 89,
      "codeSnippet": "@Entity\n@Table(name = \"owners\")\npublic class Owner...",
      "relevanceScore": 0.97
    }
  ],
  "model": "groq/llama3-8b + cohere/rerank-v3",
  "latencyMs": 980
}
```


