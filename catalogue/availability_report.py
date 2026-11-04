#!/usr/bin/env python
"""
Content-availability audit for the curated programming collection.

Every value comes from the live database, never from the manifest:

  METADATA_ONLY      catalogued, but no stored file at all
  RESOURCE_AVAILABLE a file is stored but nothing has been extracted yet
  INGESTION_PENDING  a file is stored, no passages yet, extraction not finished
  INDEXED            passages exist and every embedding is 384-dimensional
  FAILED             the file is missing, unreadable, or extraction failed

Usage:
    DB_PASSWORD=... python catalogue/availability_report.py
    DB_PASSWORD=... python catalogue/availability_report.py --log /tmp/astralib-backend.log
"""

import argparse
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_MD = os.path.join(ROOT, "docs", "PROGRAMMING_AVAILABILITY.md")
OUT_JSON = os.path.join(ROOT, "catalogue", "programming-availability.json")

DIMENSION = 384

# What each format can do in the running application.
READER_SUPPORT = {
    "application/epub+zip": ("EPUB", "Document Reader (epub.js)"),
    "application/pdf": ("PDF", "browser PDF view"),
    "text/html": ("HTML", "browser HTML view"),
}


def mysql_binary():
    candidates = (
        sorted(
            [
                path
                for path in __import__("glob").glob(
                    "C:/Program Files/MySQL/*/bin/mysql.exe"
                )
            ]
        )
        + ["/usr/bin/mysql", "/usr/local/bin/mysql"]
    )

    for candidate in candidates:
        if os.path.exists(candidate):
            return candidate

    sys.exit("mysql client not found")


def query(sql):
    binary = mysql_binary()
    env = dict(os.environ)
    env["MYSQL_PWD"] = os.environ.get("DB_PASSWORD", "")

    result = subprocess.run(
        [
            binary,
            "-u" + os.environ.get("DB_USERNAME", "root"),
            "-D" + os.environ.get("DB_NAME", "astralib"),
            "-N",
            "-B",
            "-e",
            sql,
        ],
        capture_output=True,
        text=True,
        env=env,
    )

    if result.returncode != 0:
        sys.exit("mysql failed: " + result.stderr.strip())

    rows = []

    for line in result.stdout.splitlines():
        if line.strip():
            rows.append(line.split("\t"))

    return rows


def failed_books_from_log(path):
    if not path or not os.path.exists(path):
        return {}

    pattern = re.compile(
        r"(Book file for book (\d+)|No text could be extracted from the [A-Z]+ of book (\d+))"
    )

    failed = {}

    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = pattern.search(line)
            if match:
                failed[int(match.group(2) or match.group(3))] = line.strip()[-160:]

    return failed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", default=None, help="backend log, to detect failures")
    args = parser.parse_args()

    failures = failed_books_from_log(args.log)

    rows = query(
        """
        select b.id,
               replace(replace(b.title, '\\\\t', ' '), '\\\\n', ' '),
               coalesce(a.name, 'Unknown'),
               coalesce(b.resource_id, ''),
               coalesce(b.edition, ''),
               coalesce(b.isbn, ''),
               coalesce(b.source_url, ''),
               coalesce(b.licence, ''),
               coalesce(d.format, ''),
               coalesce(d.id, ''),
               coalesce((select count(*) from vector_records v where v.document_id = b.resource_id), 0),
               coalesce((select count(*) from vector_records v
                         where v.document_id = b.resource_id
                           and json_valid(v.embedding_json)
                           and json_length(v.embedding_json) = %d), 0),
               coalesce((select sum(char_length(v.content)) from vector_records v
                         where v.document_id = b.resource_id), 0)
        from books b
        left join authors a on a.id = b.author_id
        left join digital_resources d on d.book_id = b.id
        where b.category_id = (select id from categories where name = 'Programming' limit 1)
        order by b.title
        """
        % DIMENSION
    )

    entries = []

    for row in rows:
        (
            book_id,
            title,
            author,
            resource_id,
            edition,
            isbn,
            source_url,
            licence,
            farmat,
            resource_id_num,
            chunks,
            valid,
            characters,
        ) = row

        chunks = int(chunks)
        valid = int(valid)
        characters = int(characters)

        levels = query(
            """
            select p.slug, pb.level, pb.position
            from pathway_books pb
            join pathways p on p.id = pb.pathway_id
            join books b on b.id = pb.book_id
            where b.id = %s and p.type = 'LEARNING'
            order by p.slug
            """
            % book_id
        )

        level_text = "; ".join(
            "%s:%s" % (slug, level) for slug, level, _ in levels
        )

        if not farmat:
            status = "METADATA_ONLY"
        elif chunks == 0 and int(book_id) in failures:
            status = "FAILED"
        elif chunks == 0:
            status = "INGESTION_PENDING"
        elif valid == chunks:
            status = "INDEXED"
        else:
            status = "FAILED"

        label, reader = READER_SUPPORT.get(farmat, ("—", "none"))

        entries.append(
            {
                "bookId": int(book_id),
                "title": title,
                "author": author,
                "resourceId": resource_id,
                "edition": edition,
                "isbn": isbn,
                "sourceUrl": source_url,
                "licence": licence,
                "format": farmat,
                "formatLabel": label,
                "readerSupport": reader,
                "level": level_text,
                "chunks": chunks,
                "validEmbeddings": valid,
                "characters": characters,
                "status": status,
                "failure": failures.get(int(book_id), ""),
            }
        )

    counts = {}

    for entry in entries:
        counts[entry["status"]] = counts.get(entry["status"], 0) + 1

    with open(OUT_JSON, "w", encoding="utf-8") as handle:
        json.dump(
            {"total": len(entries), "counts": counts, "books": entries},
            handle,
            indent=2,
        )
        handle.write("\n")

    write_markdown(entries, counts)

    print(
        "%d programming entries: %s"
        % (len(entries), ", ".join("%s=%d" % item for item in sorted(counts.items())))
    )
    print("wrote %s" % os.path.relpath(OUT_MD, ROOT))

    return 0


def write_markdown(entries, counts):
    lines = [
        "# Programming collection — content availability",
        "",
        "Generated from the live database by `catalogue/availability_report.py`.",
        "A metadata record is never counted as an available book.",
        "",
        "## Summary",
        "",
        "| Status | Books |",
        "| --- | --- |",
    ]

    for status in (
        "INDEXED",
        "INGESTION_PENDING",
        "RESOURCE_AVAILABLE",
        "METADATA_ONLY",
        "FAILED",
    ):
        if counts.get(status):
            lines.append("| %s | %d |" % (status, counts[status]))

    lines += [
        "| **Total** | **%d** |" % len(entries),
        "",
        "## Every programming entry",
        "",
        "| Title | Level | Format | Chunks | Valid 384-d | Status |",
        "| --- | --- | --- | --- | --- | --- |",
    ]

    for entry in entries:
        lines.append(
            "| %s | %s | %s | %d | %d | %s |"
            % (
                entry["title"].replace("|", "/"),
                entry["level"] or "—",
                entry["formatLabel"],
                entry["chunks"],
                entry["validEmbeddings"],
                entry["status"],
            )
        )

    lines += [
        "",
        "## Source and licence",
        "",
        "| Title | Edition | Source | Licence |",
        "| --- | --- | --- | --- |",
    ]

    for entry in entries:
        if not entry["sourceUrl"]:
            continue

        lines.append(
            "| %s | %s | %s | %s |"
            % (
                entry["title"].replace("|", "/"),
                (entry["edition"] or "—").replace("|", "/"),
                entry["sourceUrl"],
                entry["licence"].replace("|", "/"),
            )
        )

    readable = [entry for entry in entries if entry["chunks"]]
    lines += [
        "",
        "## Readable files",
        "",
        "%d of %d entries have a stored full text." % (len(readable), len(entries)),
    ]

    if any(entry["failure"] for entry in entries):
        lines += ["", "## Failures", ""]
        for entry in entries:
            if entry["failure"]:
                lines.append("* **%s** — %s" % (entry["title"], entry["failure"]))

    lines.append("")

    os.makedirs(os.path.dirname(OUT_MD), exist_ok=True)

    with open(OUT_MD, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))


if __name__ == "__main__":
    sys.exit(main())
