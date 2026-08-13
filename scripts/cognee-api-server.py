#!/usr/bin/env python3
"""
Cognee REST API Server — KChat Integration
===========================================
Provides a lightweight FastAPI server wrapping cognee's v1.0 Python API.
KChat's Java backend calls these endpoints for long-term memory.

Endpoints (v1.0 only):
  POST /remember — Store data into permanent graph or session cache
  POST /recall    — Auto-routed retrieval with session awareness
  POST /forget    — Unified delete (dataset / data item / everything)
  POST /improve   — Self-derive cross-document relationships
  GET  /graph     — Get knowledge graph nodes and edges for visualization
  GET  /datasets  — List all datasets and their data counts
  GET  /health    — Health check

Usage:
  python3 scripts/cognee-api-server.py

Environment:
  COGNEE_PORT    — Server port (default: 8000)
  LLM_API_KEY    — API key for LLM (optional in local mode)
"""

import os
import sys
import json
import signal
import atexit
import socket
import logging

# ── 从项目 .env 文件加载环境变量（唯一配置来源）────────────
_env_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".env")
if not os.path.exists(_env_path):
    print(f"[cognee-api] WARNING: .env not found at {_env_path}")
    sys.exit(1)
from dotenv import load_dotenv
load_dotenv(_env_path, override=True)
print(f"[cognee-api] Loaded env from {_env_path}")

# ── 绕过 tiktoken 的网络依赖 ──────────────────────────────
# cognee 内部调用 tiktoken.encoding_for_model() 来获取 tokenizer，
# 但 tiktoken 需要联网下载编码文件。本地环境没有网络访问权限，
# 所以提供一个轻量替代对象，确保调用不会崩溃即可。
try:
    import tiktoken as _tiktoken
except ImportError:
    _tiktoken = None

class _DummyTikToken:
    """极简 tokenizer：纯近似计数，绕过联网下载。嵌入服务端会自行 tokenize。"""
    def encode(self, text, *a, **kw):
        return list(range(len(text) // 4 + 1))
    def decode(self, tokens, *a, **kw):
        return " ".join(str(t) for t in tokens)
    @property
    def name(self):
        return "local-fallback"

if _tiktoken is not None:
    _orig_enc_model = _tiktoken.encoding_for_model
    def _safe_encoding_for_model(name):
        try:
            return _orig_enc_model(name)
        except Exception:
            return _DummyTikToken()
    _tiktoken.encoding_for_model = _safe_encoding_for_model

# ── Cognee 系统设置（不属于用户配置，固定值）──────────────
os.environ.setdefault('COGNEE_CACHING', 'false')
os.environ.setdefault('ENABLE_BACKEND_ACCESS_CONTROL', 'false')
os.environ.setdefault('COGNEE_DISABLE_TELEMETRY', 'true')
os.environ.setdefault('COGNEE_SKIP_CONNECTION_TEST', 'true')

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import uvicorn

# ── Pydantic Models ────────────────────────────────────────────

class ForgetRequest(BaseModel):
    """Unified deletion request mirroring cognee.forget() parameters.

    Supported combinations:
      - dataset=NAME                        → delete entire dataset
      - dataset=NAME + data_id=UUID         → delete single data item
      - dataset=NAME + memory_only=True     → clear graph/vector only, preserve raw data
      - everything=True                     → delete ALL user data (use carefully)
    """
    dataset: str | None = None
    data_id: str | None = None
    everything: bool = False
    memory_only: bool = False

class ImproveRequest(BaseModel):
    """Trigger cognee.improve() to self-derive cross-document relationships.

    If dataset is not specified, improves the default main_dataset.
    """
    dataset: str | None = "main_dataset"

class ForgetResponse(BaseModel):
    success: bool = True
    message: str = ""
    summary: dict = {}

class ImproveResponse(BaseModel):
    success: bool = True
    message: str = ""
    summary: dict = {}

# ── v1.0 API request/response models ───────────────────────────

class RememberRequest(BaseModel):
    """v1.0 remember() — store data into permanent graph or session cache.

    Without session_id: writes to permanent graph (equivalent to add + cognify + improve).
    With session_id: writes to session cache (fast, no entity extraction).
    """
    content: str
    dataset_name: str = "main_dataset"
    session_id: str | None = None
    self_improvement: bool = True
    run_in_background: bool = False

class RecallRequest(BaseModel):
    """v1.0 recall() — auto-routed retrieval with session awareness.

    only_context=True returns retrieved context only (no LLM answer generation),
    matching the behavior of legacy search() for pipeline integration.
    """
    query: str
    top_k: int = Field(default=5, ge=1, le=50)
    session_id: str | None = None
    datasets: list[str] | None = None
    only_context: bool = True

class RecallResultItem(BaseModel):
    text: str = ""
    score: float = 0.0
    source: str = "graph"

class RecallResponse(BaseModel):
    results: list[RecallResultItem] = []
    status: str = "success"

class AddResponse(BaseModel):
    id: str = ""
    success: bool = True
    message: str = ""

class HealthResponse(BaseModel):
    status: str = "ok"
    version: str = ""
    engine: str = "cognee"

class GraphNode(BaseModel):
    id: str
    label: str = ""
    type: str = "entity"
    properties: dict = {}
    position: dict = {}

class GraphEdge(BaseModel):
    id: str = ""
    source: str
    target: str
    label: str = ""
    type: str = "relationship"

class GraphResponse(BaseModel):
    nodes: list[GraphNode] = []
    edges: list[GraphEdge] = []
    status: str = "success"
    total_nodes: int = 0
    total_edges: int = 0

class DatasetInfo(BaseModel):
    id: str
    name: str = ""
    data_count: int = 0
    created_at: str = ""

class DatasetsResponse(BaseModel):
    datasets: list[DatasetInfo] = []
    status: str = "success"

# ── FastAPI App ────────────────────────────────────────────────

from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Initializing cognee...")
    await get_cognee()
    logger.info("Cognee initialized successfully")
    yield

app = FastAPI(
    title="Cognee Memory API",
    description="REST API for KChat's Cognee integration",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

logger = logging.getLogger("cognee-api")

# ── Lazy cognee import ─────────────────────────────────────────
# cognee has slow module-level init, so we import lazily

async def get_cognee():
    import cognee
    return cognee

# ── Routes ─────────────────────────────────────────────────────

@app.get("/health", response_model=HealthResponse)
async def health():
    """Health check endpoint."""
    try:
        cognee = await get_cognee()
        return HealthResponse(
            status="ok",
            version=getattr(cognee, '__version__', 'unknown'),
        )
    except Exception as e:
        return HealthResponse(status=f"error: {e}", version="unknown")

@app.get("/graph", response_model=GraphResponse)
async def get_graph():
    """Get knowledge graph nodes and edges for frontend visualization.

    Queries the graph engine directly to retrieve entities (nodes) and
    relationships (edges) extracted by Cognee's cognify pipeline.
    """
    try:
        from cognee.infrastructure.databases.graph import get_graph_engine
        from cognee.modules.data.methods import get_authorized_existing_datasets
        from cognee.modules.users.methods import get_default_user
        from cognee.context_global_variables import set_database_global_context_variables

        user = await get_default_user()
        dataset = await get_authorized_existing_datasets(["main_dataset"], "read", user)

        async with set_database_global_context_variables(
            dataset[0].id if dataset else None,
            dataset[0].owner_id if dataset else None,
        ):
            graph_engine = await get_graph_engine()
            raw_nodes, raw_edges = await graph_engine.get_graph_data()

            nodes = []
            for n in raw_nodes:
                raw_id = n[0] if isinstance(n, (tuple, list)) else getattr(n, 'id', None)
                if raw_id is None:
                    continue
                nid = str(raw_id)
                if nid == 'null':
                    continue
                props = n[1] if isinstance(n, (tuple, list)) and len(n) > 1 else getattr(n, 'properties', {})
                if not isinstance(props, dict):
                    props = dict(props) if props else {}
                label = str(props.get('name', props.get('label', nid)))[:60]
                ntype = str(props.get('type', props.get('entity_type', 'entity')))[:30]
                clean_props = {k: str(v)[:200] for k, v in props.items()
                               if k in ('description', 'created_at', 'version', 'feedback_weight')}
                nodes.append(GraphNode(id=nid, label=label, type=ntype, properties=clean_props))

            valid_node_ids = {n.id for n in nodes}
            edges = []
            skipped = 0
            for e in raw_edges:
                raw_src = e[0] if isinstance(e, (tuple, list)) else getattr(e, 'source', None)
                raw_tgt = e[1] if isinstance(e, (tuple, list)) and len(e) > 1 else getattr(e, 'target', None)
                if raw_src is None or raw_tgt is None:
                    skipped += 1
                    continue
                src = str(raw_src)
                tgt = str(raw_tgt)
                if src == 'null' or tgt == 'null' or src not in valid_node_ids or tgt not in valid_node_ids:
                    skipped += 1
                    continue
                rel = str(e[2]) if isinstance(e, (tuple, list)) and len(e) > 2 else str(getattr(e, 'relation', ''))
                eprops = e[3] if isinstance(e, (tuple, list)) and len(e) > 3 else getattr(e, 'properties', {})
                if not isinstance(eprops, dict):
                    eprops = dict(eprops) if eprops else {}
                eid = f"{src}-{rel}-{tgt}"
                edges.append(GraphEdge(id=eid, source=src, target=tgt, label=rel[:40], type=rel))
            if skipped:
                logger.warning(f"Skipped {skipped} invalid edges (null or missing node refs)")

            logger.info(f"Graph retrieved: {len(nodes)} nodes, {len(edges)} edges")
            return GraphResponse(
                nodes=nodes,
                edges=edges,
                total_nodes=len(nodes),
                total_edges=len(edges),
            )

    except Exception as e:
        logger.error(f"get_graph failed: {e}")
        return GraphResponse(status=f"error: {e}")

@app.get("/datasets", response_model=DatasetsResponse)
async def list_datasets():
    """List all datasets with their data counts."""
    try:
        cognee = await get_cognee()
        datasets = await cognee.datasets.list_datasets()

        result = []
        for ds in (datasets or []):
            ds_id = str(getattr(ds, 'id', ''))
            count = 0
            try:
                data_items = await cognee.datasets.list_data(dataset_id=getattr(ds, 'id'))
                count = len(data_items) if data_items else 0
            except Exception:
                pass

            result.append(DatasetInfo(
                id=ds_id,
                name=str(getattr(ds, 'name', ds_id)),
                data_count=count,
                created_at=str(getattr(ds, 'created_at', '')),
            ))

        return DatasetsResponse(datasets=result)
    except Exception as e:
        logger.error(f"list_datasets failed: {e}")
        return DatasetsResponse(status=f"error: {e}")

@app.post("/forget", response_model=ForgetResponse)
async def forget_content(request: ForgetRequest):
    """Unified delete endpoint mirroring cognee.forget().

    Delete a dataset, a single data item, or everything.
    """
    try:
        cognee = await get_cognee()
        forget_kwargs = {}
        if request.everything:
            forget_kwargs["everything"] = True
        else:
            if request.dataset:
                forget_kwargs["dataset"] = request.dataset
            if request.data_id:
                # Convert string to UUID if possible; cognee expects UUID
                try:
                    from uuid import UUID
                    forget_kwargs["data_id"] = UUID(request.data_id)
                except Exception:
                    forget_kwargs["data_id"] = request.data_id
            if request.memory_only:
                forget_kwargs["memory_only"] = True

        if not forget_kwargs:
            raise HTTPException(status_code=400,
                detail="Must provide at least one of: dataset, data_id(+dataset), everything")

        summary = await cognee.forget(**forget_kwargs)
        logger.info(f"forget completed with: {forget_kwargs} → {summary}")

        msg_parts = []
        if "datasets_removed" in summary:
            msg_parts.append(f"{summary['datasets_removed']} dataset(s) removed")
        if "data_id" in summary:
            msg_parts.append(f"data item {summary['data_id']} removed")
        message = "; ".join(msg_parts) if msg_parts else "forget completed"

        # Serialize summary values to strings where needed
        safe_summary = {}
        if isinstance(summary, dict):
            for k, v in summary.items():
                try:
                    safe_summary[k] = str(v) if not isinstance(v, (str, int, float, bool, list, dict, type(None))) else v
                except Exception:
                    safe_summary[k] = str(v)

        return ForgetResponse(success=True, message=message, summary=safe_summary)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"forget failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/improve", response_model=ImproveResponse)
async def improve_graph(request: ImproveRequest):
    """Trigger cognee.improve() to self-derive cross-document relationships.

    This bridges session memory into permanent memory and enriches the
    graph by deriving new edges across existing entities.
    """
    try:
        cognee = await get_cognee()
        kwargs = {}
        if request.dataset:
            kwargs["dataset"] = request.dataset

        try:
            result = await cognee.improve(**kwargs)
        except TypeError:
            # Older cognee may not accept dataset kwarg; retry with no args
            logger.warning("improve(dataset=...) not supported, retrying with no args")
            result = await cognee.improve()

        logger.info(f"improve completed for dataset={request.dataset}")

        safe_summary = {}
        if isinstance(result, dict):
            for k, v in result.items():
                try:
                    safe_summary[k] = str(v) if not isinstance(v, (str, int, float, bool, list, dict, type(None))) else v
                except Exception:
                    safe_summary[k] = str(v)
        elif result is not None:
            safe_summary["result"] = str(result)

        return ImproveResponse(
            success=True,
            message=f"Graph improved successfully for dataset={request.dataset or 'default'}",
            summary=safe_summary,
        )
    except Exception as e:
        logger.error(f"improve failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# ── v1.0 API: /remember and /recall ────────────────────────────

@app.post("/remember", response_model=AddResponse)
async def remember_content(request: RememberRequest):
    """v1.0 remember() — store data into permanent graph or session cache.

    Without session_id: full pipeline (add + cognify + improve).
    With session_id: fast session cache write (no entity extraction).
    self_improvement=True (default) triggers background improve() automatically.
    """
    try:
        cognee = await get_cognee()
        logger.info(f"remember: content_len={len(request.content)}, "
                    f"dataset={request.dataset_name}, session={request.session_id}, "
                    f"self_improvement={request.self_improvement}")

        result = await cognee.remember(
            request.content,
            dataset_name=request.dataset_name,
            session_id=request.session_id,
            self_improvement=request.self_improvement,
            run_in_background=request.run_in_background,
        )

        # Extract a usable id from the result if available
        result_id = ""
        if result is not None:
            if isinstance(result, str):
                result_id = result
            elif hasattr(result, 'id'):
                result_id = str(result.id)
            elif isinstance(result, dict):
                result_id = str(result.get('id', ''))

        return AddResponse(
            id=result_id,
            success=True,
            message=f"Remembered successfully (session={'yes' if request.session_id else 'no'}, "
                    f"self_improvement={request.self_improvement})",
        )
    except Exception as e:
        logger.error(f"remember failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/recall", response_model=RecallResponse)
async def recall_memories(request: RecallRequest):
    """v1.0 recall() — auto-routed retrieval with session awareness.

    Uses cognee's intelligent query router to pick the best retrieval strategy
    (graph completion, RAG, chunks, summaries, etc.) based on the query.

    only_context=True returns retrieved context only (no LLM answer generation),
    making it suitable for pipeline integration where the caller has its own LLM.
    """
    try:
        cognee = await get_cognee()
        logger.info(f"recall: query='{request.query[:80]}', top_k={request.top_k}, "
                    f"session={request.session_id}, only_context={request.only_context}")

        kwargs = {
            "top_k": request.top_k,
            "only_context": request.only_context,
            "auto_route": True,
        }
        if request.session_id:
            kwargs["session_id"] = request.session_id
        if request.datasets:
            kwargs["datasets"] = request.datasets

        results = await cognee.recall(request.query, **kwargs)

        items = []
        if results:
            for r in results[:request.top_k]:
                # recall() returns typed objects with varying fields depending on source.
                # Normalize to {text, score, source} for the Java client.
                text = ""
                score = 0.0
                source = "graph"

                # Try common field names for text content
                for attr in ('text', 'content', 'answer', 'response', 'summary'):
                    val = getattr(r, attr, None) if not isinstance(r, dict) else r.get(attr)
                    if val and str(val).strip():
                        text = str(val)
                        break

                # If no text field found, stringify the object
                if not text:
                    text = str(r)

                # Try common field names for score
                for attr in ('score', 'similarity', 'relevance', 'confidence'):
                    val = getattr(r, attr, None) if not isinstance(r, dict) else r.get(attr)
                    if val is not None and isinstance(val, (int, float)):
                        score = float(val)
                        break

                # Extract source tag
                src_val = getattr(r, 'source', None) if not isinstance(r, dict) else r.get('source')
                if src_val:
                    source = str(src_val)

                items.append(RecallResultItem(
                    text=text[:2000],  # Cap at 2000 chars to keep payload reasonable
                    score=score if score > 0 else 0.5,  # Default neutral score if not provided
                    source=source,
                ))

        logger.info(f"recall returned {len(items)} results (sources: "
                    f"{[i.source for i in items]})")
        return RecallResponse(results=items, status="success")

    except Exception as e:
        logger.error(f"recall failed: {e}")
        return RecallResponse(results=[], status=f"error: {e}")


# ── Main ───────────────────────────────────────────────────────

if __name__ == "__main__":
    port = int(os.environ.get("COGNEE_PORT", "8000"))

    # ── 端口占用检测 ─────────────────────────────────────
    # 如果端口已被占用（前一个实例未正常退出），尝试 kill 旧进程
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.bind(("0.0.0.0", port))
    except OSError:
        import subprocess
        logger = logging.getLogger("cognee-api")
        logger.warning(f"Port {port} is already in use. Attempting to free it...")
        try:
            result = subprocess.run(
                ["lsof", "-ti", f":{port}"],
                capture_output=True, text=True
            )
            for pid_str in result.stdout.strip().split("\n"):
                if pid_str:
                    os.kill(int(pid_str), signal.SIGTERM)
                    logger.info(f"Killed existing process on port {port} (PID {pid_str})")
        except Exception as e:
            logger.warning(f"Could not free port {port}: {e}")
    finally:
        sock.close()

    # ── 资源清理：避免 multiprocessing semaphore 泄漏 ─────
    # cognee 内部使用 multiprocessing，不干净退出会遗留 semaphore 对象
    _shutdown_done = False

    def _cleanup_resources():
        global _shutdown_done
        if _shutdown_done:
            return
        _shutdown_done = True
        import multiprocessing
        try:
            # Join 所有活跃的子进程，避免资源泄漏
            for p in multiprocessing.active_children():
                p.terminate()
                p.join(timeout=3)
            # 清理 resource tracker 注册的 semaphore
            if hasattr(multiprocessing, 'resource_tracker'):
                try:
                    multiprocessing.resource_tracker._resource_tracker._stop = True
                except Exception:
                    pass
        except Exception:
            pass

    # 注册退出清理——覆盖正常退出、Ctrl+C、kill 三种场景
    atexit.register(_cleanup_resources)
    signal.signal(signal.SIGTERM, lambda _s, _f: (_cleanup_resources(), sys.exit(0)))
    signal.signal(signal.SIGINT,  lambda _s, _f: (_cleanup_resources(), sys.exit(0)))

    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] %(levelname)s %(name)s - %(message)s",
    )
    logger = logging.getLogger("cognee-api")
    logger.info(f"Starting cognee API server on port {port}...")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")
