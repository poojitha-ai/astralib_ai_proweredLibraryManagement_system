import argparse
import csv
import json
import statistics
import time
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data" / "processed" / "documents" / "documents_full_rag.json"
OUT = ROOT / "experiments" / "results"

# Controlled query set. Each query has a known target document from the project corpus.
# These are intentionally simple, document-specific questions/phrases so that relevance
# can be evaluated without inventing a separate external dataset.
QUERIES = [
    {"id": "q01", "query": "What is the main objective and motivation of this work?", "document_id": "DOC-ARXIV-2607.28628v1"},
    {"id": "q02", "query": "What method or approach is proposed in this paper?", "document_id": "DOC-ARXIV-2607.28628v1"},
    {"id": "q03", "query": "What problem does this research attempt to solve?", "document_id": "DOC-ARXIV-2607.28271v1"},
    {"id": "q04", "query": "What is the key contribution of the proposed method?", "document_id": "DOC-ARXIV-2607.28272v1"},
    {"id": "q05", "query": "What experimental setup or evaluation is used?", "document_id": "DOC-ARXIV-2607.28274v1"},
    {"id": "q06", "query": "What are the main findings reported by the authors?", "document_id": "DOC-ARXIV-2607.28282v1"},
    {"id": "q07", "query": "What limitations or future work are discussed?", "document_id": "DOC-ARXIV-2607.28287v1"},
    {"id": "q08", "query": "What dataset is used in this research?", "document_id": "DOC-ARXIV-2607.28292v1"},
    {"id": "q09", "query": "What model architecture or system design is described?", "document_id": "DOC-ARXIV-2607.28293v1"},
    {"id": "q10", "query": "How does the proposed technique improve the baseline?", "document_id": "DOC-ARXIV-2607.28300v1"},
    {"id": "q11", "query": "What evaluation metrics are reported?", "document_id": "DOC-ARXIV-2607.28301v1"},
    {"id": "q12", "query": "What are the important results and conclusions?", "document_id": "DOC-ARXIV-2607.28304v1"},
    {"id": "q13", "query": "What is the proposed algorithm or procedure?", "document_id": "DOC-ARXIV-2607.28306v1"},
    {"id": "q14", "query": "What task does the system perform?", "document_id": "DOC-ARXIV-2607.28307v1"},
    {"id": "q15", "query": "What training data and methodology are used?", "document_id": "DOC-ARXIV-2607.28308v1"},
    {"id": "q16", "query": "What is the central research question?", "document_id": "DOC-ARXIV-2607.28311v1"},
    {"id": "q17", "query": "What baseline methods are compared?", "document_id": "DOC-ARXIV-2607.28312v1"},
    {"id": "q18", "query": "What does the ablation or component analysis show?", "document_id": "DOC-ARXIV-2607.28317v1"},
    {"id": "q19", "query": "What is the main technical novelty?", "document_id": "DOC-ARXIV-2607.28318v1"},
    {"id": "q20", "query": "What results are obtained on the reported benchmark?", "document_id": "DOC-ARXIV-2607.28319v1"},
    {"id": "q21", "query": "How is the proposed system evaluated?", "document_id": "DOC-ARXIV-2607.28324v1"},
    {"id": "q22", "query": "What are the main components of the proposed architecture?", "document_id": "DOC-ARXIV-2607.28330v1"},
    {"id": "q23", "query": "What challenge motivates the proposed approach?", "document_id": "DOC-ARXIV-2607.28331v1"},
    {"id": "q24", "query": "What conclusions does the paper draw from its experiments?", "document_id": "DOC-ARXIV-2607.28336v1"},
    {"id": "q25", "query": "What data preprocessing is performed?", "document_id": "DOC-ARXIV-2607.28338v1"},
    {"id": "q26", "query": "What performance improvement is reported?", "document_id": "DOC-ARXIV-2607.28344v1"},
    {"id": "q27", "query": "What are the stated limitations of the approach?", "document_id": "DOC-ARXIV-2607.28347v1"},
    {"id": "q28", "query": "What future directions are proposed?", "document_id": "DOC-ARXIV-2607.28351v1"},
    {"id": "q29", "query": "What is the main contribution of this research?", "document_id": "DOC-ARXIV-2607.28359v1"},
    {"id": "q30", "query": "What is the overall method and evaluation strategy?", "document_id": "DOC-ARXIV-2607.28362v1"},
]


def percentile(values, p):
    if not values:
        return None
    values = sorted(values)
    if len(values) == 1:
        return values[0]
    k = (len(values) - 1) * p / 100
    f = int(k)
    c = min(f + 1, len(values) - 1)
    return values[f] + (values[c] - values[f]) * (k - f)


def extract_results(payload):
    if isinstance(payload, list):
        return payload
    for key in ("passages", "results", "data", "items"):
        if isinstance(payload, dict) and isinstance(payload.get(key), list):
            return payload[key]
    return []


def result_doc_id(item):
    return item.get("documentId") or item.get("document_id") or item.get("docId")


def evaluate(results, target, k):
    docs = [result_doc_id(x) for x in results[:k]]
    hit_positions = [i + 1 for i, d in enumerate(docs) if d == target]
    precision = 1 / k if hit_positions else 0.0
    rr = 1 / hit_positions[0] if hit_positions else 0.0
    return precision, rr, bool(hit_positions)


def request(session, method, url, token, **kwargs):
    headers = kwargs.pop("headers", {})
    headers["Authorization"] = f"Bearer {token}"
    headers.setdefault("Accept", "application/json")
    start = time.perf_counter()
    r = session.request(method, url, headers=headers, timeout=120, **kwargs)
    elapsed = (time.perf_counter() - start) * 1000
    r.raise_for_status()
    return elapsed, r.json()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default="http://localhost:8082")
    ap.add_argument("--token", required=True)
    ap.add_argument("--top-k", type=int, default=5)
    args = ap.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    corpus = json.loads(DATA.read_text(encoding="utf-8"))
    chunk_count = sum(len(d.get("chunks", [])) for d in corpus)

    session = requests.Session()
    rows = []

    for q in QUERIES:
        common = {"query": q["query"], "topK": args.top_k}
        for mode, path in (("semantic", "/api/search/semantic"), ("hybrid", "/api/search/hybrid")):
            ms, payload = request(session, "GET", args.base_url.rstrip("/") + path, args.token, params=common)
            results = extract_results(payload)
            p1, rr, hit = evaluate(results, q["document_id"], args.top_k)
            p3, _, _ = evaluate(results, q["document_id"], 3)
            p5, _, _ = evaluate(results, q["document_id"], 5)
            rows.append({"query_id": q["id"], "mode": mode, "latency_ms": ms, "p_at_1": p1, "p_at_3": p3, "p_at_5": p5, "mrr": rr, "hit": hit, "returned": len(results)})

        # Global RAG latency only; answer-quality scoring is intentionally not automated.
        ms, payload = request(session, "POST", args.base_url.rstrip("/") + "/api/rag/ask", args.token, json={"question": q["query"]})
        rows.append({"query_id": q["id"], "mode": "global_rag", "latency_ms": ms, "p_at_1": None, "p_at_3": None, "p_at_5": None, "mrr": None, "hit": None, "returned": None})

        ms, payload = request(session, "POST", args.base_url.rstrip("/") + f"/api/rag/documents/{q['document_id']}/ask", args.token, json={"question": q["query"]})
        rows.append({"query_id": q["id"], "mode": "document_rag", "latency_ms": ms, "p_at_1": None, "p_at_3": None, "p_at_5": None, "mrr": None, "hit": None, "returned": None})

    (OUT / "retrieval_results.json").write_text(json.dumps({"corpus_documents": len(corpus), "corpus_chunks": chunk_count, "queries": QUERIES, "results": rows}, indent=2), encoding="utf-8")

    summary = []
    for mode in sorted(set(r["mode"] for r in rows)):
        rr = [r for r in rows if r["mode"] == mode]
        lat = [r["latency_ms"] for r in rr]
        summary.append({
            "mode": mode,
            "n": len(rr),
            "mean_latency_ms": statistics.mean(lat),
            "p50_latency_ms": percentile(lat, 50),
            "p95_latency_ms": percentile(lat, 95),
            "p99_latency_ms": percentile(lat, 99),
            "P@1": statistics.mean([r["p_at_1"] for r in rr if r["p_at_1"] is not None]) if any(r["p_at_1"] is not None for r in rr) else None,
            "P@3": statistics.mean([r["p_at_3"] for r in rr if r["p_at_3"] is not None]) if any(r["p_at_3"] is not None for r in rr) else None,
            "P@5": statistics.mean([r["p_at_5"] for r in rr if r["p_at_5"] is not None]) if any(r["p_at_5"] is not None for r in rr) else None,
            "MRR": statistics.mean([r["mrr"] for r in rr if r["mrr"] is not None]) if any(r["mrr"] is not None for r in rr) else None,
            "hit_rate": statistics.mean([1 if r["hit"] else 0 for r in rr if r["hit"] is not None]) if any(r["hit"] is not None for r in rr) else None,
        })

    with (OUT / "retrieval_summary.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=summary[0].keys())
        w.writeheader(); w.writerows(summary)

    with (OUT / "latency_summary.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["mode", "n", "mean_latency_ms", "p50_latency_ms", "p95_latency_ms", "p99_latency_ms"])
        w.writeheader()
        for x in summary:
            w.writerow({k: x[k] for k in w.fieldnames})

    print(f"Corpus: {len(corpus)} documents / {chunk_count} chunks")
    print(f"Queries: {len(QUERIES)}")
    print(f"Results written to: {OUT}")


if __name__ == "__main__":
    main()
