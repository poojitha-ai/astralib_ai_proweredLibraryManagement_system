#!/usr/bin/env python
"""
Evaluate AstraLib's natural-language search against a fixed query set.

The same 30 queries are run before and after a retrieval change so the two
reports are directly comparable. Nothing is tuned to make a query pass: the
queries are ordinary things a reader would type, including vague ones, and
three questions the collection genuinely cannot answer.

For every query the report records:
  * detected intent (as reported by the API)
  * the expected book(s), or None when the collection holds no source
  * the retrieved books with their scores
  * the rank of the first acceptable expected book
  * whether the returned passage is real prose (a legacy metadata stub is not)
  * whether any unreadable/metadata-only record was offered as a source

Usage:
    ASTRALIB_TOKEN=... python catalogue/evaluate_search.py --label before
    ASTRALIB_TOKEN=... python catalogue/evaluate_search.py --label after --smart
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.environ.get("ASTRALIB_API", "http://127.0.0.1:8082")
TOP_K = 5

PY4E = "Python for Everybody: Exploring Data in Python 3"
THINK_PY = "Think Python (2nd Edition)"
PY_CRASH = "Python Crash Course"
THINK_JAVA = "Think Java: How to Think Like a Computer Scientist (2nd Edition)"
JJJ = "Java, Java, Java: Object-Oriented Problem Solving (3rd Edition)"
HEAD_FIRST_JAVA = "Head First Java"
C_BOOK = "C Programming"
MODERN_C = "Modern C (C23 Edition)"
EXPERT_C = "Expert C Programming"
THINK_CPP = "Think C++: How to Think Like a Computer Scientist"
BUSBEE = "Programming Fundamentals: A Modular Structured Approach Using C++"
ELOQUENT = "Eloquent JavaScript (3rd Edition)"
JS_WAY = "The JavaScript Way"
RUST = "The Rust Programming Language"
RUST_ADV = "Rust for Rustaceans"
PY_MASTERY = "Advanced Python Mastery"
OSC = "Operating System Concepts (9th Edition)"
PRIDE = "Pride and Prejudice"
HOUND = "The Hound of the Baskervilles"
ART_OF_WAR = "The Art of War"

# category: recommendation | concept | ml | literature | vague
QUERIES = [
    # --- beginner recommendations (metadata-driven) ---------------------
    dict(q="I'm new to programming, which Python book should I start with?",
         category="recommendation", expect=[PY4E, THINK_PY, PY_CRASH],
         want_language="Python", want_level="BEGINNER"),
    dict(q="Something easy for a complete beginner in Java",
         category="recommendation", expect=[THINK_JAVA, HEAD_FIRST_JAVA],
         want_language="Java", want_level="BEGINNER"),
    dict(q="Best book to learn C++ from scratch?",
         category="recommendation", expect=[BUSBEE, THINK_CPP],
         want_language="C++", want_level="BEGINNER"),
    dict(q="I already know the basics, recommend a more advanced Rust book",
         category="recommendation", expect=[RUST_ADV, RUST],
         want_language="Rust", want_level="ADVANCED"),
    dict(q="Easy introduction to JavaScript for someone who only knows HTML",
         category="recommendation", expect=[ELOQUENT, JS_WAY],
         want_language="JavaScript", want_level="BEGINNER"),
    dict(q="What should I read after learning basic C?",
         category="recommendation", expect=[MODERN_C, EXPERT_C, C_BOOK],
         want_language="C", want_level=None),
    dict(q="Which book teaches advanced Python metaprogramming and decorators?",
         category="recommendation", expect=[PY_MASTERY],
         want_language="Python", want_level="ADVANCED"),
    dict(q="I want a beginner friendly book about object oriented programming",
         category="recommendation", expect=[JJJ, THINK_JAVA, BUSBEE],
         want_language=None, want_level="BEGINNER"),

    # --- conceptual questions -------------------------------------------
    dict(q="How does a while loop work?",
         category="concept", expect=[C_BOOK, THINK_JAVA, JJJ, PY4E]),
    dict(q="What is inheritance in object-oriented programming?",
         category="concept", expect=[JJJ]),
    dict(q="How do I declare a pointer in C and take the address of a variable?",
         category="concept", expect=[C_BOOK, MODERN_C]),
    dict(q="What is a vector in C++?",
         category="concept", expect=[THINK_CPP, BUSBEE]),
    dict(q="How do I define a Python function with default arguments?",
         category="concept", expect=[THINK_PY, PY4E]),
    dict(q="What is a closure in JavaScript?",
         category="concept", expect=[ELOQUENT, JS_WAY]),
    dict(q="How does Rust ownership and borrowing work?",
         category="concept", expect=[RUST]),
    dict(q="Explain stacks and queues",
         category="concept", expect=[JJJ, C_BOOK, THINK_PY]),
    dict(q="What is recursion and when should I use it?",
         category="concept", expect=[THINK_PY, THINK_CPP, JJJ]),
    dict(q="How is a linked list stored in memory?",
         category="concept", expect=[JJJ, C_BOOK, MODERN_C]),
    dict(q="How does virtual memory paging work?",
         category="concept", expect=[OSC]),
    dict(q="What is a deadlock in an operating system?",
         category="concept", expect=[OSC]),
    dict(q="How does the scheduler decide which process runs next?",
         category="concept", expect=[OSC]),
    dict(q="How does TCP establish a connection?",
         category="concept", expect=[OSC]),

    # --- machine learning ------------------------------------------------
    # The collection holds ML scholarly papers but no ML textbook, so there
    # is no expected title. These are judged on whether a real, cited passage
    # comes back at all - never on whether a book was invented to answer.
    dict(q="What is supervised learning?",
         category="ml", expect=[], grounded_only=True),
    dict(q="Explain gradient descent",
         category="ml", expect=[], grounded_only=True),
    dict(q="What is the difference between precision and recall?",
         category="ml", expect=[], grounded_only=True),

    # --- literature sanity checks ---------------------------------------
    dict(q="Why does Elizabeth Bennet dislike Mr Darcy?",
         category="literature", expect=[PRIDE]),
    dict(q="How does Holmes solve the case of the hound?",
         category="literature", expect=[HOUND]),
    dict(q="What does Sun Tzu say about deception in war?",
         category="literature", expect=[ART_OF_WAR]),

    # --- deliberately vague ---------------------------------------------
    dict(q="I keep forgetting how memory works in C",
         category="vague", expect=[C_BOOK, MODERN_C]),

    # --- final breadth checks --------------------------------------------
    # A catalogued language with no digitised titles: the answer may only be
    # a metadata-only record, and it must say so rather than pretend.
    dict(q="Which C# book should I start with?",
         category="recommendation", expect=["Head First C#"],
         want_language="C#", want_level="BEGINNER"),
    dict(q="What is the difference between a process and a thread?",
         category="concept", expect=[OSC]),
    dict(q="Why do my C++ objects get copied when I pass them to a function?",
         category="concept", expect=[THINK_CPP, BUSBEE]),
]


def token():
    existing = os.environ.get("ASTRALIB_TOKEN")
    if existing:
        return existing

    email = os.environ.get("ASTRALIB_EMAIL")
    password = os.environ.get("ASTRALIB_PASSWORD")
    if not email or not password:
        sys.exit("set ASTRALIB_TOKEN or ASTRALIB_EMAIL/ASTRALIB_PASSWORD")

    body = json.dumps({"email": email, "password": password}).encode()
    request = urllib.request.Request(
        BASE + "/api/auth/login", data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response).get("token", "")


def call(path, params, token_value):
    url = "%s%s?%s" % (BASE, path, urllib.parse.urlencode(params))

    request = urllib.request.Request(
        url, headers={"Authorization": "Bearer " + token_value}
    )

    started = time.time()

    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        return {"error": "HTTP %d" % error.code, "ms": 0, "payload": None}
    except Exception as error:                                    # noqa: BLE001
        return {"error": str(error), "ms": 0, "payload": None}

    return {
        "error": None,
        "ms": int((time.time() - started) * 1000),
        "payload": payload,
    }


def is_stub(text):
    """A legacy metadata-only vector is a title block, not book prose."""
    if not text:
        return False
    head = text.lstrip()[:120].lower()
    return head.startswith("title:") and "author:" in head


def unreadable_titles():
    """Catalogue titles with no digitised copy (metadata-only records).

    A search result naming one of these has cited a book nobody can read, so
    it counts as a failure however relevant the title looks.
    """

    path = os.path.join(ROOT, "catalogue", "programming-availability.json")

    if not os.path.exists(path):
        return set()

    with open(path, encoding="utf-8") as handle:
        payload = json.load(handle)

    return {
        book["title"]
        for book in payload.get("books", [])
        if book.get("status") not in ("INDEXED", "RESOURCE_AVAILABLE")
    }


UNREADABLE = unreadable_titles()


def rank_of(expect, titles):
    for index, title in enumerate(titles, start=1):
        for wanted in expect:
            if wanted == title:
                return index
    return None


def evaluate_query(entry, engine, token_value, smart=False):
    path = "/api/search/smart" if smart else "/api/search/" + engine

    result = call(path, {"query": entry["q"], "topK": TOP_K}, token_value)

    record = {
        "query": entry["q"],
        "category": entry["category"],
        "engine": "smart" if smart else engine,
        "expected": entry["expect"],
        "want_none": entry.get("want_none", False),
        "error": result["error"],
        "ms": result["ms"],
        "intent": None,
        "rank": None,
        "top_score": None,
        "returned": [],
        "grounded": False,
        "stub_sources": 0,
        "unreadable_sources": 0,
        "grounded_only": entry.get("grounded_only", False),
        "recommendations": [],
        "recommendation_hit": None,
    }

    payload = result["payload"]

    if payload is None:
        return record

    # The semantic endpoint now answers with an envelope (passages,
    # catalogue matches, understanding) because a book request has no
    # passage to cite. Evaluation of a passage engine still scores the
    # passage list.
    if not smart and isinstance(payload, dict) and isinstance(payload.get("passages"), list):
        payload = payload.get("passages")

    if smart and isinstance(payload, dict):
        record["intent"] = (payload.get("understanding") or {}).get("intent")

        passages = payload.get("passages") or []
        recs = payload.get("recommendations") or []

        record["recommendations"] = [
            {
                "title": r.get("title"),
                "level": r.get("level"),
                "readable": r.get("readable"),
            }
            for r in recs
        ]

        titles = [r.get("title") for r in recs]

        if entry.get("want_language"):
            level = entry.get("want_level")
            record["recommendation_hit"] = any(
                (r.get("level") or "").upper() == (level or r.get("level") or "")
                for r in recs
            ) if level else bool(recs)

        # A recommendation query is judged on the recommendations; a concept
        # query on the passages.
        if entry["category"] == "recommendation":
            record["rank"] = rank_of(entry["expect"], titles)
            record["grounded"] = bool(recs)
        else:
            titles = [p.get("title") for p in passages]
            record["rank"] = rank_of(entry["expect"], titles)
            record["stub_sources"] = sum(
                1 for p in passages if is_stub(p.get("content"))
            )
            record["grounded"] = bool(passages) and record["stub_sources"] == 0
            if passages:
                record["top_score"] = passages[0].get("similarityScore")

        record["returned"] = [
            {
                "title": p.get("title"),
                "score": p.get("similarityScore"),
                "section": p.get("section"),
                "stub": is_stub(p.get("content")),
            }
            for p in passages
        ]

        record["unreadable_sources"] = sum(
            1
            for p in passages
            if p.get("title") in UNREADABLE
        )

        return record

    if not isinstance(payload, list):
        return record

    titles = [item.get("title") for item in payload]

    record["rank"] = rank_of(entry["expect"], titles)
    record["returned"] = [
        {
            "title": item.get("title"),
            "score": item.get("similarityScore"),
            "section": item.get("section"),
            "stub": is_stub(item.get("content")),
        }
        for item in payload
    ]
    record["stub_sources"] = sum(1 for item in record["returned"] if item["stub"])

    if payload:
        record["top_score"] = payload[0].get("similarityScore")

    record["grounded"] = (
        bool(payload)
        and record["stub_sources"] == 0
        and (record["rank"] is not None or entry.get("want_none"))
    )

    return record


def summarise(records):
    total = len(records)

    def hit(record):
        if record.get("grounded_only"):
            # No expected title: the bar is a real, cited passage and no
            # invented or unreadable source.
            return (
                bool(record["returned"])
                and record["stub_sources"] == 0
                and record.get("unreadable_sources", 0) == 0
            )
        if record["want_none"]:
            # Correct behaviour is to offer nothing, or nothing grounded.
            return record["rank"] is None and record["stub_sources"] == 0
        if record["category"] == "recommendation":
            return record["rank"] is not None and record["rank"] <= 3
        return record["rank"] is not None and record["rank"] <= 3

    hits = [r for r in records if hit(r)]

    return {
        "queries": total,
        "top3": len(hits),
        "accuracy": round(len(hits) / total, 3) if total else 0,
        "empty": sum(1 for r in records if not r["returned"] and not r["recommendations"]),
        "stub_sources": sum(r["stub_sources"] for r in records),
        "unreadable_sources": sum(
            r.get("unreadable_sources", 0) for r in records
        ),
        "errors": sum(1 for r in records if r["error"]),
        "median_ms": sorted(r["ms"] for r in records)[total // 2] if total else 0,
    }


def write_markdown(report, path):
    lines = [
        "# Smart Search evaluation — %s" % report["label"],
        "",
        "Generated by `catalogue/evaluate_search.py`. %d queries per engine."
        % report["summary"][next(iter(report["summary"]))]["queries"]
        if report["summary"] else "",
        "",
        "| Engine | Top-3 correct | Accuracy | Empty results | Stub sources | Median latency |",
        "| --- | --- | --- | --- | --- | --- |",
    ]

    for engine, summary in report["summary"].items():
        lines.append(
            "| %s | %d/%d | %.0f%% | %d | %d | %d ms |"
            % (
                engine,
                summary["top3"],
                summary["queries"],
                summary["accuracy"] * 100,
                summary["empty"],
                summary["stub_sources"],
                summary["median_ms"],
            )
        )

    for engine, records in report["engines"].items():
        lines += [
            "",
            "## %s" % engine,
            "",
            "| Query | Intent | Correct? | Rank | Top score | Notes |",
            "| --- | --- | --- | --- | --- | --- |",
        ]

        for record in records:
            if record.get("grounded_only"):
                correct = (
                    bool(record["returned"])
                    and record["stub_sources"] == 0
                    and record.get("unreadable_sources", 0) == 0
                )
            elif record["want_none"]:
                correct = (
                    record["rank"] is None
                    and record["stub_sources"] == 0
                )
            else:
                correct = record["rank"] is not None and record["rank"] <= 3

            notes = []
            if record["error"]:
                notes.append(record["error"])
            if record["stub_sources"]:
                notes.append("%d metadata stub(s)" % record["stub_sources"])
            if record.get("unreadable_sources"):
                notes.append(
                    "%d unreadable source(s)" % record["unreadable_sources"]
                )
            if record["want_none"] and record["returned"]:
                notes.append("returned sources for an unanswerable question")
            if record.get("grounded_only"):
                notes.append(
                    "no expected title; judged on grounding"
                    if record["returned"]
                    else "no passage returned"
                )
            if not record["returned"] and not record["recommendations"]:
                notes.append("no results")

            lines.append(
                "| %s | %s | %s | %s | %s | %s |"
                % (
                    record["query"].replace("|", "/"),
                    record["intent"] or "—",
                    "yes" if correct else "**no**",
                    record["rank"] if record["rank"] else "—",
                    record["top_score"] if record["top_score"] is not None else "—",
                    "; ".join(notes) or "",
                )
            )

    lines.append("")

    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))


def unindexed_check(token_value):
    """Ask for each metadata-only book by name and see what comes back.

    A title with no digitised copy must never come back as a passage, because
    a passage is presented to the reader as a quotable source. It may come
    back as a recommendation, where the UI states that it is not readable.
    """

    path = os.path.join(ROOT, "catalogue", "programming-availability.json")

    with open(path, encoding="utf-8") as handle:
        books = json.load(handle).get("books", [])

    rows = []

    for book in books:
        if book.get("status") == "INDEXED":
            continue

        result = call(
            "/api/search/smart",
            {"query": book["title"], "topK": TOP_K},
            token_value,
        )

        payload = result["payload"] or {}

        passages = [
            p.get("title") for p in payload.get("passages") or []
        ]

        recs = {
            (r.get("title"), bool(r.get("readable")))
            for r in payload.get("recommendations") or []
        }

        cited = book["title"] in passages

        rows.append(
            {
                "title": book["title"],
                "status": book.get("status"),
                "cited_as_passage": cited,
                "offered_as_recommendation": any(
                    title == book["title"] for title, _ in recs
                ),
                "recommendation_readable_flag": next(
                    (
                        readable
                        for title, readable in recs
                        if title == book["title"]
                    ),
                    None,
                ),
                "passages": passages,
            }
        )

    failures = [
        row for row in rows
        if row["cited_as_passage"]
        or row["recommendation_readable_flag"] is True
    ]

    lines = [
        "# Unindexed books are never cited as sources",
        "",
        "Each of the %d metadata-only programming titles was searched by name. "
        "A pass means it never appears in `passages`, and if it appears in "
        "`recommendations` it is flagged as not readable." % len(rows),
        "",
        "Result: **%s** (%d failure(s))."
        % ("pass" if not failures else "FAIL", len(failures)),
        "",
        "| Title | Status | Cited as passage | Offered as recommendation | Marked readable |",
        "| --- | --- | --- | --- | --- |",
    ]

    for row in rows:
        lines.append(
            "| %s | %s | %s | %s | %s |"
            % (
                row["title"].replace("|", "/"),
                row["status"],
                "**yes**" if row["cited_as_passage"] else "no",
                "yes" if row["offered_as_recommendation"] else "no",
                row["recommendation_readable_flag"],
            )
        )

    lines.append("")

    out = os.path.join(ROOT, "docs", "UNINDEXED_SOURCE_CHECK.md")

    with open(out, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))

    print(
        "%d metadata-only titles checked, %d failure(s)"
        % (len(rows), len(failures))
    )
    print("wrote %s" % os.path.relpath(out, ROOT))

    return 0 if not failures else 1


def recompute(label):
    """Re-derive the unreadable-source counts from stored result lists.

    The counts depend on the current catalogue status, not on the search, so
    a report can be refreshed without spending hours re-querying the API.
    """

    report = load_report(label)

    for records in report["engines"].values():
        for record in records:
            record["unreadable_sources"] = sum(
                1
                for item in record.get("returned", [])
                if item.get("title") in UNREADABLE
            )

        report["summary"] = {
            engine: summarise(records)
            for engine, records in report["engines"].items()
        }

    path = os.path.join(ROOT, "catalogue", "search_evaluation_%s.json" % label)

    with open(path, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2)

    write_markdown(
        report,
        os.path.join(ROOT, "docs", "SEARCH_EVALUATION_%s.md" % label.upper()),
    )

    return report


def load_report(label):
    path = os.path.join(ROOT, "catalogue", "search_evaluation_%s.json" % label)
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def write_comparison(before, after, path):
    """Side-by-side table for two reports over the same query set."""

    before_rows = {}
    for engine, records in before["engines"].items():
        for record in records:
            before_rows[(engine, record["query"])] = record

    lines = [
        "# Smart Search — before and after, same query set",
        "",
        "`before` is the unchanged semantic and hybrid endpoints (fixed 0.60 "
        "cosine floor, fixed hybrid weights). `after` is `/api/search/smart`. "
        "Both reports were produced by `catalogue/evaluate_search.py` on the "
        "same %d queries." % len(after["engines"][next(iter(after["engines"]))]),
        "",
        "| Query | Before (best of semantic / hybrid) | After (smart) | Before rank | After rank | Intent |",
        "| --- | --- | --- | --- | --- | --- |",
    ]

    for engine, records in after["engines"].items():
        for record in records:

            def verdict(row):
                if row is None:
                    return "—"
                if row.get("error"):
                    return "error"
                if row["want_none"]:
                    return "correct" if not row["returned"] else "**wrong**"
                if row["rank"] is not None and row["rank"] <= 3:
                    return "hit"
                return "**miss**"

            def rank(row):
                if row is None:
                    return "—"
                return row["rank"] if row["rank"] else "—"

            semantic = before_rows.get(("semantic", record["query"]))
            hybrid = before_rows.get(("hybrid", record["query"]))

            # The stronger of the two pre-existing engines represents "before".
            best_before = None
            for candidate in (semantic, hybrid):
                if candidate is None:
                    continue
                if best_before is None:
                    best_before = candidate
                elif (candidate["rank"] or 99) < (best_before["rank"] or 99):
                    best_before = candidate

            if best_before is not None and best_before["want_none"]:
                if not semantic["returned"] or not hybrid["returned"]:
                    best_before = semantic if not semantic["returned"] else hybrid

            lines.append(
                "| %s | %s | %s | %s | %s | %s |"
                % (
                    record["query"].replace("|", "/"),
                    verdict(best_before),
                    verdict(record),
                    rank(best_before),
                    rank(record),
                    record["intent"] or "—",
                )
            )

    lines += [
        "",
        "## Summary",
        "",
        "| Run | Top-3 correct | Accuracy | Empty results | Stub sources | Median latency |",
        "| --- | --- | --- | --- | --- | --- |",
    ]

    before_best = max(
        before["summary"].get("semantic", {"top3": 0, "queries": 1})["top3"],
        before["summary"].get("hybrid", {"top3": 0, "queries": 1})["top3"],
    )
    before_queries = next(iter(before["summary"].values()))["queries"]
    before_latency = min(
        summary["median_ms"] for summary in before["summary"].values()
    )

    lines.append(
        "| before (best existing engine) | %d/%d | %.0f%% | %d | %d | %d ms |"
        % (
            before_best,
            before_queries,
            before_best / before_queries * 100,
            min(summary["empty"] for summary in before["summary"].values()),
            sum(summary["stub_sources"] for summary in before["summary"].values()),
            before_latency,
        )
    )

    for engine, summary in after["summary"].items():
        lines.append(
            "| after (%s) | %d/%d | %.0f%% | %d | %d | %d ms |"
            % (
                engine,
                summary["top3"],
                summary["queries"],
                summary["accuracy"] * 100,
                summary["empty"],
                summary["stub_sources"],
                summary["median_ms"],
            )
        )

    lines.append("")

    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", default="run")
    parser.add_argument("--engines", default="semantic,hybrid")
    parser.add_argument("--smart", action="store_true")
    parser.add_argument(
        "--unindexed-check",
        action="store_true",
        help="verify metadata-only books are never cited as sources",
    )
    parser.add_argument(
        "--recompute",
        nargs="*",
        metavar="LABEL",
        help="refresh stored reports without re-querying the API",
    )
    parser.add_argument(
        "--compare",
        nargs=2,
        metavar=("BEFORE", "AFTER"),
        help="write a before/after comparison from two existing reports",
    )
    args = parser.parse_args()

    if args.unindexed_check:
        return unindexed_check(token())

    if args.recompute is not None:
        for label in args.recompute:
            report = recompute(label)
            for engine, summary in report["summary"].items():
                print(
                    "%s/%s top3=%d/%d stubs=%d unreadable=%d"
                    % (
                        label,
                        engine,
                        summary["top3"],
                        summary["queries"],
                        summary["stub_sources"],
                        summary.get("unreadable_sources", 0),
                    )
                )
        return 0

    if args.compare:
        before_label, after_label = args.compare
        before = load_report(before_label)
        after = load_report(after_label)
        out = os.path.join(
            ROOT, "docs", "SEARCH_EVALUATION_COMPARISON.md"
        )
        write_comparison(before, after, out)
        print("wrote %s" % os.path.relpath(out, ROOT))
        return 0

    token_value = token()

    engines = [engine.strip() for engine in args.engines.split(",") if engine.strip()]

    if args.smart:
        engines = ["smart"]

    report = {
        "label": args.label,
        "generated": time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()),
        "top_k": TOP_K,
        "summary": {},
        "engines": {},
    }

    for engine in engines:
        records = []

        for index, entry in enumerate(QUERIES, start=1):
            record = evaluate_query(
                entry,
                engine if engine != "smart" else "smart",
                token_value,
                smart=(engine == "smart"),
            )
            records.append(record)

            status = "ok " if (
                (record["rank"] or 9) <= 3
                if not record["want_none"]
                else record["rank"] is None
            ) else "MISS"

            print(
                "[%s %2d/%d] %s %-46s rank=%s top=%s"
                % (
                    engine,
                    index,
                    len(QUERIES),
                    status,
                    entry["q"][:46],
                    record["rank"],
                    record["top_score"],
                ),
                flush=True,
            )

        report["engines"][engine] = records
        report["summary"][engine] = summarise(records)

    out_json = os.path.join(ROOT, "catalogue", "search_evaluation_%s.json" % args.label)
    out_md = os.path.join(ROOT, "docs", "SEARCH_EVALUATION_%s.md" % args.label.upper())

    with open(out_json, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2)

    write_markdown(report, out_md)

    print()
    for engine, summary in report["summary"].items():
        print(
            "%-9s top3=%d/%d empty=%d stubs=%d median=%dms"
            % (
                engine,
                summary["top3"],
                summary["queries"],
                summary["empty"],
                summary["stub_sources"],
                summary["median_ms"],
            )
        )

    print("wrote %s" % os.path.relpath(out_md, ROOT))

    return 0


if __name__ == "__main__":
    sys.exit(main())
