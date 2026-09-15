# AstraLib Dataset Documentation & Specification

**Project:** AstraLib - AI-Powered Universal Knowledge Discovery and Library Management Platform  
**Scope:** 7-Week Academic Project Data Engineering Phase  
**Generated Date:** 2026-08-01 22:26:54  

---

## Executive Overview

This directory contains the complete, clean, normalized, and synthetic datasets required to power all **12 core features** of AstraLib. The dataset suite is strictly scoped for academic demonstration, maintaining high analytical depth while avoiding excessive download footprints.

Total Data Size across all categories: **~12–15 MB** (uncompressed JSON/markdown).

---

## Dataset Summaries

### 1. Category A: Book / Catalogue Data (`AstraLib/data/books/`)
- **Dataset Name:** Open Library Curated CS & AI Book Corpus
- **Source:** [Open Library REST API](https://openlibrary.org/developers/api)
- **License:** CC0 1.0 Universal / Public Domain
- **Record Count:** 3,000 book catalogue entries
- **Files:** `books_raw.json`, `books_cleaned.json`, `metadata.json`
- **Key Fields:** `resource_id`, `open_library_key`, `title`, `authors`, `first_publish_year`, `publisher`, `subjects`, `isbn`, `cover_url`, `description`
- **AstraLib Features Served:**
  - Traditional Library Management (Feature 1)
  - Keyword Search (Feature 2)
  - Semantic Search (Feature 3)
  - Knowledge Graph Visualization (Feature 8)
  - Reading History & Favorites (Feature 11)

---

### 2. Category B: Scholarly Data (`AstraLib/data/scholarly/`)
- **Dataset Name:** OpenAlex Computer Science & AI Works Corpus
- **Source:** [OpenAlex REST API](https://api.openalex.org/)
- **License:** CC0 1.0 Universal
- **Record Count:** 3,000 scholarly paper metadata entries
- **Files:** `scholarly_raw.json`, `scholarly_cleaned.json`, `metadata.json`
- **Key Fields:** `resource_id`, `openalex_id`, `doi`, `title`, `publication_year`, `authors`, `concepts`, `cited_by_count`, `referenced_works`, `abstract`, `venue`, `pdf_url`
- **AstraLib Features Served:**
  - Keyword Search (Feature 2)
  - Semantic Search (Feature 3)
  - Knowledge Graph Citation & Concept Visualization (Feature 8)
  - Personalized Recommendations (Feature 7)
  - Search Analytics (Feature 12)

---

### 3. Category C: Full-Text Document Data (`AstraLib/data/documents/`)
- **Dataset Name:** arXiv Open-Access AI/CS Research Corpus & Technical Classics
- **Source:** [arXiv API](https://export.arxiv.org/api/query)
- **License:** CC BY 4.0 / Public Domain
- **Record Count:** 100 high-quality full-text documents
- **Files & Subdirectories:**
  - `documents_catalog.json`: Catalogue metadata for all 100 documents.
  - `documents_full_rag.json`: Metadata paired with full section chunks for vector ingestion.
  - `extracted_text/*.md`: 100 clean, structured Markdown files ready for LLM chunking.
  - `metadata.json`: Category specification.
- **Key Fields:** `document_id`, `arxiv_id`, `title`, `authors`, `publication_date`, `categories`, `abstract`, `chunks` (`chunk_id`, `section`, `content`, `token_count`)
- **AstraLib Features Served:**
  - RAG-Based Document Question Answering (Feature 4)
  - AI Document Summarization (Feature 5)
  - AI Chat Assistant (Feature 6)
  - Digital Study Hall (Feature 10)
  - Multilingual Interaction (Feature 9)

---

### 4. Category D: Synthetic User Data (`AstraLib/data/users/`)
- **Dataset Name:** AstraLib Synthetic User Profile & Interaction Logs
- **Source:** Deterministic Python generator (`AstraLib/scripts/build_synthetic_users.py`)
- **License:** MIT / Open Project (100% Synthetic, Zero PII)
- **Record Counts:**
  - `users.json`: 200 user profiles (Students, Researchers, Faculty)
  - `user_interests.json`: 200 user interest topic mappings
  - `reading_history.json`: 5,000 reading session logs
  - `favorites.json`: 1,500 bookmarked resources
  - `search_history.json`: 3,000 user search query logs
  - `interactions.json`: 8,000 granular interaction events (views, downloads, RAG queries, citations)
- **AstraLib Features Served:**
  - Personalized Recommendations (Feature 7)
  - Digital Study Hall (Feature 10)
  - Reading History and Favorites (Feature 11)
  - Search Analytics (Feature 12)

---

### 5. Category E: Unified Processed & Graph Data (`AstraLib/data/processed/`)
- **Unified Catalogue:** `unified_catalog.json` (6,100 unified records across books, papers, and docs)
- **Knowledge Graph Nodes:** `knowledge_graph_nodes.json` (Books, Papers, Authors, Concepts)
- **Knowledge Graph Edges:** `knowledge_graph_edges.json` (WRITTEN_BY, HAS_TOPIC, CITES)
- **RAG Chunk Samples:** `rag_chunks_sample.json` (Pre-segmented 500-token chunks with overlap metadata)

---

## Preprocessing & Data Normalization Applied

1. **UTF-8 Sanitization:** Stripped invalid control characters and HTML entities.
2. **Schema Normalization:** Standardized IDs into prefix formats (`BOOK-`, `PAPER-`, `DOC-`, `USR-`, `HIST-`, `SRCH-`, `INT-`).
3. **Citation Graph Reconstruction:** Formatted OpenAlex referenced works and Open Library subject mappings into standardized nodes and edges.
4. **Markdown Chunk Extraction:** Extracted structured Markdown with section headers (`## Introduction`, `## Methodology`, etc.) and pre-calculated token counts for vector stores.
5. **Deduplication:** Filtered duplicate titles, ISBNs, DOIs, and Open Library keys.

---

## Feature Traceability Matrix

| AstraLib Feature | Datasets Used |
| :--- | :--- |
| 1. Traditional Library Management | `books/books_cleaned.json`, `processed/unified_catalog.json` |
| 2. Keyword Search | `books_cleaned.json`, `scholarly_cleaned.json`, `unified_catalog.json` |
| 3. Semantic Search | `books_cleaned.json`, `scholarly_cleaned.json`, `documents_full_rag.json` |
| 4. RAG-based Doc Q&A | `documents/extracted_text/*.md`, `processed/rag_chunks_sample.json` |
| 5. AI Document Summarization | `documents_full_rag.json`, `documents/extracted_text/*.md` |
| 6. AI Chat Assistant | `documents_full_rag.json`, `unified_catalog.json` |
| 7. Personalized Recommendations | `users/user_interests.json`, `users/reading_history.json`, `scholarly_cleaned.json` |
| 8. Knowledge Graph Visualization | `processed/knowledge_graph_nodes.json`, `processed/knowledge_graph_edges.json` |
| 9. Multilingual Interaction | `documents_full_rag.json` |
| 10. Digital Study Hall | `users/users.json`, `users/interactions.json`, `documents_full_rag.json` |
| 11. Reading History & Favorites | `users/reading_history.json`, `users/favorites.json` |
| 12. Search Analytics | `users/search_history.json`, `users/interactions.json` |
