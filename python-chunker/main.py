"""
Python Chunker Microservice
Clones GitHub repos and splits code into AST-aware chunks using tree-sitter.
Reranking is handled by Cohere API in Spring Boot — no ML models here.
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import subprocess, shutil, uuid, hashlib
from pathlib import Path
from typing import Optional
import tree_sitter_python as tspython
import tree_sitter_javascript as tsjavascript
import tree_sitter_java as tsjava
import tree_sitter_go as tsgo
from tree_sitter import Language, Parser

app = FastAPI(title="Code Chunker Microservice")

LANGUAGES = {
    ".py":   Language(tspython.language()),
    ".js":   Language(tsjavascript.language()),
    ".ts":   Language(tsjavascript.language()),
    ".java": Language(tsjava.language()),
    ".go":   Language(tsgo.language()),
}

EXT_NAME = {
    ".py": "python", ".js": "javascript", ".ts": "typescript",
    ".java": "java", ".go": "go"
}

CHUNK_NODE_TYPES = {
    "python":     {"function_definition", "class_definition"},
    "javascript": {"function_declaration", "arrow_function", "class_declaration", "method_definition"},
    "typescript": {"function_declaration", "arrow_function", "class_declaration", "method_definition"},
    "java":       {"method_declaration", "class_declaration", "constructor_declaration"},
    "go":         {"function_declaration", "method_declaration"},
}

class ChunkRequest(BaseModel):
    repoUrl: str
    branch: str = "main"

class CodeChunk(BaseModel):
    chunkId: str
    filePath: str
    language: str
    nodeType: str
    functionName: Optional[str]
    startLine: int
    endLine: int
    content: str

class ChunkResponse(BaseModel):
    repoId: str
    chunks: list[CodeChunk]
    totalChunks: int

def extract_name(node, source_bytes: bytes) -> Optional[str]:
    for child in node.children:
        if child.type == "identifier":
            return source_bytes[child.start_byte:child.end_byte].decode("utf-8", errors="replace")
    return None

def parse_file(file_path: Path, repo_root: Path) -> list[CodeChunk]:
    ext = file_path.suffix.lower()
    if ext not in LANGUAGES:
        return []
    lang = LANGUAGES[ext]
    lang_name = EXT_NAME[ext]
    target_types = CHUNK_NODE_TYPES.get(lang_name, set())
    source = file_path.read_bytes()
    parser = Parser(lang)
    tree = parser.parse(source)
    rel_path = str(file_path.relative_to(repo_root))
    chunks = []

    def walk(node):
        if node.type in target_types:
            content = source[node.start_byte:node.end_byte].decode("utf-8", errors="replace")
            if node.end_point[0] - node.start_point[0] < 2:
                return
            chunk_id = hashlib.md5(f"{rel_path}:{node.start_point[0]}".encode()).hexdigest()
            chunks.append(CodeChunk(
                chunkId=chunk_id, filePath=rel_path, language=lang_name,
                nodeType=node.type, functionName=extract_name(node, source),
                startLine=node.start_point[0] + 1, endLine=node.end_point[0] + 1,
                content=content,
            ))
        for child in node.children:
            walk(child)

    walk(tree.root_node)

    if not chunks:
        content = source.decode("utf-8", errors="replace")
        if content.strip():
            chunk_id = hashlib.md5(rel_path.encode()).hexdigest()
            chunks.append(CodeChunk(
                chunkId=chunk_id, filePath=rel_path, language=lang_name,
                nodeType="module", functionName=None,
                startLine=1, endLine=len(content.splitlines()),
                content=content[:4000],
            ))
    return chunks

CLONE_DIR = Path("/tmp/repos")
CLONE_DIR.mkdir(exist_ok=True)
SKIP_DIRS = {".git", "node_modules", "__pycache__", ".venv", "venv", "dist", "build", "target"}

@app.post("/chunk", response_model=ChunkResponse)
async def chunk_repo(request: ChunkRequest):
    print("========== CHUNK REQUEST ==========")
    print(request)
    print("repoUrl =", request.repoUrl)
    print("branch =", request.branch)
    repo_id = str(uuid.uuid5(uuid.NAMESPACE_URL, request.repoUrl))
    clone_path = CLONE_DIR / repo_id

    if clone_path.exists():
        shutil.rmtree(clone_path)
    try:
        subprocess.run(
            ["git", "clone", "--depth", "1", "--branch", request.branch,
             request.repoUrl, str(clone_path)],
            check=True, capture_output=True, timeout=120
        )
    except subprocess.CalledProcessError as e:
        err = e.stderr.decode()
        print("GIT CLONE FAILED:")
        print(err)
        raise HTTPException(status_code=400, detail=f"Git clone failed: {err}")
    except subprocess.TimeoutExpired:
        raise HTTPException(status_code=408, detail="Git clone timed out")

    all_chunks: list[CodeChunk] = []
    for file_path in clone_path.rglob("*"):
        if any(skip in file_path.parts for skip in SKIP_DIRS):
            continue
        if file_path.is_file() and file_path.suffix.lower() in LANGUAGES:
            try:
                all_chunks.extend(parse_file(file_path, clone_path))
            except Exception as e:
                print(f"Warning: failed to parse {file_path}: {e}")

    shutil.rmtree(clone_path)
    return ChunkResponse(repoId=repo_id, chunks=all_chunks, totalChunks=len(all_chunks))

@app.get("/health")
def health():
    return {"status": "ok"}
