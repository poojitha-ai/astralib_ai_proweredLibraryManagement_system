#!/usr/bin/env python
"""
Fetch the open textbooks AstraLib indexes.

Every source here was checked against the publisher's own licence statement;
the licence is recorded next to the download so the catalogue never claims a
right it does not have. Only official sites are contacted: the author or
publisher, an institutional repository, or the Wikimedia API for Wikibooks
books (CC BY-SA 4.0).

Files are staged in data/imports/incoming/ and then imported by the normal
book-import pipeline, which copies them into data/fulltext/books/<slug>/ and
starts the usual chunk -> embed -> vector job.

Usage:
    python catalogue/fetch_open_textbooks.py            # all missing files
    python catalogue/fetch_open_textbooks.py --force    # re-download
"""

import argparse
import html
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INCOMING = os.path.join(ROOT, "data", "imports", "incoming")
REPORT = os.path.join(ROOT, "catalogue", "open-textbook-sources.json")

USER_AGENT = "AstraLibCatalogueBot/1.0 (library collection maintenance)"

# ---------------------------------------------------------------- sources
#
# `kind` drives how the file is assembled:
#   pdf        - a single PDF downloaded verbatim
#   page       - one HTML page saved verbatim (already the whole book)
#   articles   - several chapter pages, only each page's <article> kept
#   wikibooks  - every subpage of a Wikibooks book via the MediaWiki API
#
SOURCES = [
    {
        "key": "THINK-JAVA-2E-OEW",
        "title": "Think Java: How to Think Like a Computer Scientist (2nd Edition)",
        "author": "Allen B. Downey and Chris Mayfield",
        "language": "Java",
        "level": "BEGINNER",
        "kind": "pdf",
        "file": "think-java-2e.pdf",
        "urls": ["https://greenteapress.com/thinkjava7/thinkjava2.pdf"],
        "sourceUrl": "https://greenteapress.com/wp/think-java-2e/",
        "licence": "CC BY-NC-SA 4.0 (Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International), as stated by Green Tea Press. Non-commercial use only.",
        "edition": "2nd edition, version 7.1.0 (2020)",
        "min_bytes": 500_000,
    },
    {
        "key": "JJJ-JAVA-3E-OEW",
        "title": "Java, Java, Java: Object-Oriented Problem Solving (3rd Edition)",
        "author": "Ralph Morelli and Ralph Walde",
        "language": "Java",
        "level": "INTERMEDIATE",
        "kind": "pdf",
        "file": "java-java-java-3e.pdf",
        "urls": ["https://www.cs.trincoll.edu/~ram/jjj/jjj-os-20170625.pdf"],
        "sourceUrl": "https://www.cs.trincoll.edu/~ram/jjj/",
        "licence": "CC BY 4.0 (Creative Commons Attribution 4.0 International), as stated by the authors. Redistribution permitted with attribution.",
        "edition": "3rd edition, open-source PDF revised 2017-06-25",
        "min_bytes": 1_000_000,
    },
    {
        "key": "WIKIBOOKS-JAVA-OEW",
        "title": "Java Programming",
        "author": "Wikibooks contributors",
        "language": "Java",
        "level": "ADVANCED",
        "kind": "wikibooks",
        "file": "wikibooks-java-programming.html",
        "root": "Java_Programming",
        "sourceUrl": "https://en.wikibooks.org/wiki/Java_Programming",
        "licence": "CC BY-SA 4.0 and GFDL (dual), per Wikibooks:Copyrights.",
        "edition": "Wikibooks live edition, snapshot of the chapter set at download time",
        "min_bytes": 300_000,
    },
    {
        "key": "PYTHON-MASTERY-OEW",
        "title": "Advanced Python Mastery",
        "author": "David Beazley",
        "language": "Python",
        "level": "ADVANCED",
        "kind": "pdf",
        "file": "advanced-python-mastery.pdf",
        "urls": [
            "https://github.com/dabeaz-course/python-mastery/raw/main/PythonMastery.pdf"
        ],
        "sourceUrl": "https://github.com/dabeaz-course/python-mastery",
        "licence": "CC BY-SA 4.0 (Creative Commons Attribution-ShareAlike 4.0 International), as stated in the course README.",
        "edition": "Course edition (2023-2026), Python 3",
        "min_bytes": 1_000_000,
    },
    {
        "key": "WIKIBOOKS-C-OEW",
        "title": "C Programming",
        "author": "Wikibooks contributors",
        "language": "C",
        "level": "BEGINNER",
        "kind": "wikibooks",
        "file": "wikibooks-c-programming.html",
        "root": "C_Programming",
        "sourceUrl": "https://en.wikibooks.org/wiki/C_Programming",
        "licence": "CC BY-SA 4.0 and GFDL (dual), per Wikibooks:Copyrights.",
        "edition": "Wikibooks live edition, snapshot of the chapter set at download time",
        "min_bytes": 200_000,
    },
    {
        "key": "MODERN-C-OEW",
        "title": "Modern C (C23 Edition)",
        "author": "Jens Gustedt",
        "language": "C",
        "level": "INTERMEDIATE",
        "kind": "pdf",
        "file": "modern-c-c23.pdf",
        "urls": ["https://inria.hal.science/hal-02383654v2/file/modernC.pdf"],
        "sourceUrl": "https://gustedt.gitlabpages.inria.fr/modern-c/",
        "licence": "CC BY-NC-ND 4.0 (Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International), as stated by the author. Verbatim non-commercial use only.",
        "edition": "C23 edition, October 2024",
        "min_bytes": 1_000_000,
    },
    {
        "key": "PROGRAMMING-FUNDAMENTALS-CPP-OEW",
        "title": "Programming Fundamentals: A Modular Structured Approach Using C++",
        "author": "Kenneth Leroy Busbee and Dave Braunschweig",
        "language": "C++",
        "level": "BEGINNER",
        "kind": "pdf",
        "file": "programming-fundamentals-cpp.pdf",
        "urls": [
            "https://openlibrary-repo.ecampusontario.ca/jspui/bitstream/123456789/692/4/"
            "Programming-Fundamentals-1570222282._print.pdf"
        ],
        "sourceUrl": "https://open.umn.edu/opentextbooks/textbooks/144",
        "licence": "CC BY-SA 4.0 (Creative Commons Attribution-ShareAlike 4.0 International), as stated on the copyright page of the eCampusOntario Open Library copy.",
        "edition": "2nd edition, eCampusOntario Open Library copy (2019)",
        "min_bytes": 1_000_000,
    },
    {
        "key": "THINK-CPP-OEW",
        "title": "Think C++: How to Think Like a Computer Scientist",
        "author": "Allen B. Downey",
        "language": "C++",
        "level": "INTERMEDIATE",
        "kind": "pdf",
        "file": "think-cpp.pdf",
        "urls": ["https://greenteapress.com/thinkcpp/thinkCScpp.pdf"],
        "sourceUrl": "https://greenteapress.com/wp/think-c/",
        "licence": "CC BY-NC-SA 4.0 (Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International), as stated by Green Tea Press. Non-commercial use only.",
        "edition": "Green Tea Press edition",
        "min_bytes": 300_000,
    },
    {
        "key": "THE-JAVASCRIPT-WAY-OEW",
        "title": "The JavaScript Way",
        "author": "Baptiste Pesquet",
        "language": "JavaScript",
        "level": "INTERMEDIATE",
        "kind": "articles",
        "file": "the-javascript-way.html",
        "urls": [
            "https://thejsway.net/chapter%02d/" % n for n in range(1, 17)
        ] + ["https://thejsway.net/appendix02/"],
        "sourceUrl": "https://thejsway.net/",
        "licence": "CC BY-NC-SA 4.0 (Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International), per the book's LICENSE file. Non-commercial use only.",
        "edition": "Online edition, 2018-2024",
        "min_bytes": 200_000,
    },
    {
        "key": "THE-RUST-PROGRAMMING-LANGUAGE-OEW",
        "title": "The Rust Programming Language",
        "author": "Steve Klabnik, Carol Nichols and the Rust community",
        "language": "Rust",
        "level": "BEGINNER",
        "kind": "page",
        "file": "the-rust-programming-language.html",
        "urls": ["https://doc.rust-lang.org/book/print.html"],
        "sourceUrl": "https://doc.rust-lang.org/book/",
        "licence": "MIT OR Apache-2.0, as stated in the rust-lang/book repository. Redistribution permitted.",
        "edition": "Current online edition (mdBook)",
        "min_bytes": 500_000,
    },
]


def http_get(url, timeout=120, attempts=4):
    """GET with backoff. Wikimedia answers 429 when a crawl is too eager."""
    delay = 2.0

    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})

        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == attempts:
                raise
            print("    ~ rate limited, retrying in %.0fs" % delay)
            time.sleep(delay)
            delay *= 2

    raise RuntimeError("unreachable")


def wiki_parse(title):
    """Parsed HTML of one page, throttled and retried."""
    params = {
        "action": "parse",
        "page": title,
        "prop": "text",
        "disableeditsection": "1",
        "format": "json",
        "formatversion": "2",
    }
    url = "https://en.wikibooks.org/w/api.php?" + urllib.parse.urlencode(params)
    return json.loads(http_get(url).decode("utf-8"))


def wiki_page(title, render=False):
    """One Wikibooks page, via the ordinary index.php endpoint."""
    params = {"title": title}

    if render:
        # action=render returns only the article body, without site chrome.
        params["action"] = "render"

    url = "https://en.wikibooks.org/w/index.php?" + urllib.parse.urlencode(params)

    for attempt in range(1, 5):
        try:
            return http_get(url).decode("utf-8", "replace")
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 4:
                raise
            pause = 10 * attempt
            print("    ~ rate limited, waiting %ds" % pause)
            time.sleep(pause)

    raise RuntimeError("unreachable")


def wikibooks_subpages(root):
    """All subpages of a Wikibooks book, via the MediaWiki query API."""
    titles = []
    params = {
        "action": "query",
        "list": "allpages",
        "apprefix": root.replace(" ", "_") + "/",
        "aplimit": "500",
        "format": "json",
    }

    while True:
        url = "https://en.wikibooks.org/w/api.php?" + urllib.parse.urlencode(params)

        for attempt in range(1, 6):
            try:
                data = json.loads(http_get(url).decode("utf-8"))
                break
            except urllib.error.HTTPError as error:
                if error.code != 429 or attempt == 5:
                    raise
                pause = 15 * attempt
                print("    ~ listing rate limited, waiting %ds" % pause)
                time.sleep(pause)

        titles.extend(
            page["title"] for page in data.get("query", {}).get("allpages", [])
        )

        if "continue" not in data:
            break

        params.update(data["continue"])

    return titles


def wikibooks_chapters(root):
    """
    Chapters of a Wikibooks book.

    Deep subpages under a chapter (".../C99_Reference/wchar.h/wcscat") are
    individual reference stubs, not chapters, so only the book's own chapter
    level is kept. If a book is organised more deeply than that, one extra
    level is allowed so the book is not reduced to a handful of pages.
    """
    titles = [
        title
        for title in wikibooks_subpages(root)
        if "/Print_version" not in title and "/Development" not in title
    ]

    def depth(title):
        return title.count("/")

    root_depth = root.replace(" ", "_").count("/")
    candidates = [t for t in titles if depth(t) == root_depth + 1]

    if len(candidates) < 10:
        candidates = [t for t in titles if depth(t) <= root_depth + 2]

    return candidates


def wikibooks_book(root):
    parts = []

    for index, title in enumerate([root.replace("_", " ")] + wikibooks_chapters(root)):

        """
        A missing chapter would silently truncate the book, so any
        failure aborts the download instead of indexing a partial text.
        """
        body = wiki_page(title.replace(" ", "_"), render=True)

        if not body.strip():
            raise RuntimeError("chapter came back empty: %s" % title)

        # A stub with no real prose contributes nothing; skip it.
        if len(re.sub(r"<[^>]+>", "", body).split()) < 80:
            continue

        parts.append("<h1>%s</h1>\n%s\n" % (html.escape(title), body))

        if index and index % 20 == 0:
            print("    ... %d chapters" % index)

        time.sleep(0.25)

    if not parts:
        raise RuntimeError("no chapters retrieved for %s" % root)

    return (
        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        "<title>%s</title></head><body><article>%s</article></body></html>"
        % (html.escape(root.replace("_", " ")), "\n".join(parts))
    )


ARTICLE_START = re.compile(r"<article\b", re.IGNORECASE)
ARTICLE_TAG = re.compile(r"</?article\b", re.IGNORECASE)


def slice_article(page):
    """Return the inner HTML of the first <article> element, or the whole page."""
    start_match = ARTICLE_START.search(page)

    if not start_match:
        return page

    depth = 0
    position = start_match.start()

    for tag in ARTICLE_TAG.finditer(page, position):
        if tag.group(0).startswith("</"):
            depth -= 1
            if depth == 0:
                inner_start = page.find(">", start_match.start()) + 1
                return page[inner_start:tag.start()]
        else:
            depth += 1

    return page


def articles_book(urls):
    parts = []

    for url in urls:
        try:
            page = http_get(url).decode("utf-8", "replace")
        except Exception as error:                      # noqa: BLE001
            print("    ! skipped %s: %s" % (url, error))
            continue

        body = slice_article(page)
        if not body.strip():
            continue

        parts.append(body)

    if not parts:
        raise RuntimeError("no chapter pages retrieved")

    return (
        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        "<title>Chapters</title></head><body><article>%s</article></body></html>"
        % "\n".join(parts)
    )


def build(source):
    kind = source["kind"]

    if kind == "pdf":
        data = http_get(source["urls"][0])
        if not data.lstrip()[:4] == b"%PDF":
            raise RuntimeError("response is not a PDF")
        return data, "pdf"

    if kind == "page":
        page = http_get(source["urls"][0]).decode("utf-8", "replace")
        return page.encode("utf-8"), "html"

    if kind == "articles":
        return articles_book(source["urls"]).encode("utf-8"), "html"

    if kind == "wikibooks":
        return wikibooks_book(source["root"]).encode("utf-8"), "html"

    raise RuntimeError("unknown source kind: %s" % kind)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--only", default=None, help="fetch a single key")
    args = parser.parse_args()

    os.makedirs(INCOMING, exist_ok=True)

    report = []

    for source in SOURCES:

        if args.only and source["key"] != args.only:
            continue

        target = os.path.join(INCOMING, source["file"])
        entry = {k: source[k] for k in source if k not in ("urls", "min_bytes")}
        entry["file"] = "data/imports/incoming/" + source["file"]

        if "urls" in source:
            entry["downloadUrls"] = source["urls"]
        else:
            entry["downloadUrls"] = [
                "https://en.wikibooks.org/wiki/" + source["root"]
            ]

        if os.path.exists(target) and not args.force:
            size = os.path.getsize(target)
            entry["status"] = "PRESENT"
            entry["bytes"] = size
            report.append(entry)
            print("= %-42s present  %8d bytes" % (source["key"], size))
            continue

        print("- %-42s fetching" % source["key"])

        try:
            data, detected = build(source)
        except Exception as error:                      # noqa: BLE001
            entry["status"] = "FAILED"
            entry["error"] = str(error)
            report.append(entry)
            print("  ! failed: %s" % error)
            continue

        if len(data) < source["min_bytes"]:
            entry["status"] = "FAILED"
            entry["error"] = "only %d bytes, expected at least %d" % (
                len(data),
                source["min_bytes"],
            )
            report.append(entry)
            print("  ! %s" % entry["error"])
            continue

        with open(target, "wb") as handle:
            handle.write(data)

        entry["status"] = "DOWNLOADED"
        entry["bytes"] = len(data)
        entry["detectedFormat"] = detected
        report.append(entry)
        print("  + saved %s (%d bytes)" % (os.path.basename(target), len(data)))

    with open(REPORT, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2)
        handle.write("\n")

    failed = [item for item in report if item["status"] == "FAILED"]

    print("\n%d sources, %d failed -> %s" % (
        len(report), len(failed), os.path.relpath(REPORT, ROOT)
    ))

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
