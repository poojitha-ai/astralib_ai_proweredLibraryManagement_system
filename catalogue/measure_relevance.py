#!/usr/bin/env python
"""Measure retrieval quality, not just whether a query returned something.

Presence is the wrong success criterion: five unrelated passages satisfy it.
This script scores the ranked document list from ``/api/search/semantic``
against ``catalogue/relevance_dataset.json``, a small manually reviewed set of
graded judgements, and reports:

  * Precision@5   - of the five slots, how many are relevant
  * Recall@5      - of the documents that could answer, how many were found
  * MRR           - 1 / rank of the first relevant document
  * nDCG@5        - graded ranking quality (2 = answers, 1 = supporting)
  * FP rate       - share of unsupported queries that still returned a passage

Recommendation queries are checked separately: order and readability, because
a book list is not a ranked passage list.

Usage:
    python catalogue/measure_relevance.py --label before
    ASTRALIB_TOKEN=... python catalogue/measure_relevance.py --label after --json out.json
"""

import argparse
import json
import math
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("ASTRALIB_API", "http://127.0.0.1:8082")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASET = os.path.join(ROOT, "catalogue", "relevance_dataset.json")
TOP_K = 5


def login():
    existing = os.environ.get("ASTRALIB_TOKEN")
    if existing:
        return existing

    email = os.environ.get("ASTRALIB_EMAIL")
    password = os.environ.get("ASTRALIB_PASSWORD")
    if not email or not password:
        sys.exit("set ASTRALIB_TOKEN or ASTRALIB_EMAIL/ASTRALIB_PASSWORD")

    body = json.dumps({"email": email, "password": password}).encode()
    request = urllib.request.Request(
        BASE + "/api/auth/login",
        data=body,
        headers={"Content-Type": "application/json"},
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response).get("token", "")


def semantic(token, query, top_k=TOP_K):
    params = urllib.parse.urlencode({"query": query, "topK": top_k})
    request = urllib.request.Request(
        "%s/api/search/semantic?%s" % (BASE, params),
        headers={"Authorization": "Bearer " + token},
    )

    started = time.time()

    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        return {"error": "HTTP %d" % error.code, "ms": 0, "payload": {}}
    except Exception as error:  # noqa: BLE001
        return {"error": str(error), "ms": 0, "payload": {}}

    return {
        "error": None,
        "ms": int((time.time() - started) * 1000),
        "payload": payload,
    }


def ranked_docs(payload):
    """Documents in the order the user would see them."""

    docs = []
    for passage in payload.get("passages") or []:
        doc = passage.get("documentId")
        if doc and doc not in docs:
            docs.append(doc)
    return docs


def precision_at_k(docs, expected, k):
    if not docs:
        return 0.0
    hits = sum(1 for d in docs[:k] if d in expected)
    return hits / float(k)


def recall_at_k(docs, expected, k):
    if not expected:
        return 0.0
    found = {d for d in docs[:k] if d in expected}
    return len(found) / float(len(expected))


def reciprocal_rank(docs, expected):
    for index, doc in enumerate(docs, start=1):
        if doc in expected:
            return 1.0 / index
    return 0.0


def ndcg_at_k(docs, expected, k):
    def gain(doc):
        grade = expected.get(doc)
        return (2 ** grade) - 1 if grade else 0

    dcg = 0.0
    for index, doc in enumerate(docs[:k], start=1):
        dcg += gain(doc) / math.log2(index + 1)

    ideal = sorted(expected.values(), reverse=True)[:k]
    idcg = 0.0
    for index, grade in enumerate(ideal, start=1):
        idcg += ((2 ** grade) - 1) / math.log2(index + 1)

    return dcg / idcg if idcg else 0.0


def measure_supported(token, entry):
    result = semantic(token, entry["query"], TOP_K)
    payload = result["payload"] or {}
    docs = ranked_docs(payload)
    expected = entry["expected"]

    grades = [expected.get(doc, 0) for doc in docs[:TOP_K]]

    return {
        "id": entry["id"],
        "query": entry["query"],
        "error": result["error"],
        "latency_ms": result["ms"],
        "intent": (payload.get("understanding") or {}).get("intent"),
        "best_cosine": (payload.get("confidence") or {}).get("bestPassageScore"),
        "ranked": [
            {"doc": doc, "grade": expected.get(doc, 0)}
            for doc in docs[:TOP_K]
        ],
        "returned": len(docs),
        "p_at_5": round(precision_at_k(docs, expected, TOP_K), 3),
        "r_at_5": round(recall_at_k(docs, expected, TOP_K), 3),
        "mrr": round(reciprocal_rank(docs, expected), 3),
        "ndcg_at_5": round(ndcg_at_k(docs, expected, TOP_K), 3),
        "weak_evidence": bool(docs) and not any(grades),
    }


def measure_recommendation(token, entry):
    result = semantic(token, entry["query"], 6)
    payload = result["payload"] or {}
    books = payload.get("recommendations") or []

    first = books[0] if books else None

    return {
        "id": entry["id"],
        "query": entry["query"],
        "error": result["error"],
        "books": [
            {
                "title": book.get("title"),
                "level": book.get("level"),
                "language": book.get("language"),
                "readable": book.get("readable"),
            }
            for book in books
        ],
        "first": first,
        "note": entry.get("note"),
    }


def measure_unsupported(token, query):
    result = semantic(token, query, TOP_K)
    payload = result["payload"] or {}
    docs = ranked_docs(payload)

    return {
        "query": query,
        "error": result["error"],
        "intent": (payload.get("understanding") or {}).get("intent"),
        "returned": len(docs),
        "docs": docs,
        "best_cosine": (payload.get("confidence") or {}).get("bestPassageScore"),
        "false_positive": bool(docs),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", default="run")
    parser.add_argument("--json", default="")
    parser.add_argument(
        "--dataset",
        default=DATASET,
        help="alternate judgement file (used to re-score an earlier label)",
    )
    args = parser.parse_args()

    dataset = json.loads(open(args.dataset, encoding="utf-8").read())
    token = login()

    supported = [measure_supported(token, e) for e in dataset["supported"]]
    recommendations = [
        measure_recommendation(token, e) for e in dataset["recommendations"]
    ]
    unsupported = [measure_unsupported(token, q) for q in dataset["unsupported"]]

    def mean(rows, key):
        values = [r[key] for r in rows if r.get("error") is None]
        return round(sum(values) / len(values), 3) if values else 0.0

    fp_rate = (
        round(sum(1 for r in unsupported if r["false_positive"]) / len(unsupported), 3)
        if unsupported
        else 0.0
    )

    summary = {
        "label": args.label,
        "queries": len(supported),
        "p_at_5": mean(supported, "p_at_5"),
        "r_at_5": mean(supported, "r_at_5"),
        "mrr": mean(supported, "mrr"),
        "ndcg_at_5": mean(supported, "ndcg_at_5"),
        "weak_evidence": sum(1 for r in supported if r["weak_evidence"]),
        "unsupported_queries": len(unsupported),
        "unsupported_false_positive_rate": fp_rate,
    }

    print("\n=== %s ===" % args.label)
    print(
        "P@5 %.3f | R@5 %.3f | MRR %.3f | nDCG@5 %.3f | weak %d/%d | "
        "unsupported FP %.2f (%d/%d)"
        % (
            summary["p_at_5"],
            summary["r_at_5"],
            summary["mrr"],
            summary["ndcg_at_5"],
            summary["weak_evidence"],
            len(supported),
            fp_rate,
            sum(1 for r in unsupported if r["false_positive"]),
            len(unsupported),
        )
    )

    for row in supported:
        status = "ERR" if row["error"] else "ok"
        print(
            "  %-18s %-3s P5 %.2f R5 %.2f MRR %.2f nDCG %.2f  %s"
            % (
                row["id"],
                status,
                row["p_at_5"],
                row["r_at_5"],
                row["mrr"],
                row["ndcg_at_5"],
                " ".join(
                    "%s(%d)" % (r["doc"][:22], r["grade"]) for r in row["ranked"]
                ),
            )
        )

    print("\nRecommendations:")
    for row in recommendations:
        first = row["first"] or {}
        print(
            "  %-18s first=%s | %s | readable=%s"
            % (
                row["id"],
                (first.get("title") or "none")[:34],
                first.get("level"),
                first.get("readable"),
            )
        )

    print("\nUnsupported:")
    for row in unsupported:
        print(
            "  %-46s returned=%d fp=%s %s"
            % (
                row["query"][:46],
                row["returned"],
                "YES" if row["false_positive"] else "no",
                ",".join(row["docs"][:3]),
            )
        )

    report = {
        "summary": summary,
        "supported": supported,
        "recommendations": recommendations,
        "unsupported": unsupported,
    }

    out = args.json or os.path.join(
        ROOT, "catalogue", "relevance_%s.json" % args.label
    )

    with open(out, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2)

    print("\nwrote %s" % out)


if __name__ == "__main__":
    main()
