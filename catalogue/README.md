# Curated collection

`collection.json` is the source of truth for AstraLib's curated library: every
book's metadata, its real source and licence, and the pathways it belongs to.

Applying it is idempotent:

```
POST /api/admin/catalogue/seed        (ADMIN)
```

For each manifest book the seeder

1. finds an existing record by `resourceId`, then ISBN, then exact normalised
   title (a record that already has its own resource is never adopted),
2. imports the EPUB through the normal book-import pipeline when the book is
   missing — which saves the file, registers `digital_resources`, and starts the
   existing per-book ingestion job — or updates metadata in place,
3. replaces each pathway's membership rows with the manifest's sequence,
   levels, prerequisites and rationale.

Re-applying the manifest never re-imports an existing book and never touches
stored vectors. `books` and `digital_resources` are never duplicated.

## Catalogued but not readable (`METADATA_ONLY`)

A manifest entry with `"availability": "METADATA_ONLY"` and no `file` is a book
we describe, classify and level but hold no licensed digital edition of. The
seeder creates or updates the `books` row (setting `catalogue_entry`), gives it
no `digital_resources` record and starts no ingestion, so it can never be
mistaken for an indexed holding.

The catalogue lists those entries alongside readable books and marks them
*Not available yet*; `/api/books?resourceOnly=true` still returns only books a
reader can actually open, while `catalogueOnly=true` returns the published
collection.

The programming-library expansion (`catalogue/programming-books.json`, built by
`resolve_programming_books.py` and merged by `build_programming_manifest.py`)
is entirely of this kind: commercial titles, each with a verified Open Library
ISBN/cover where one exists, and no text. Eloquent JavaScript is the exception —
the collection already holds its CC-licensed EPUB, so the JavaScript pathway
reuses that record instead of cataloguing a duplicate.

## Where the books come from

* **Project Gutenberg** — public domain in the United States
  (`https://www.gutenberg.org/ebooks/<id>`). The eBook edition carries the
  Project Gutenberg License.
* **Open textbooks** — Python for Everybody (CC BY-NC-SA 3.0),
  Eloquent JavaScript (CC BY-NC 3.0), Pro Git (CC BY-NC-SA 3.0),
  Think Python (CC BY-NC-SA 3.0). Each is fetched from its author's official
  distribution.
* **Operating System Concepts (9th ed.)** — a copyrighted, user-provided copy
  retained for private study. It is not redistributed as a public asset and its
  licence field says so.

EPUB binaries are staged in `data/imports/incoming/` and copied by the importer
into `data/fulltext/books/<slug>/`. Both locations are git-ignored; only the
metadata, source URL and licence are versioned.

## Adding a book

1. Add an entry to `books` with `resourceId`, title, author, category, genres,
   subjects, edition, `sourceUrl`, `licence` and the local `file`.
2. Add it to one or more pathways in `pathways`, with `level`, `position`,
   optional `prerequisite` (another `resourceId`) and a `rationale` that
   justifies the level.
3. Download the EPUB into `data/imports/incoming/`.
4. Re-apply the manifest, then regenerate the inventory:

```
DB_PASSWORD=... python catalogue/build_inventory.py
```

## Formats

The ingestion pipeline reads EPUB, PDF and HTML editions through one
contract (`ExtractedSection`), so every format feeds the same
`extraction -> section-aware chunking -> local embedding -> vector_records`
path. There is no second vector table and no second RAG implementation.

* **EPUB** - OPF spine order, chapter-level sections.
* **PDF** - PDFBox text layer, split at chapter and numbered headings, and
  grouped into 20-page windows where no heading is detected. A scanned PDF
  without a text layer yields no sections and the book is reported as failed
  rather than indexed.
* **HTML** - a saved HTML edition is reduced to its article body (scripts,
  navigation, footers and sidebars are dropped) and split at headings. The
  file is the archival copy of the licensed edition, so nothing is fetched at
  ingestion time.

The reader action follows the stored format: EPUB opens in the document
reader, PDF and HTML editions open in the browser. A book with no stored file
is marked unavailable and is never presented as searchable.

## Open textbooks

`fetch_open_textbooks.py` downloads the openly licensed editions and
`open-textbook-sources.json` records, per file, the edition, the official
source and the exact licence (including CC BY-NC and CC BY-ND restrictions).
Each licence was checked on the publisher's or author's own page.

```
python catalogue/fetch_open_textbooks.py            # fetch anything missing
python catalogue/add_open_textbooks.py              # add them to the manifest
```

## Availability audit

`availability_report.py` builds `docs/PROGRAMMING_AVAILABILITY.md` from the
live database. It distinguishes `METADATA_ONLY`, `RESOURCE_AVAILABLE`,
`INGESTION_PENDING`, `INDEXED` and `FAILED`, and only calls a book indexed when
its passages exist and every embedding is 384-dimensional.

```
DB_PASSWORD=... python catalogue/availability_report.py --log <backend log>
ASTRALIB_TOKEN=... python catalogue/validate_search.py --programming
```

## Level vocabulary

* Learning pathways: `BEGINNER`, `INTERMEDIATE`, `ADVANCED`.
* Leisure pathways: `ACCESSIBLE`, `STANDARD`, `COMPLEX` — these describe the
  text (length, syntax, narrative structure), not the reader.

Every membership carries a `rationale`, so a level is a documented assessment
rather than a label chosen to fill a column.

## Verifying the collection

`validate_search.py` asks one grounded question per curated book through the
live semantic and hybrid search APIs and records the result in
`search_validation.json`; `build_inventory.py` then folds that into
`docs/COLLECTION_INVENTORY.md`.

```
ASTRALIB_EMAIL=... ASTRALIB_PASSWORD=... python catalogue/validate_search.py
DB_PASSWORD=... python catalogue/build_inventory.py
```

Queries embed with `LLM_EMBEDDING_PROVIDER`. It must produce the same vectors
as the stored rows — this collection is indexed with the local
`all-MiniLM-L6-v2` service (`http://127.0.0.1:8001/embed`). Pointing query
embedding at a different provider puts queries in a different vector space and
the relevance scores become meaningless.

## Natural-language search evaluation

`evaluate_search.py` runs one fixed set of 32 realistic queries — beginner
requests, concept questions, all six languages, data structures, OOP,
networking, operating systems, machine learning, literature and deliberately
vague phrasing — against the live API and records the intent the server
detected, the expected book, the returned books, their scores, the rank of the
expected book, and whether each returned passage is real prose.

```
ASTRALIB_TOKEN=... python catalogue/evaluate_search.py --label baseline --engines semantic,hybrid
ASTRALIB_TOKEN=... python catalogue/evaluate_search.py --label after --smart
python catalogue/evaluate_search.py --compare baseline after
python catalogue/evaluate_search.py --unindexed-check
```

The comparison writes `docs/SEARCH_EVALUATION_COMPARISON.md`. `--unindexed-check`
searches every metadata-only book by name and fails if one is ever returned as
a passage or marked readable.

`/api/search/smart` answers two different questions and keeps them apart:

* **recommendations** come from `books`, pathway membership and difficulty —
  no embeddings, so a catalogue-only book still gets a correct answer, marked
  as not readable;
* **passages** come from `vector_records` and always carry book, section and
  chunk identifiers. Rows whose content is a title block rather than prose are
  filtered out (`PassageFilter`) and never cited.

A passage must clear an absolute cosine floor *and* a relative floor against
the best candidate for the query, which is why the fixed 0.60 cut on the
original endpoints was replaced for natural language.

The absolute floor was measured, not guessed. `calibrate_threshold.py` runs 20
questions the collection really answers and 10 on subjects it does not hold,
and compares the raw best cosine of each group:

```
ASTRALIB_TOKEN=... python catalogue/calibrate_threshold.py --log ../backend-run9.log
```

```
answerable:   min 0.525  median 0.732  max 0.797   (20 questions)
unanswerable: min 0.250  median 0.383  max 0.520   (10 questions)
```

The two ranges overlap between 0.46 and 0.52, so no single cut is exact. The
floor is 0.46: every genuinely answerable question in that set survives, and
9 of the 10 unanswerable ones are refused with an explicit "the library holds
no passage on this" notice instead of a citation to unrelated prose. The
script reads the backend log because the raw score is logged per query
(`Smart passages for '<query>': ... best semantic ...`).
