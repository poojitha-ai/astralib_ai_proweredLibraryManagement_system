#!/usr/bin/env python3
"""
Resolves verified bibliographic data for the programming-library expansion.

Every field written to catalogue/programming-books.json comes from an
edition record on Open Library, never from memory:

  * the ISBN-13, publisher and year come from the same English edition;
  * editions from the book's actual publisher are preferred over
    print-on-demand reprints;
  * a cover is kept only when Open Library serves a real image for it.

Books with no verifiable cover are left without one, so the UI falls back
to its typographic cover instead of inventing artwork.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

UA = {"User-Agent": "AstraLib-catalogue-builder/1.0 (local development)"}
OUT = os.path.join(os.path.dirname(__file__), "programming-books.json")

SELF_PUBLISHED = ("createspace", "independently published", "lulu", "blurb", "de gruyter")

# language, level, title, author, open library title hint, publisher hint
BOOKS = [
    ("Python", "BEGINNER", "Python Crash Course", "Eric Matthes",
     "Python Crash Course", ("no starch",)),
    ("Python", "INTERMEDIATE", "Effective Python", "Brett Slatkin",
     "Effective Python", ("addison", "pearson")),
    ("Python", "ADVANCED", "Fluent Python", "Luciano Ramalho",
     "Fluent Python", ("o'reilly", "o’reilly", "oreilly")),
    ("Java", "BEGINNER", "Head First Java", "Kathy Sierra, Bert Bates and Trisha Gee",
     "Head First Java", ("o'reilly", "o’reilly", "oreilly")),
    ("Java", "INTERMEDIATE", "Core Java, Volume I", "Cay S. Horstmann",
     "Core Java Volume I Fundamentals", ("pearson", "prentice", "oracle")),
    ("Java", "ADVANCED", "Effective Java", "Joshua Bloch",
     "Effective Java", ("addison", "pearson")),
    ("C", "BEGINNER", "Let Us C", "Yashavant Kanetkar",
     "Let Us C", ("bpb",)),
    ("C", "INTERMEDIATE", "C Programming: A Modern Approach", "K. N. King",
     "C Programming A Modern Approach", ("norton", "w. w. norton")),
    ("C", "ADVANCED", "Expert C Programming", "Peter van der Linden",
     "Expert C Programming", ("prentice", "sunsoft", "pearson")),
    ("C++", "BEGINNER", "Programming: Principles and Practice Using C++", "Bjarne Stroustrup",
     "Programming Principles and Practice Using C++", ("addison", "pearson")),
    ("C++", "INTERMEDIATE", "C++ Primer", "Stanley B. Lippman, Josée Lajoie and Barbara E. Moo",
     "C++ Primer", ("addison", "pearson")),
    ("C++", "ADVANCED", "Effective Modern C++", "Scott Meyers",
     "Effective Modern C++", ("o'reilly", "o’reilly", "oreilly")),
    ("JavaScript", "BEGINNER", "Eloquent JavaScript", "Marijn Haverbeke",
     "Eloquent JavaScript", ("no starch",)),
    ("JavaScript", "INTERMEDIATE", "You Don't Know JS Yet", "Kyle Simpson",
     "You Don't Know JS", ("o'reilly", "o’reilly", "oreilly")),
    ("JavaScript", "ADVANCED", "JavaScript: The Definitive Guide", "David Flanagan",
     "JavaScript The Definitive Guide", ("o'reilly", "o’reilly", "oreilly")),
    ("C#", "BEGINNER", "Head First C#", "Andrew Stellman and Jennifer Greene",
     "Head First C#", ("o'reilly", "o’reilly", "oreilly")),
    ("C#", "INTERMEDIATE", "The C# Player's Guide", "R. B. Whitaker",
     "C# Player's Guide", ("starbound",)),
    ("C#", "ADVANCED", "C# in Depth", "Jon Skeet",
     "C# in Depth", ("manning",)),
    ("Go", "BEGINNER", "Get Programming with Go", "Nathan Youngman and Roger Peppe",
     "Get Programming with Go", ("manning",)),
    ("Go", "INTERMEDIATE", "The Go Programming Language", "Alan A. A. Donovan and Brian W. Kernighan",
     "The Go Programming Language", ("addison", "pearson")),
    ("Go", "ADVANCED", "Concurrency in Go", "Katherine Cox-Buday",
     "Concurrency in Go", ("o'reilly", "o’reilly", "oreilly")),
    ("Rust", "BEGINNER", "The Rust Programming Language", "Steve Klabnik and Carol Nichols",
     "The Rust Programming Language", ("no starch",)),
    ("Rust", "INTERMEDIATE", "Rust in Action", "Tim McNamara",
     "Rust in Action", ("manning",)),
    ("Rust", "ADVANCED", "Rust for Rustaceans", "Jon Gjengset",
     "Rust for Rustaceans", ("no starch",)),
]


CACHE_PATH = os.path.join(os.path.dirname(__file__), ".resolve-cache.json")

try:
    with open(CACHE_PATH, encoding="utf-8") as cache_file:
        CACHE = json.load(cache_file)
except (OSError, ValueError):
    CACHE = {}


def save_cache():
    with open(CACHE_PATH, "w", encoding="utf-8") as cache_file:
        json.dump(CACHE, cache_file)


def get_json(url):
    if url in CACHE:
        return CACHE[url]

    request = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.load(response)

    CACHE[url] = payload
    return payload


def get_json_safe(url):
    """Fetch JSON, tolerating removed works and short rate limits."""
    for attempt in range(3):
        try:
            return get_json(url)
        except urllib.error.HTTPError as error:
            if error.code == 429:
                time.sleep(2 + attempt * 2)
                continue
            return None
        except (urllib.error.URLError, TimeoutError):
            time.sleep(1 + attempt)
    return None


def image_ok(url):
    if url in CACHE:
        return CACHE[url]

    try:
        request = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(request, timeout=20) as response:
            content_type = response.headers.get("Content-Type", "")
            size = len(response.read())
            ok = response.status == 200 and content_type.startswith("image/") and size > 2000
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError):
        ok = False

    CACHE[url] = ok
    return ok


def edition_year(edition):
    for token in (edition.get("publish_date") or "").replace(",", " ").split():
        if token.isdigit() and len(token) == 4:
            return int(token)
    return None


def publisher_matches(publisher, hints):
    text = (publisher or "").lower()
    return any(hint in text for hint in hints)


def candidate_editions(work_key):
    data = get_json_safe(f"https://openlibrary.org{work_key}/editions.json?limit=200")

    if not data:
        return

    for edition in data.get("entries", []):
        languages = edition.get("languages") or []
        keys = {entry.get("key") for entry in languages}

        # Require an edition Open Library records as English: an
        # unlabelled edition is often a translation.
        if "/languages/eng" not in keys:
            continue

        isbn13 = [v for v in (edition.get("isbn_13") or []) if len(v) == 13]

        if isbn13 and edition.get("publishers"):
            publisher = edition["publishers"][0].strip()
            if any(flag in publisher.lower() for flag in SELF_PUBLISHED):
                continue
            yield edition, isbn13[0], publisher


def resolve(title, author, hint, publisher_hints):
    # A free-text query finds far more works than the strict title field
    # (Open Library often files the canonical title under a variant).
    query = urllib.parse.urlencode({"q": hint, "limit": 20})
    search = get_json_safe(
        f"https://openlibrary.org/search.json?{query}&fields=key,title,cover_i"
    ) or {}
    found = search.get("docs", [])

    result = {
        "title": title,
        "author": author,
        "isbn": None,
        "publisher": None,
        "publicationYear": None,
        "coverImage": None,
        "openLibraryKey": None,
        "openLibraryWork": None,
        "verified": False,
        "note": None,
    }

    hint_tokens = {t for t in hint.lower().replace(":", " ").split() if len(t) > 2}

    # (publisher_match, year, edition, isbn, publisher, work_key, work_cover)
    candidates = []
    covered_works = []
    inspected = 0

    for doc in found:
        key = doc.get("key")
        if not key or inspected >= 3:
            continue

        work_title_tokens = {
            t for t in (doc.get("title") or "").lower().replace(":", " ").split() if len(t) > 2
        }

        if hint_tokens:
            overlap = len(hint_tokens & work_title_tokens) / len(hint_tokens)
            if overlap < 0.5:
                continue

        inspected += 1

        if doc.get("cover_i"):
            covered_works.append((doc["cover_i"], key))

        for edition, isbn, publisher in candidate_editions(key):
            candidates.append(
                (
                    1 if publisher_matches(publisher, publisher_hints) else 0,
                    edition_year(edition) or 0,
                    edition,
                    isbn,
                    publisher,
                    key,
                    doc.get("cover_i"),
                )
            )

    # Only editions from the book's real publisher are trusted with an
    # ISBN; Open Library also carries reprints and lookalike works.
    candidates.sort(key=lambda item: (item[0], item[1]), reverse=True)
    trusted = [item for item in candidates if item[0] == 1]

    for _, _, edition, isbn, publisher, key, work_cover in trusted[:4]:

        if not image_ok(f"https://covers.openlibrary.org/b/isbn/{isbn}-L.jpg?default=false"):
            continue

        result.update(
            {
                "isbn": isbn,
                "publisher": publisher,
                "publicationYear": edition_year(edition),
                "coverImage": f"https://covers.openlibrary.org/b/isbn/{isbn}-L.jpg",
                "openLibraryKey": edition.get("key"),
                "openLibraryWork": key,
                "verified": True,
            }
        )
        return result

    # No usable publisher-matched cover. Keep the edition metadata (it is
    # still a real edition of the book) and fall back to a work cover.
    if trusted:
        _, _, edition, isbn, publisher, key, work_cover = trusted[0]
        result.update(
            {
                "isbn": isbn,
                "publisher": publisher,
                "publicationYear": edition_year(edition),
                "openLibraryKey": edition.get("key"),
                "openLibraryWork": key,
                "verified": True,
                "note": "Edition verified; no cover image served for its ISBN.",
            }
        )

    for cover_id, _ in covered_works:
        if image_ok(f"https://covers.openlibrary.org/b/id/{cover_id}-L.jpg?default=false"):
            result["coverImage"] = f"https://covers.openlibrary.org/b/id/{cover_id}-L.jpg"
            result["note"] = (result["note"] or "") + " Cover taken from the work record."
            return result

    if not result["note"]:
        result["note"] = "No verified cover image; the UI sets the title typographically."
    elif result["coverImage"] is None:
        result["note"] += " The UI sets the title typographically."

    return result


def main():
    resolved = []

    for language, level, title, author, hint, publisher_hints in BOOKS:
        entry = resolve(title, author, hint, publisher_hints)
        entry["language"] = language
        entry["level"] = level
        resolved.append(entry)
        print(
            f"{language:<11} {level:<13} {title[:40]:<42} "
            f"isbn={str(entry['isbn']):<15} cover={'yes' if entry['coverImage'] else 'NO ':<3} "
            f"yr={entry['publicationYear']} pub={entry['publisher']}"
        )
        time.sleep(0.3)

    save_cache()

    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(resolved, handle, indent=2, ensure_ascii=False)

    print(f"\nWrote {OUT}")
    print(f"Covers verified: {sum(1 for b in resolved if b['coverImage'])}/{len(resolved)}")
    print(f"ISBNs verified: {sum(1 for b in resolved if b['isbn'])}/{len(resolved)}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
