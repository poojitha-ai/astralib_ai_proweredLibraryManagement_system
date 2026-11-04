#!/usr/bin/env python
"""
Build docs/COLLECTION_INVENTORY.md from the live AstraLib database.

The inventory is derived from real rows, never from the manifest alone:
a book counts as "fully indexed" only when it has stored passages and
every stored embedding is 384-dimensional.

Usage:
    DB_PASSWORD=... python catalogue/build_inventory.py

Credentials come from the environment (DB_USERNAME, DB_PASSWORD,
DB_NAME) exactly like the application's own configuration. Nothing is
hardcoded and nothing secret is written to the output.
"""

import glob
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "docs" / "COLLECTION_INVENTORY.md"
VALIDATION = ROOT / "catalogue" / "search_validation.json"
EXPECTED_DIMENSION = 384


def mysql_binary():
    candidates = sorted(
        glob.glob("C:/Program Files/MySQL/*/bin/mysql.exe")
        + glob.glob("/usr/bin/mysql")
        + glob.glob("/usr/local/bin/mysql")
    )
    if not candidates:
        sys.exit("mysql client not found; install it or add it to PATH")
    return candidates[-1]


def query(sql):
    env = dict(os.environ)
    env["MYSQL_PWD"] = os.environ.get("DB_PASSWORD", "")
    result = subprocess.run(
        [
            mysql_binary(),
            "-u",
            os.environ.get("DB_USERNAME", "root"),
            "--batch",
            "--raw",
            "--default-character-set=utf8mb4",
            "-N",
            os.environ.get("DB_NAME", "astralib"),
            "-e",
            sql,
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
        check=True,
    )
    rows = []
    for line in result.stdout.splitlines():
        if line.strip():
            rows.append(line.split("\t"))
    return rows


BOOKS_SQL = """
select
  b.id,
  b.title,
  coalesce(a.name, 'Unknown'),
  coalesce(c.name, 'Uncategorized'),
  coalesce(b.publication_year, 0),
  coalesce(b.edition, ''),
  coalesce(b.source_url, ''),
  coalesce(b.licence, ''),
  coalesce(b.resource_id, ''),
  coalesce(dr.file_path, ''),
  (select count(*) from vector_records v where v.document_id = b.resource_id),
  (select count(*) from vector_records v
     where v.document_id = b.resource_id
       and json_length(v.embedding_json) = {dim}),
  (select count(*) from vector_records v
     where v.document_id = b.resource_id
       and json_length(v.embedding_json) <> {dim})
from books b
left join authors a on a.id = b.author_id
left join categories c on c.id = b.category_id
left join digital_resources dr on dr.book_id = b.id
where b.resource_id is not null
  and b.resource_id not like 'BOOK-OL%'
  and b.resource_id not like 'PAPER-%'
order by b.id;
""".format(dim=EXPECTED_DIMENSION)

PATHWAYS_SQL = """
select
  p.id,
  p.slug,
  p.name,
  p.type,
  coalesce(p.subject, ''),
  p.description is not null and p.description <> '',
  count(pb.id)
from pathways p
left join pathway_books pb on pb.pathway_id = p.id
group by p.id
order by p.type, p.name;
"""

MEMBERS_SQL = """
select
  pb.pathway_id,
  pb.position,
  pb.level,
  b.title,
  coalesce(pr.title, ''),
  coalesce(pb.rationale, ''),
  coalesce(pb.learning_outcome, '')
from pathway_books pb
join books b on b.id = pb.book_id
left join books pr on pr.id = pb.prerequisite_book_id
order by pb.pathway_id, pb.position;
"""


def esc(value):
    return str(value).replace("|", "\\|")


def main():
    books = query(BOOKS_SQL)
    pathways = query(PATHWAYS_SQL)
    members = query(MEMBERS_SQL)

    members_by_pathway = {}
    for row in members:
        members_by_pathway.setdefault(row[0], []).append(row)

    validation = []
    if VALIDATION.exists():
        validation = json.loads(VALIDATION.read_text(encoding="utf-8")).get("checks", [])

    fully_indexed = [b for b in books if int(b[10]) > 0 and int(b[12]) == 0]
    partial = [b for b in books if int(b[10]) > 0 and int(b[12]) > 0]
    pending = [b for b in books if int(b[10]) == 0]
    no_resource = [b for b in books if not b[9]]

    validated_books = {
        check["book"] for check in validation if check.get("verified")
    }

    lines = []
    lines.append("# AstraLib — Curated Collection Inventory")
    lines.append("")
    lines.append(
        "Generated from the live database by `catalogue/build_inventory.py` on "
        + datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
        + "."
    )
    lines.append("")
    lines.append("## Summary")
    lines.append("")
    lines.append(f"- Curated books: **{len(books)}**")
    lines.append(f"- With a saved digital resource: **{len(books) - len(no_resource)}**")
    lines.append(f"- Fully indexed (real text, all embeddings 384-d): **{len(fully_indexed)}**")
    lines.append(f"- Partially indexed: **{len(partial)}**")
    lines.append(f"- Metadata only / not indexed: **{len(pending)}**")
    lines.append(f"- Pathways: **{len(pathways)}**")
    lines.append("")
    lines.append(
        "A book is only counted as fully indexed when it has stored passages "
        "and **every** stored embedding is 384-dimensional. Metadata alone never counts."
    )
    lines.append("")

    lines.append("## Books")
    lines.append("")
    lines.append(
        "| ID | Title | Author | Genre | Year | Passages | 384-d | Status | Search validated |"
    )
    lines.append("|---|---|---|---|---|---|---|---|---|")

    for b in books:
        book_id, title, author, genre, year, _edition, _src, _lic, ref, path, chunks, dims, invalid = b
        chunks, dims, invalid = int(chunks), int(dims), int(invalid)
        if chunks and invalid == 0:
            status = "Fully indexed"
        elif chunks:
            status = "Partial (invalid embeddings)"
        else:
            status = "Not indexed"
        lines.append(
            "| {} | {} | {} | {} | {} | {} | {} | {} | {} |".format(
                book_id,
                esc(title),
                esc(author),
                esc(genre),
                year,
                chunks,
                dims,
                status,
                "yes" if title in validated_books else "—",
            )
        )

    lines.append("")
    lines.append("## Source and licence record")
    lines.append("")
    lines.append("| Title | Edition | Source | Licence | Resource |")
    lines.append("|---|---|---|---|---|")
    for b in books:
        _id, title, _a, _g, _y, edition, src, lic, ref, path, _c, _d, _i = b
        lines.append(
            "| {} | {} | {} | {} | {} |".format(
                esc(title),
                esc(edition),
                esc(src),
                esc(lic),
                esc(path) if path else "—",
            )
        )

    lines.append("")
    lines.append("## Pathways")
    lines.append("")
    for p in pathways:
        pid, slug, name, ptype, subject, _desc, count = p
        lines.append(f"### {name} (`{slug}`)")
        lines.append("")
        lines.append(f"- Type: **{ptype}**")
        lines.append(f"- Subject: {subject}")
        lines.append(f"- Books: {count}")
        lines.append("")
        lines.append("| # | Level | Book | Prerequisite | Rationale | Outcome |")
        lines.append("|---|---|---|---|---|---|")
        for m in members_by_pathway.get(pid, []):
            _pid, position, level, title, prereq, rationale, outcome = m
            lines.append(
                "| {} | {} | {} | {} | {} | {} |".format(
                    position,
                    level,
                    esc(title),
                    esc(prereq) if prereq else "—",
                    esc(rationale),
                    esc(outcome) if outcome else "—",
                )
            )
        lines.append("")

    lines.append("## Search validation")
    lines.append("")
    if validation:
        lines.append("| Query | Expected book | Top hit | Score | Real passage text |")
        lines.append("|---|---|---|---|---|")
        for check in validation:
            lines.append(
                "| {} | {} | {} | {} | {} |".format(
                    esc(check["query"]),
                    esc(check["book"]),
                    esc(check.get("top_hit", "")),
                    check.get("score", ""),
                    "yes" if check.get("verified") else "no",
                )
            )
    else:
        lines.append("No search validation has been recorded yet.")
    lines.append("")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUTPUT}")
    print(
        f"books={len(books)} fully_indexed={len(fully_indexed)} "
        f"partial={len(partial)} pending={len(pending)} pathways={len(pathways)}"
    )


if __name__ == "__main__":
    main()
