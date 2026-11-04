#!/usr/bin/env python
"""
Calibrate the Smart Search confidence floor against this collection.

The question is not "what cosine looks respectable" but "what value separates a
question this library can answer from one it cannot". So two sets are run:

  * answerable  - questions whose answer really is in an indexed book;
  * unanswerable - questions on subjects the collection does not hold at all.

For every query the raw best cosine (the number the floor compares against) is
read back from the backend log, which logs it per query. A candidate floor is
scored by how many answerable questions it keeps and how many unanswerable ones
it rejects; the report names the range where both are satisfied.

The floor is a property of the embedding model and the corpus, not of a taste
for high numbers, which is why it is measured instead of guessed.

Usage:
    ASTRALIB_TOKEN=... python catalogue/calibrate_threshold.py \
        --log ../backend-run7.log
"""

import argparse
import os
import re
import statistics
import sys
import time
import urllib.parse
import urllib.request

BASE = os.environ.get("ASTRALIB_API", "http://127.0.0.1:8082")

ANSWERABLE = [
    "How does a while loop work?",
    "What is inheritance in object-oriented programming?",
    "How do I declare a pointer in C and take the address of a variable?",
    "What is a vector in C++?",
    "How do I define a Python function with default arguments?",
    "What is a closure in JavaScript?",
    "How does Rust ownership and borrowing work?",
    "Explain stacks and queues",
    "What is recursion and when should I use it?",
    "How is a linked list stored in memory?",
    "How does virtual memory paging work?",
    "What is a deadlock in an operating system?",
    "How does the scheduler decide which process runs next?",
    "How does TCP establish a connection?",
    "Why does Elizabeth Bennet dislike Mr Darcy?",
    "How does Holmes solve the case of the hound?",
    "What does Sun Tzu say about deception in war?",
    "I keep forgetting how memory works in C",
    "What is the difference between a process and a thread?",
    "Why do my C++ objects get copied when I pass them to a function?",
]

UNANSWERABLE = [
    "How do I configure a Kubernetes ingress controller?",
    "What did Wittgenstein mean by language games?",
    "How do I bake sourdough bread with a starter?",
    "How were Roman aqueducts built?",
    "What is the treatment for type 2 diabetes?",
    "Who won the 1994 football World Cup?",
    "How do I prune an apple tree in winter?",
    "What caused the collapse of the Bronze Age palaces?",
    "How do I set up a Kubernetes operator?",
    "What is the best way to learn the violin as an adult?",
]

LOG_LINE = re.compile(
    r"Smart passages for '(.+?)': \d+ candidates, best semantic ([\d.]+)"
)

CANDIDATE_FLOORS = [0.26, 0.30, 0.34, 0.38, 0.40, 0.42, 0.44, 0.46, 0.48, 0.50, 0.55]


def token():
    existing = os.environ.get("ASTRALIB_TOKEN")
    if existing:
        return existing
    email = os.environ.get("ASTRALIB_EMAIL")
    password = os.environ.get("ASTRALIB_PASSWORD")
    if not email or not password:
        sys.exit("set ASTRALIB_TOKEN or ASTRALIB_EMAIL/ASTRALIB_PASSWORD")
    body = __import__("json").dumps({"email": email, "password": password}).encode()
    request = urllib.request.Request(
        BASE + "/api/auth/login", data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return __import__("json").load(response).get("token", "")


def ask(token_value, query):
    params = urllib.parse.urlencode({"query": query, "topK": 3})
    request = urllib.request.Request(
        "%s/api/search/smart?%s" % (BASE, params),
        headers={"Authorization": "Bearer " + token_value},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return __import__("json").load(response)


def log_scores(path):
    scores = {}
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = LOG_LINE.search(line)
            if match:
                scores[match.group(1)] = float(match.group(2))
    return scores


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", required=True)
    parser.add_argument("--wait", type=float, default=1.0)
    args = parser.parse_args()

    token_value = token()

    rows = []

    for label, queries in (
        ("answerable", ANSWERABLE),
        ("unanswerable", UNANSWERABLE),
    ):
        for query in queries:
            payload = ask(token_value, query)
            rows.append(
                {
                    "label": label,
                    "query": query,
                    "passages": len(payload.get("passages") or []),
                }
            )
            print("%-13s %-60s -> %d passages" % (label, query[:60], rows[-1]["passages"]))
            time.sleep(args.wait)

    scores = log_scores(args.log)

    for row in rows:
        row["best_semantic"] = scores.get(row["query"])

    missing = [row for row in rows if row["best_semantic"] is None]

    if missing:
        print(
            "\n%d query result(s) had no score in the log (was the backend "
            "restarted mid-run?)" % len(missing)
        )

    usable = [row for row in rows if row["best_semantic"] is not None]

    for label in ("answerable", "unanswerable"):
        values = sorted(
            row["best_semantic"] for row in usable if row["label"] == label
        )
        if not values:
            continue
        print(
            "\n%s: min %.3f  p25 %.3f  median %.3f  p75 %.3f  max %.3f"
            % (
                label,
                values[0],
                statistics.quantiles(values, n=4)[0] if len(values) > 3 else values[0],
                statistics.median(values),
                statistics.quantiles(values, n=4)[2] if len(values) > 3 else values[-1],
                values[-1],
            )
        )

    print("\nCandidate floor | answerable kept | unanswerable rejected")
    best = None
    for floor in CANDIDATE_FLOORS:
        kept = sum(
            1 for row in usable
            if row["label"] == "answerable" and row["best_semantic"] >= floor
        )
        rejected = sum(
            1 for row in usable
            if row["label"] == "unanswerable" and row["best_semantic"] < floor
        )
        total_ok = kept + rejected
        print(
            "  %.2f          | %2d/%2d            | %2d/%2d"
            % (
                floor,
                kept,
                sum(1 for r in usable if r["label"] == "answerable"),
                rejected,
                sum(1 for r in usable if r["label"] == "unanswerable"),
            )
        )
        if best is None or total_ok > best[1]:
            best = (floor, total_ok)

    if best:
        print("\nBest separation at floor %.2f (%d/%d queries correct)" % (
            best[0], best[1], len(usable)
        ))

    return 0


if __name__ == "__main__":
    sys.exit(main())
