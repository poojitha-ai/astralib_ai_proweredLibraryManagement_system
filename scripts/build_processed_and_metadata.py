import json
import os
from datetime import datetime

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE_DIR = os.path.join(PROJECT_ROOT, "data")
PROCESSED_DIR = os.path.join(BASE_DIR, "processed")
os.makedirs(PROCESSED_DIR, exist_ok=True)

def load_json(filepath):
    if os.path.exists(filepath):
        with open(filepath, "r", encoding="utf-8") as f:
            return json.load(f)
    return []

def get_dir_size_mb(directory):
    total = 0
    for root, dirs, files in os.walk(directory):
        for f in files:
            fp = os.path.join(root, f)
            total += os.path.getsize(fp)
    return round(total / (1024 * 1024), 2)

def build_processed():
    print("Building Processed Unified Datasets & Knowledge Graph...")
    books = load_json(os.path.join(BASE_DIR, "books", "books_cleaned.json"))
    papers = load_json(os.path.join(BASE_DIR, "scholarly", "scholarly_cleaned.json"))
    docs = load_json(os.path.join(BASE_DIR, "documents", "documents_full_rag.json"))
    
    # 1. Unified Catalog
    unified = []
    nodes = []
    edges = []
    edge_set = set()
    
    # Authors and Concepts dictionary for KG node deduplication
    kg_authors = {}
    kg_concepts = {}
    
    for b in books:
        r_id = b["resource_id"]
        title = b["title"]
        authors = b.get("authors", [])
        subjects = b.get("subjects", [])
        year = b.get("first_publish_year")
        
        unified.append({
            "resource_id": r_id,
            "title": title,
            "creators": authors,
            "resource_type": "book",
            "publication_year": year,
            "publisher_or_venue": b.get("publisher"),
            "topics": subjects,
            "summary": b.get("description"),
            "identifier": b.get("isbn")
        })
        
        nodes.append({"id": r_id, "label": title[:40], "type": "Book", "year": year})
        
        for a in authors:
            a_id = f"AUTH-{abs(hash(a))%1000000:06d}"
            if a_id not in kg_authors:
                kg_authors[a_id] = {"id": a_id, "label": a, "type": "Author"}
            edge_key = (a_id, r_id, "WRITTEN_BY")
            if edge_key not in edge_set:
                edges.append({"source": a_id, "target": r_id, "relation": "WRITTEN_BY"})
                edge_set.add(edge_key)
                
        for s in subjects:
            s_id = f"TOPIC-{abs(hash(s))%1000000:06d}"
            if s_id not in kg_concepts:
                kg_concepts[s_id] = {"id": s_id, "label": s, "type": "Concept"}
            edge_key = (r_id, s_id, "HAS_TOPIC")
            if edge_key not in edge_set:
                edges.append({"source": r_id, "target": s_id, "relation": "HAS_TOPIC"})
                edge_set.add(edge_key)

    for p in papers:
        r_id = p["resource_id"]
        title = p["title"]
        authors = [a["name"] for a in p.get("authors", [])] if isinstance(p.get("authors"), list) and len(p.get("authors")) > 0 and isinstance(p.get("authors")[0], dict) else p.get("authors", [])
        concepts = [c["name"] for c in p.get("concepts", [])] if isinstance(p.get("concepts"), list) and len(p.get("concepts")) > 0 and isinstance(p.get("concepts")[0], dict) else p.get("concepts", [])
        year = p.get("publication_year")
        
        unified.append({
            "resource_id": r_id,
            "title": title,
            "creators": authors,
            "resource_type": "paper",
            "publication_year": year,
            "publisher_or_venue": p.get("venue"),
            "topics": concepts,
            "summary": p.get("abstract"),
            "identifier": p.get("doi")
        })
        
        nodes.append({"id": r_id, "label": title[:40], "type": "Paper", "year": year})
        
        for a in authors:
            a_id = f"AUTH-{abs(hash(a))%1000000:06d}"
            if a_id not in kg_authors:
                kg_authors[a_id] = {"id": a_id, "label": a, "type": "Author"}
            edge_key = (a_id, r_id, "WRITTEN_BY")
            if edge_key not in edge_set:
                edges.append({"source": a_id, "target": r_id, "relation": "WRITTEN_BY"})
                edge_set.add(edge_key)
                
        for c in concepts:
            c_id = f"TOPIC-{abs(hash(c))%1000000:06d}"
            if c_id not in kg_concepts:
                kg_concepts[c_id] = {"id": c_id, "label": c, "type": "Concept"}
            edge_key = (r_id, c_id, "HAS_TOPIC")
            if edge_key not in edge_set:
                edges.append({"source": r_id, "target": c_id, "relation": "HAS_TOPIC"})
                edge_set.add(edge_key)
                
        for ref_id in p.get("referenced_works", [])[:3]:
            target_ref = f"PAPER-{ref_id}"
            edge_key = (r_id, target_ref, "CITES")
            if edge_key not in edge_set:
                edges.append({"source": r_id, "target": target_ref, "relation": "CITES"})
                edge_set.add(edge_key)

    for d in docs:
        r_id = d["document_id"]
        title = d["title"]
        authors = d.get("authors", [])
        categories = d.get("categories", [])
        year = int(d.get("publication_date", "2024")[:4])
        
        unified.append({
            "resource_id": r_id,
            "title": title,
            "creators": authors,
            "resource_type": "document",
            "publication_year": year,
            "publisher_or_venue": "arXiv / Open Access",
            "topics": categories,
            "summary": d.get("abstract"),
            "identifier": d.get("arxiv_id")
        })

    all_nodes = nodes + list(kg_authors.values()) + list(kg_concepts.values())
    
    # Save Unified Catalog
    with open(os.path.join(PROCESSED_DIR, "unified_catalog.json"), "w", encoding="utf-8") as f:
        json.dump(unified, f, indent=2, ensure_ascii=False)
        
    with open(os.path.join(PROCESSED_DIR, "knowledge_graph_nodes.json"), "w", encoding="utf-8") as f:
        json.dump(all_nodes, f, indent=2, ensure_ascii=False)
        
    with open(os.path.join(PROCESSED_DIR, "knowledge_graph_edges.json"), "w", encoding="utf-8") as f:
        json.dump(edges, f, indent=2, ensure_ascii=False)

    # 2. Extract sample RAG Chunks
    rag_chunks = []
    for d in docs[:20]: # sample top 20 docs chunking
        for chk in d.get("chunks", []):
            rag_chunks.append({
                "chunk_id": chk["chunk_id"],
                "document_id": d["document_id"],
                "document_title": d["title"],
                "section": chk["section"],
                "text": chk["content"],
                "token_count": chk["token_count"]
            })
            
    with open(os.path.join(PROCESSED_DIR, "rag_chunks_sample.json"), "w", encoding="utf-8") as f:
        json.dump(rag_chunks, f, indent=2, ensure_ascii=False)

    print(f"Processed Catalog Built: {len(unified)} items, {len(all_nodes)} KG nodes, {len(edges)} KG edges, {len(rag_chunks)} sample RAG chunks.")

def create_metadata_files():
    print("Writing metadata.json files for every dataset directory...")
    timestamp = datetime.now().isoformat()
    
    # Books metadata
    books_meta = {
        "dataset_name": "Open Library Curated Book Corpus",
        "category": "A. BOOK/CATALOGUE DATA",
        "source": "Open Library (Internet Archive)",
        "source_url": "https://openlibrary.org/developers/api",
        "license": "CC0 1.0 Universal / Public Domain",
        "last_updated": timestamp,
        "record_count": 3000,
        "files": ["books_raw.json", "books_cleaned.json"],
        "storage_size_mb": get_dir_size_mb(os.path.join(BASE_DIR, "books")),
        "schema_fields": ["resource_id", "open_library_key", "title", "authors", "first_publish_year", "publisher", "subjects", "isbn", "language", "description"]
    }
    with open(os.path.join(BASE_DIR, "books", "metadata.json"), "w", encoding="utf-8") as f:
        json.dump(books_meta, f, indent=2)
        
    # Scholarly metadata
    scholarly_meta = {
        "dataset_name": "OpenAlex Scholarly Works Corpus",
        "category": "B. SCHOLARLY DATA",
        "source": "OpenAlex",
        "source_url": "https://api.openalex.org/works",
        "license": "CC0 1.0 Universal",
        "last_updated": timestamp,
        "record_count": 3000,
        "files": ["scholarly_raw.json", "scholarly_cleaned.json"],
        "storage_size_mb": get_dir_size_mb(os.path.join(BASE_DIR, "scholarly")),
        "schema_fields": ["resource_id", "openalex_id", "doi", "title", "publication_year", "authors", "concepts", "cited_by_count", "referenced_works", "abstract", "venue", "pdf_url"]
    }
    with open(os.path.join(BASE_DIR, "scholarly", "metadata.json"), "w", encoding="utf-8") as f:
        json.dump(scholarly_meta, f, indent=2)

    # Documents metadata
    docs_meta = {
        "dataset_name": "arXiv Full-Text Open Access Document Corpus",
        "category": "C. DOCUMENT DATA",
        "source": "arXiv API & Open Access Repository",
        "source_url": "https://export.arxiv.org/api/query",
        "license": "CC BY 4.0 / Public Domain",
        "last_updated": timestamp,
        "record_count": 100,
        "files": ["documents_catalog.json", "documents_full_rag.json", "extracted_text/*.md"],
        "storage_size_mb": get_dir_size_mb(os.path.join(BASE_DIR, "documents")),
        "schema_fields": ["document_id", "arxiv_id", "title", "authors", "publication_date", "categories", "abstract", "pdf_url", "extracted_text_path", "chunks"]
    }
    with open(os.path.join(BASE_DIR, "documents", "metadata.json"), "w", encoding="utf-8") as f:
        json.dump(docs_meta, f, indent=2)

    # Users metadata
    users_meta = {
        "dataset_name": "AstraLib Synthetic User Profiles & Activity Logs",
        "category": "D. SYNTHETIC USER DATA",
        "source": "AstraLib Synthetic Activity Generator",
        "license": "MIT / Open Project License (Zero PII)",
        "last_updated": timestamp,
        "record_counts": {
            "users": 200,
            "user_interests": 200,
            "reading_history": 5000,
            "favorites": 1500,
            "search_history": 3000,
            "interactions": 8000
        },
        "files": ["users.json", "user_interests.json", "reading_history.json", "favorites.json", "search_history.json", "interactions.json"],
        "storage_size_mb": get_dir_size_mb(os.path.join(BASE_DIR, "users")),
        "schema_fields": ["user_id", "username", "email", "full_name", "role", "department", "academic_level", "created_at"]
    }
    with open(os.path.join(BASE_DIR, "users", "metadata.json"), "w", encoding="utf-8") as f:
        json.dump(users_meta, f, indent=2)

    # Processed metadata
    proc_meta = {
        "dataset_name": "AstraLib Processed & Graph Unified Index",
        "category": "PROCESSED INDEX",
        "source": "AstraLib Data Normalization Pipeline",
        "last_updated": timestamp,
        "files": ["unified_catalog.json", "knowledge_graph_nodes.json", "knowledge_graph_edges.json", "rag_chunks_sample.json"],
        "storage_size_mb": get_dir_size_mb(os.path.join(BASE_DIR, "processed"))
    }
    with open(os.path.join(BASE_DIR, "processed", "metadata.json"), "w", encoding="utf-8") as f:
        json.dump(proc_meta, f, indent=2)

def generate_readme():
    readme_path = os.path.join(BASE_DIR, "DATASET_README.md")
    content = f"""# AstraLib Dataset Documentation & Specification

**Project:** AstraLib - AI-Powered Universal Knowledge Discovery and Library Management Platform  
**Scope:** 7-Week Academic Project Data Engineering Phase  
**Generated Date:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}  

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
"""
    with open(readme_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"DATASET_README.md written to {readme_path}")

def main():
    build_processed()
    create_metadata_files()
    generate_readme()

if __name__ == "__main__":
    main()
