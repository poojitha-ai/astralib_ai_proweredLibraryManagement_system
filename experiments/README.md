# AstraLib Reproducible RAG Benchmark

This benchmark is designed to measure the **actual running AstraLib backend** rather than a surrogate implementation.

## Dataset

The benchmark uses the project-provided `data/processed/documents/documents_full_rag.json` corpus:

- 100 documents
- 700 pre-segmented chunks
- 7 chunks per document

## Requirements

- AstraLib backend running locally
- MySQL database populated and vector ingestion completed
- Gemini embedding configuration available to the backend
- Python 3.10+
- `requests` package (`pip install requests`)

## Run

1. Start AstraLib backend.
2. Make sure vector ingestion has completed.
3. Obtain a valid USER JWT for the API.
4. Run:

```powershell
python experiments/benchmark_rag.py --base-url http://localhost:8082 --token YOUR_JWT
```

If your backend uses port 8081, change `--base-url` accordingly.

The script writes:

- `experiments/results/retrieval_results.json`
- `experiments/results/retrieval_summary.csv`
- `experiments/results/latency_summary.csv`

## What it measures

- Semantic Search P@1, P@3, P@5
- Semantic Search MRR
- Hybrid Search P@1, P@3, P@5
- Hybrid Search MRR
- Semantic/Hybrid latency
- Global RAG latency
- Document-scoped RAG latency

## Important interpretation rule

These measurements are for the **actual AstraLib implementation**. They must not be copied into the manuscript until the run completes successfully and the raw result files are reviewed.

The benchmark currently uses the project's 100-document / 700-chunk corpus. It does not claim the manuscript's earlier 3,200-chunk corpus.
