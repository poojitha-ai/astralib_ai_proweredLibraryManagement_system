#!/usr/bin/env python
"""
Validate AstraLib's live semantic and hybrid search against the curated collection.

Every check names a book and a question whose answer is genuinely in that book.
A check passes when the expected book is returned within the top 3 results with a
passage that is not just the title page.

Usage:
    ASTRALIB_TOKEN=<jwt> python catalogue/validate_search.py
    ASTRALIB_EMAIL=... ASTRALIB_PASSWORD=... python catalogue/validate_search.py --full
    python catalogue/validate_search.py --only "The Time Machine"   # re-check one book

Results are written to catalogue/search_validation.json and consumed by
catalogue/build_inventory.py. Nothing here writes to the database.

The JSON file is rewritten after every check, so an interrupted run keeps
its completed checks. Semantic search currently scans every stored vector
(~50 s per query on the full 46k-row table), which is why the default run
uses a representative "core" subset rather than every book.
"""

import json
import os
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "catalogue" / "search_validation.json"
BASE = os.environ.get("ASTRALIB_API", "http://127.0.0.1:8082")
TOP_K = 5
ACCEPTED_RANK = 3

# One representative question per pathway: literature, mystery, gothic /
# science fiction, philosophy, self-development, and each learning pathway.
CORE = [
    ("Pride and Prejudice", "What does Elizabeth Bennet think of Mr Darcy's pride?"),
    ("The Moonstone", "Who took the Moonstone diamond and how is the mystery explained?"),
    ("The Hound of the Baskervilles", "What is the legend of the hound that haunts the Baskerville family?"),
    ("Frankenstein; Or, The Modern Prometheus", "How does Victor Frankenstein bring his creature to life?"),
    # Gutenberg's text spells the year out in words ("Eight Hundred and Two
    # Thousand Seven Hundred and One"), so a numeric year has no passage to
    # match; this question is grounded in the Morlock chapters instead.
    ("The Time Machine", "What are the Morlocks in The Time Machine?"),
    ("The Wonderful Wizard of Oz", "How does Dorothy travel to the Emerald City and who joins her?"),
    ("Meditations", "What does Marcus Aurelius say about controlling your own judgments?"),
    ("The Art of War", "What does Sun Tzu say about winning without fighting?"),
    ("How to Live on 24 Hours a Day", "How should a person use the hours outside work to improve their life?"),
    ("Think Python (2nd Edition)", "How do you define a function with parameters in Python?"),
    ("Python for Everybody: Exploring Data in Python 3", "How do you read a file in Python and process each line?"),
    ("Eloquent JavaScript (3rd Edition)", "What is a closure in JavaScript and what does it let a function keep?"),
    ("Pro Git (2nd Edition)", "How do you create and merge a branch in Git?"),
    ("Operating System Concepts (9th Edition)", "How does a process move between the ready and waiting states?"),
]

# Extra books, used only by --full.
EXTRA = [
    ("Emma", "How does Emma Woodhouse interfere in Harriet's love life?"),
    ("Jane Eyre", "Describe the red-room at Gateshead and Jane Eyre's terror"),
    ("Wuthering Heights", "Why does Heathcliff seek revenge on the Earnshaw and Linton families?"),
    ("A Study in Scarlet", "How do Holmes and Watson first meet and agree to share rooms?"),
    ("The Woman in White", "What is the secret of the woman in white on the road to London?"),
    ("Dracula", "What rules must Van Helsing follow to destroy a vampire?"),
    ("Treasure Island", "Where is the treasure buried and who has the map?"),
    ("Alice's Adventures in Wonderland", "What happens at the Mad Tea-Party?"),
    ("Little Women", "How do the March sisters spend their Christmas?"),
    ("Great Expectations", "Who is Pip's secret benefactor?"),
    ("Moby-Dick; Or, The Whale", "Why does Captain Ahab hunt the white whale?"),
    ("The Secret Garden", "How does Mary Lennox discover and restore the locked garden?"),
    ("The Picture of Dorian Gray", "What happens to Dorian Gray's portrait as he ages?"),
    ("The Strange Case of Dr. Jekyll and Mr. Hyde", "How does Dr Jekyll transform into Mr Hyde?"),
    ("The Prophet", "What does the Prophet say about love and marriage?"),
]

HYBRID_EXTRA = [
    ("Operating System Concepts (9th Edition)", "What is thrashing and how does the system recover from it?"),
    ("Eloquent JavaScript (3rd Edition)", "How do promises differ from callbacks for asynchronous code?"),
    ("Pro Git (2nd Edition)", "How do you undo a commit that has already been pushed?"),
]

# The open textbooks that now carry real full text. Each question is
# answerable from that book and from no other in the collection.
# Semantic search keeps a 0.60 relevance floor, so a check uses the
# vocabulary of the book itself rather than a long conceptual question that
# every programming text in the collection could answer. Each query below
# names something only that book covers (verified in the stored passages).
PROGRAMMING = [
    ("Think Java: How to Think Like a Computer Scientist (2nd Edition)",
     "99 Bottles of Beer song printed by a loop in Java"),
    ("Java, Java, Java: Object-Oriented Problem Solving (3rd Edition)",
     "OneRowNim game and its pile of sticks"),
    ("Java Programming",
     "reflection lets a program inspect a class and its methods at runtime"),
    ("Advanced Python Mastery",
     "generators and yield produce values lazily instead of a list"),
    ("C Programming",
     "declare a pointer to an integer and take the address of a variable"),
    ("Modern C (C23 Edition)",
     "the _BitInt(N) type provides bit-precise integers in C23"),
    ("Programming Fundamentals: A Modular Structured Approach Using C++",
     "hierarchy chart for program planning and design"),
    ("Think C++: How to Think Like a Computer Scientist",
     "Deck structure holding an array of cards in C++"),
    ("The JavaScript Way",
     "cancel the default behavior of an event with preventDefault"),
    ("The Rust Programming Language",
     "ownership and borrowing of values in Rust prevents data races"),
]

SKIP_TITLE_ONLY = 200  # a passage shorter than this is likely a heading, not content


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
        BASE + "/api/auth/login",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.load(response)
    return payload.get("token", "")


def search(token_value, engine, query):
    params = urllib.parse.urlencode({"query": query, "topK": TOP_K})
    request = urllib.request.Request(
        f"{BASE}/api/search/{engine}?{params}",
        headers={"Authorization": "Bearer " + token_value},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def run_check(token_value, engine, book, query):
    try:
        results = search(token_value, engine, query)
    except Exception as exc:  # network / auth failure: record, do not crash
        return {
            "query": query,
            "book": book,
            "engine": engine,
            "top_hit": None,
            "score": None,
            "rank": None,
            "snippet": "",
            "verified": False,
            "error": str(exc),
        }

    titles = [r.get("title") for r in results]
    rank = titles.index(book) + 1 if book in titles else None
    hit = results[0] if results else {}
    best = results[rank - 1] if rank else hit
    content = (best.get("content") or "").strip()

    return {
        "query": query,
        "book": book,
        "engine": engine,
        "top_hit": hit.get("title"),
        "score": best.get("similarityScore") if rank else hit.get("similarityScore"),
        "rank": rank,
        "snippet": content[:160],
        "verified": bool(rank and rank <= ACCEPTED_RANK and len(content) >= SKIP_TITLE_ONLY),
    }


def flush(checks):
    payload = {
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "api": BASE,
        "top_k": TOP_K,
        "accepted_rank": ACCEPTED_RANK,
        "checks": checks,
    }
    OUTPUT.write_text(json.dumps(payload, indent=1), encoding="utf-8")


def parse_only():
    """--only <book title>: re-check selected books without discarding the rest."""
    for index, arg in enumerate(sys.argv):
        if arg == "--only" and index + 1 < len(sys.argv):
            return sys.argv[index + 1]
    return None


def main():
    full = "--full" in sys.argv
    programming = "--programming" in sys.argv
    only = parse_only()

    if programming:
        # Semantic for every new textbook, hybrid for the two largest PDFs.
        plan = [("semantic", b, q) for b, q in PROGRAMMING]
        plan += [("hybrid", b, q) for b, q in PROGRAMMING[:2]]
    else:
        books = CORE + EXTRA if full else CORE

        plan = [("semantic", b, q) for b, q in books]
        plan += [("hybrid", b, q) for b, q in books]
        plan += [("hybrid", b, q) for b, q in HYBRID_EXTRA]

    if only:
        plan = [step for step in plan if only.lower() in step[1].lower()]
        if not plan:
            sys.exit(f"no checks matched {only!r}")

    token_value = token()
    if not token_value:
        sys.exit("login did not return a token")

    # A targeted re-check keeps the previously recorded checks for every
    # other book instead of replacing the whole report.
    checks = []
    if only and OUTPUT.exists():
        previous = json.loads(OUTPUT.read_text(encoding="utf-8")).get("checks", [])
        planned_books = {book for _engine, book, _query in plan}
        checks = [c for c in previous if c.get("book") not in planned_books]

    rechecked = []
    for index, (engine, book, query) in enumerate(plan, start=1):
        check = run_check(token_value, engine, book, query)
        rechecked.append(check)
        flush(checks + rechecked)
        status = "ok  " if check["verified"] else "FAIL"
        print(
            f"[{index}/{len(plan)}] {status} {engine:8} rank={check['rank']} "
            f"{check['top_hit']} <- {query[:55]}",
            flush=True,
        )

    checks = checks + rechecked
    total = len(checks)
    passed = sum(1 for c in checks if c["verified"])
    for c in checks:
        if not c["verified"]:
            print(
                f"FAIL: {c['engine']:8} | {c['query'][:60]} -> "
                f"expected {c['book']} / got {c['top_hit']} (rank {c['rank']})"
            )
    print(f"{passed}/{total} verified -> {OUTPUT.relative_to(ROOT)}")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
