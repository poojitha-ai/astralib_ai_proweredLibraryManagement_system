# AstraLib Feature Status Matrix

This document provides a feature-by-feature verification matrix comparing backend capabilities and frontend interfaces for AstraLib — Academic & Institutional Digital Library System.

**Audit Date:** August 2026  
**Status Legend:**
- `COMPLETE`: Fully implemented backend, connected API service, and functional frontend UI.
- `PARTIAL`: Backend logic/endpoint is partially implemented or conditional on external services (e.g. OpenAI keys), with graceful frontend fallback.
- `PLANNED`: UI shell and workflow planned/designed in frontend; backend service/endpoint not yet implemented in codebase.
- `BLOCKED`: Dependency or upstream blocking issue.

---

## 1. Authentication & Role Management

| Feature | Backend Endpoint / Mechanism | Frontend Screen / Route | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **User Registration** | `POST /api/auth/register` | `/register` (Auth Screen) | `COMPLETE` | Creates new user with default `USER` role and encrypted password. |
| **User Login & JWT** | `POST /api/auth/login` | `/login` (Auth Screen) | `COMPLETE` | Issues JWT token, returns userId, name, and role. |
| **Current User Info** | `GET /api/users/me` | Profile & Topbar Header | `COMPLETE` | Decodes JWT identity and fetches user entity. |
| **Role-Based Access** | `JwtAuthenticationFilter`, `SecurityConfig` | Protected & Role Route Guards | `COMPLETE` | Supports `USER`, `LIBRARIAN`, `ADMIN` with custom navigation. |
| **Role Modification** | `POST /api/admin/update-role/{userId}` | Admin User Management | `COMPLETE` | Allows administrators to promote/demote users. |

---

## 2. Library Catalogue & Resource Discovery

| Feature | Backend Endpoint / Mechanism | Frontend Screen / Route | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Book Catalogue (Filtered Search)** | `GET /api/books` | `/catalogue` | `COMPLETE` | Supports title, author, category, year, and keyword filters. |
| **Book Details** | `GET /api/books/{id}` | `/books/:id` | `COMPLETE` | Displays title, authors, category, ISBN, publisher, language, description. |
| **Author Directory** | `GET /api/authors`, `GET /api/authors/{id}` | Catalogue / Librarian Authors | `COMPLETE` | Lists authors with biographies. |
| **Category Directory** | `GET /api/categories` | Catalogue / Librarian Categories | `COMPLETE` | Dynamic category filter badges and selector. |
| **Favorites Management** | `GET/POST/DELETE /api/favorites` | `/favorites` | `COMPLETE` | Add, view, and remove saved resources. |
| **Reading History** | `GET /api/reading-history` | `/history` | `COMPLETE` | Chronological reading activity automatically logged upon reading resources. |
| **Recommendations Engine** | `GET /api/recommendations` | `/recommendations` | `COMPLETE` | Calculates user-book interaction scores and category affinity. |

---

## 3. Intelligent Search & Research Assistant (Milestone 5)

| Feature | Backend Endpoint / Mechanism | Frontend Screen / Route | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Keyword Search** | `GET /api/books?keyword=...` | `/search?mode=keyword` | `COMPLETE` | SQL pattern matching on title, ISBN, description. |
| **Smart (Semantic) Search** | `GET /api/search/semantic` | `/search?mode=semantic` | `COMPLETE` | Cosine similarity against chunked vector records. |
| **Hybrid Search** | `GET /api/search/hybrid` | `/search?mode=hybrid` | `COMPLETE` | Blends vector similarity (70%) and keyword frequency (30%). |
| **General Research Assistant (RAG)** | `POST /api/rag/ask`, `POST /api/rag/query` | `/research` | `COMPLETE` | Retrieves relevant document chunks and synthesizes answer with citations. Gracefully notifies when LLM key is unset. |
| **Document-Specific Q&A (Reader)** | `POST /api/rag/documents/{docId}/ask` | `/reader/:resourceId` | `COMPLETE` | Scoped RAG on active digital document with citation inspector. |

---

## 4. Digital Library & Document Reading

| Feature | Backend Endpoint / Mechanism | Frontend Screen / Route | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Digital Resource Metadata** | `GET /api/resources/{id}` | `/reader/:resourceId` | `COMPLETE` | Loads document record, file path, resource type. |
| **Digital Document Streaming/Download** | `GET /api/resources/file/{id}` | Reader stream & download | `COMPLETE` | Streams local files with proper MIME types. |
| **Digital Resource Ingestion** | `POST /api/resources` (Multipart) | Librarian Digital Resources | `COMPLETE` | Uploads PDF/documents and attaches to catalogue books. |

---

## 5. Administration & Librarian Control

| Feature | Backend Endpoint / Mechanism | Frontend Screen / Route | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Librarian Book Management** | `POST /api/books`, `PUT /api/books/{id}`, `DELETE /api/books/{id}` | `/librarian/books` | `COMPLETE` | Full CRUD operations for library catalog. |
| **Librarian Author Management** | `POST /api/authors`, `PUT /api/authors/{id}`, `DELETE /api/authors/{id}` | `/librarian/authors` | `COMPLETE` | Full CRUD operations for authors. |
| **Librarian Category Management** | `POST /api/categories`, `PUT /api/categories/{id}`, `DELETE /api/categories/{id}` | `/librarian/categories` | `COMPLETE` | Full CRUD operations for categories. |
| **Admin Dataset Ingestion** | `POST /api/admin/load-datasets`, `GET /api/admin/dataset-stats` | `/admin/datasets` | `COMPLETE` | Triggers synthetic dataset loader and provides live stats. |
| **Admin Vector Indexing** | `POST /api/admin/vector-ingest`, `GET /api/admin/vector-stats` | `/admin/ai-config` | `COMPLETE` | Ingests vector records, displays embedding index stats and status. |
| **User Role Administration** | `POST /api/admin/update-role/{userId}` | `/admin/users` | `COMPLETE` | Interactive role upgrade/downgrade for users. |

---

## 6. Planned Modules (Under Development)

| Feature | Backend Status | Frontend Status | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Study Hall (Collaborative Groups & Discussions)** | `NOT IMPLEMENTED` | `PLANNED (UI Shell)` | `PLANNED` | Clean academic interface explaining planned collaboration features. No fake backend mocked. |
| **Real-Time Notification Service** | `NOT IMPLEMENTED` | `PLANNED (UI Center)` | `PLANNED` | Notification center displaying clear empty/system status. |
