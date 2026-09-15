import React, {
  useEffect,
  useState,
} from 'react';

import {
  Link,
  useSearchParams,
} from 'react-router-dom';

import { api } from '../../services/api';

import {
  Search,
  BookOpen,
} from 'lucide-react';

import {
  Badge,
  LoadingSpinner,
  EmptyState,
} from '../../components/common/Components';

export function SmartSearchPage() {
  const [
    searchParams,
    setSearchParams,
  ] = useSearchParams();

  const [
    query,
    setQuery,
  ] = useState(
    searchParams.get('q') || ''
  );

  const [
    searchMode,
    setSearchMode,
  ] = useState(
    searchParams.get('mode') ||
      'semantic'
  );

  const [
    documentType,
    setDocumentType,
  ] = useState('all');

  const [
    topK,
    setTopK,
  ] = useState(6);

  const [
    results,
    setResults,
  ] = useState([]);

  const [
    loading,
    setLoading,
  ] = useState(false);

  const [
    error,
    setError,
  ] = useState('');

  const [
    semanticUnavailable,
    setSemanticUnavailable,
  ] = useState(false);

  const [
    hasSearched,
    setHasSearched,
  ] = useState(false);

  // =========================================================
  // AUTO SEARCH FROM URL
  // =========================================================

  useEffect(() => {
    const q =
      searchParams.get('q');

    const mode =
      searchParams.get('mode') ||
      'semantic';

    if (q) {
      setQuery(q);
      setSearchMode(mode);

      executeSearch(
        q,
        mode,
        documentType,
        topK
      );
    }
  }, [searchParams]);

  // =========================================================
  // EXECUTE SEARCH
  // =========================================================

  const executeSearch =
    async (
      q,
      mode,
      docType,
      k
    ) => {
      if (!q?.trim()) {
        return;
      }

      setLoading(true);
      setError('');
      setHasSearched(true);

      try {
        // =====================================================
        // KEYWORD SEARCH
        // =====================================================

        if (
          mode === 'keyword'
        ) {
          setSemanticUnavailable(
            false
          );

          const data =
            await api.books.getAll({
              keyword: q.trim(),
            });

          const formatted = (
            Array.isArray(data)
              ? data
              : []
          ).map((book) => ({
            /*
             * Keyword results already come
             * directly from books table.
             */
            documentId:
              String(book.id),

            /*
             * IMPORTANT:
             * Common navigation ID.
             */
            bookId:
              book.id,

            documentType:
              'book',

            title:
              book.title,

            author:
              book.author?.name ||
              'Unknown Author',

            category:
              book.category?.name ||
              'General',

            section:
              null,

            content:
              book.description ||
              'No excerpt available.',

            similarityScore:
              null,

            isBook:
              true,
          }));

          setResults(formatted);

          return;
        }

        // =====================================================
        // HYBRID SEARCH
        // =====================================================

        if (
          mode === 'hybrid'
        ) {
          const data =
            await api.search.hybrid(
              q.trim(),
              k,
              docType === 'all'
                ? ''
                : docType
            );

          if (
            Array.isArray(data)
          ) {
            setSemanticUnavailable(
              false
            );

            setResults(data);
          } else {
            setSemanticUnavailable(
              true
            );

            setResults([]);

            setError(
              data?.error ||
                'Hybrid search unavailable. Please use Keyword Search.'
            );
          }

          return;
        }

        // =====================================================
        // SEMANTIC SEARCH
        // =====================================================

        const data =
          await api.search.semantic(
            q.trim(),
            k,
            docType === 'all'
              ? ''
              : docType
          );

        if (
          Array.isArray(data)
        ) {
          setSemanticUnavailable(
            false
          );

          setResults(data);
        } else {
          setSemanticUnavailable(
            true
          );

          setResults([]);

          setError(
            data?.error ||
              'Semantic search unavailable. Please use Keyword Search.'
          );
        }
      } catch (err) {
        console.error(
          'Search execution error:',
          err
        );

        const message =
          err.message || '';

        if (
          message
            .toLowerCase()
            .includes('embedding') ||
          message
            .toLowerCase()
            .includes('provider') ||
          err.status === 400
        ) {
          setSemanticUnavailable(
            true
          );

          setError(
            'Semantic/Hybrid search requires the configured Gemini embedding service. Please use Keyword Search if the embedding service is unavailable.'
          );
        } else {
          setError(
            message ||
              'Search failed. Please try again.'
          );
        }
      } finally {
        setLoading(false);
      }
    };

  // =========================================================
  // SEARCH FORM
  // =========================================================

  const handleSearchSubmit =
    (event) => {
      event.preventDefault();

      if (!query.trim()) {
        return;
      }

      setSearchParams({
        q: query.trim(),
        mode: searchMode,
      });

      executeSearch(
        query.trim(),
        searchMode,
        documentType,
        topK
      );
    };

  // =========================================================
  // RELEVANCE %
  // =========================================================

  const formatRelevance =
    (score) => {
      if (score == null) {
        return null;
      }

      const percentage =
        Math.round(
          score * 100
        );

      return `${Math.min(
        100,
        Math.max(
          0,
          percentage
        )
      )}%`;
    };

  // =========================================================
  // NAVIGATION HELPERS
  // =========================================================

  const canOpenBook =
    (item) =>
      item?.documentType
        ?.toLowerCase() ===
        'book' &&
      item?.bookId != null;

  const getBookDetailsUrl =
    (item) =>
      canOpenBook(item)
        ? `/books/${item.bookId}`
        : null;

  const getReaderUrl =
    (item) =>
      canOpenBook(item)
        ? `/reader/${item.bookId}`
        : null;

  // =========================================================
  // PAGE
  // =========================================================

  return (
    <div className="page-container">

      {/* =====================================================
          HEADER
      ====================================================== */}

      <div className="page-header">
        <div className="page-title-group">
          <h1>
            Library Knowledge Search
          </h1>

          <p>
            Switch between keyword
            catalogue matching and
            semantic vector retrieval
            across institutional texts.
          </p>
        </div>
      </div>

      {/* =====================================================
          SEARCH CARD
      ====================================================== */}

      <div
        className="card"
        style={{
          marginBottom:
            '2rem',

          padding:
            '1.75rem',
        }}
      >

        {/* ===================================================
            SEARCH MODE TABS
        ==================================================== */}

        <div
          className="tabs-nav"
          style={{
            marginBottom:
              '1.25rem',
          }}
        >
          <button
            type="button"
            className={`tab-btn ${
              searchMode ===
              'semantic'
                ? 'active'
                : ''
            }`}
            onClick={() => {
              setSearchMode(
                'semantic'
              );

              if (
                query.trim()
              ) {
                executeSearch(
                  query,
                  'semantic',
                  documentType,
                  topK
                );
              }
            }}
          >
            Smart (Semantic) Search
          </button>

          <button
            type="button"
            className={`tab-btn ${
              searchMode ===
              'hybrid'
                ? 'active'
                : ''
            }`}
            onClick={() => {
              setSearchMode(
                'hybrid'
              );

              if (
                query.trim()
              ) {
                executeSearch(
                  query,
                  'hybrid',
                  documentType,
                  topK
                );
              }
            }}
          >
            Hybrid Search
            (Vector + Keyword)
          </button>

          <button
            type="button"
            className={`tab-btn ${
              searchMode ===
              'keyword'
                ? 'active'
                : ''
            }`}
            onClick={() => {
              setSearchMode(
                'keyword'
              );

              if (
                query.trim()
              ) {
                executeSearch(
                  query,
                  'keyword',
                  documentType,
                  topK
                );
              }
            }}
          >
            Keyword Search
          </button>
        </div>

        {/* ===================================================
            SEARCH FORM
        ==================================================== */}

        <form
          onSubmit={
            handleSearchSubmit
          }
        >
          <div
            style={{
              display:
                'flex',

              gap:
                '0.75rem',

              flexWrap:
                'wrap',
            }}
          >
            <div
              style={{
                flex:
                  1,

                position:
                  'relative',

                minWidth:
                  '280px',
              }}
            >
              <Search
                size={18}
                style={{
                  position:
                    'absolute',

                  left:
                    '1rem',

                  top:
                    '50%',

                  transform:
                    'translateY(-50%)',

                  color:
                    'var(--text-muted)',
                }}
              />

              <input
                type="text"
                className="form-input"
                style={{
                  paddingLeft:
                    '2.75rem',

                  height:
                    '48px',

                  fontSize:
                    '1rem',
                }}
                placeholder={
                  searchMode ===
                  'semantic'
                    ? 'Describe what you are looking for...'
                    : searchMode ===
                      'hybrid'
                    ? 'Combine meaning and exact terms...'
                    : 'Enter exact keyword, title, author or ISBN...'
                }
                value={query}
                onChange={(
                  event
                ) =>
                  setQuery(
                    event.target
                      .value
                  )
                }
                autoFocus
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{
                height:
                  '48px',

                padding:
                  '0 1.75rem',
              }}
              disabled={
                loading
              }
            >
              {loading
                ? 'Searching...'
                : 'Execute Search'}
            </button>
          </div>

          {/* =================================================
              SEMANTIC/HYBRID FILTERS
          ================================================== */}

          {searchMode !==
            'keyword' && (
            <div
              style={{
                display:
                  'flex',

                gap:
                  '1.25rem',

                marginTop:
                  '1rem',

                alignItems:
                  'center',

                flexWrap:
                  'wrap',
              }}
            >
              <div
                style={{
                  display:
                    'flex',

                  alignItems:
                    'center',

                  gap:
                    '0.5rem',

                  fontSize:
                    '0.85rem',
                }}
              >
                <span
                  style={{
                    fontWeight:
                      600,

                    color:
                      'var(--text-secondary)',
                  }}
                >
                  Target Domain:
                </span>

                <select
                  className="form-select"
                  style={{
                    width:
                      'auto',

                    padding:
                      '0.3rem 0.6rem',

                    fontSize:
                      '0.85rem',
                  }}
                  value={
                    documentType
                  }
                  onChange={(
                    event
                  ) =>
                    setDocumentType(
                      event.target
                        .value
                    )
                  }
                >
                  <option value="all">
                    All Documents & Books
                  </option>

                  <option value="book">
                    Books & Monographs
                  </option>

                  <option value="paper">
                    Scholarly Papers
                  </option>

                  <option value="document">
                    Archival Documents
                  </option>
                </select>
              </div>

              <div
                style={{
                  display:
                    'flex',

                  alignItems:
                    'center',

                  gap:
                    '0.5rem',

                  fontSize:
                    '0.85rem',
                }}
              >
                <span
                  style={{
                    fontWeight:
                      600,

                    color:
                      'var(--text-secondary)',
                  }}
                >
                  Top Results:
                </span>

                <select
                  className="form-select"
                  style={{
                    width:
                      'auto',

                    padding:
                      '0.3rem 0.6rem',

                    fontSize:
                      '0.85rem',
                  }}
                  value={topK}
                  onChange={(
                    event
                  ) =>
                    setTopK(
                      Number(
                        event.target
                          .value
                      )
                    )
                  }
                >
                  <option value={4}>
                    4 results
                  </option>

                  <option value={6}>
                    6 results
                  </option>

                  <option value={10}>
                    10 results
                  </option>

                  <option value={20}>
                    20 results
                  </option>
                </select>
              </div>
            </div>
          )}
        </form>
      </div>

      {/* =====================================================
          SEMANTIC SERVICE WARNING
      ====================================================== */}

      {semanticUnavailable && (
        <div
          className="banner"
          style={{
            background:
              'var(--accent-amber, #fef3c7)',

            borderColor:
              '#f59e0b',

            color:
              '#92400e',

            marginBottom:
              '1rem',
          }}
        >
          <span>
            ⚠️{' '}

            <strong>
              Semantic & Hybrid Search
            </strong>{' '}

            require the configured
            Gemini embedding service.

            <button
              type="button"
              className="btn btn-sm btn-secondary"
              style={{
                marginLeft:
                  '0.5rem',
              }}
              onClick={() => {
                setSearchMode(
                  'keyword'
                );

                setSemanticUnavailable(
                  false
                );

                setError('');

                if (
                  query.trim()
                ) {
                  executeSearch(
                    query,
                    'keyword',
                    documentType,
                    topK
                  );
                }
              }}
            >
              Switch to Keyword Search →
            </button>
          </span>
        </div>
      )}

      {error &&
        !semanticUnavailable && (
          <div className="banner banner-error">
            <span>
              {error}
            </span>
          </div>
        )}

      {/* =====================================================
          RESULTS
      ====================================================== */}

      {loading ? (
        <LoadingSpinner
          message="Scanning semantic vector space..."
        />
      ) : hasSearched &&
        results.length ===
          0 ? (
        <EmptyState
          icon={Search}
          title="No Matching Information Discovered"
          description={`No records matched "${query}". Consider rephrasing your research query or using Keyword Search.`}
        />
      ) : results.length >
        0 ? (
        <div>
          <div
            style={{
              fontSize:
                '0.9rem',

              color:
                'var(--text-secondary)',

              marginBottom:
                '1.25rem',
            }}
          >
            Retrieved{' '}

            <strong>
              {results.length}
            </strong>{' '}

            matching references for{' '}

            <em>
              "{query}"
            </em>
          </div>

          <div
            style={{
              display:
                'flex',

              flexDirection:
                'column',

              gap:
                '1rem',
            }}
          >
            {results.map(
              (
                item,
                index
              ) => {
                const relevanceText =
                  formatRelevance(
                    item.similarityScore
                  );

                const detailsUrl =
                  getBookDetailsUrl(
                    item
                  );

                const readerUrl =
                  getReaderUrl(
                    item
                  );

                return (
                  <div
                    key={
                      item.chunkId ||
                      index
                    }
                    className="card"
                    style={{
                      padding:
                        '1.5rem',
                    }}
                  >
                    <div
                      style={{
                        display:
                          'flex',

                        justifyContent:
                          'space-between',

                        alignItems:
                          'flex-start',

                        gap:
                          '1rem',

                        marginBottom:
                          '0.5rem',
                      }}
                    >
                      <div>
                        <div
                          style={{
                            display:
                              'flex',

                            alignItems:
                              'center',

                            gap:
                              '0.5rem',

                            marginBottom:
                              '0.35rem',

                            flexWrap:
                              'wrap',
                          }}
                        >
                          <Badge variant="primary">
                            {item.documentType
                              ? item.documentType.toUpperCase()
                              : 'DOCUMENT'}
                          </Badge>

                          {item.category && (
                            <Badge variant="default">
                              {item.category}
                            </Badge>
                          )}

                          {item.section && (
                            <span
                              style={{
                                fontSize:
                                  '0.78rem',

                                color:
                                  'var(--text-muted)',
                              }}
                            >
                              Section:{' '}
                              {
                                item.section
                              }
                            </span>
                          )}
                        </div>

                        <h3
                          style={{
                            fontSize:
                              '1.2rem',

                            color:
                              'var(--primary-ink)',

                            margin:
                              0,
                          }}
                        >
                          {detailsUrl ? (
                            <Link
                              to={
                                detailsUrl
                              }
                            >
                              {
                                item.title
                              }
                            </Link>
                          ) : (
                            item.title ||
                            'Untitled Document'
                          )}
                        </h3>

                        {item.author && (
                          <div
                            style={{
                              fontSize:
                                '0.85rem',

                              color:
                                'var(--text-secondary)',

                              marginTop:
                                '0.2rem',
                            }}
                          >
                            By{' '}
                            {
                              item.author
                            }
                          </div>
                        )}
                      </div>

                      {relevanceText && (
                        <Badge
                          variant="gold"
                          style={{
                            fontSize:
                              '0.85rem',

                            padding:
                              '0.3rem 0.6rem',
                          }}
                        >
                          Relevance:{' '}
                          {
                            relevanceText
                          }
                        </Badge>
                      )}
                    </div>

                    {/* =========================================
                        MATCHED PASSAGE
                    ========================================== */}

                    <div
                      style={{
                        padding:
                          '0.85rem 1rem',

                        background:
                          'var(--bg-app)',

                        borderRadius:
                          'var(--radius-sm)',

                        border:
                          '1px solid var(--border-light)',

                        fontSize:
                          '0.9rem',

                        color:
                          'var(--text-primary)',

                        lineHeight:
                          '1.6',

                        margin:
                          '0.75rem 0',
                      }}
                    >
                      {item.content}
                    </div>

                    {/* =========================================
                        RESULT FOOTER
                    ========================================== */}

                    <div
                      style={{
                        display:
                          'flex',

                        justifyContent:
                          'space-between',

                        alignItems:
                          'center',

                        paddingTop:
                          '0.5rem',

                        gap:
                          '1rem',

                        flexWrap:
                          'wrap',
                      }}
                    >
                      <span
                        style={{
                          fontSize:
                            '0.75rem',

                          color:
                            'var(--text-muted)',
                        }}
                      >
                        {item.chunkId
                          ? `Chunk: ${item.chunkId}`
                          : `Doc ID: ${item.documentId}`}
                      </span>

                      <div
                        style={{
                          display:
                            'flex',

                          gap:
                            '0.5rem',
                        }}
                      >
                        {detailsUrl && (
                          <Link
                            to={
                              detailsUrl
                            }
                            className="btn btn-secondary btn-sm"
                          >
                            View Details
                          </Link>
                        )}

                        {readerUrl && (
                          <Link
                            to={
                              readerUrl
                            }
                            className="btn btn-outline-primary btn-sm"
                          >
                            Open in Reader
                          </Link>
                        )}

                        {!detailsUrl &&
                          item.documentType
                            ?.toLowerCase() ===
                            'book' && (
                            <span
                              style={{
                                fontSize:
                                  '0.75rem',

                                color:
                                  'var(--text-muted)',

                                alignSelf:
                                  'center',
                              }}
                            >
                              Catalogue link unavailable
                            </span>
                          )}
                      </div>
                    </div>
                  </div>
                );
              }
            )}
          </div>
        </div>
      ) : (
        /* ===================================================
           DEFAULT LANDING
        ==================================================== */

        <div
          className="card"
          style={{
            padding:
              '2.5rem',

            textAlign:
              'center',
          }}
        >
          <BookOpen
            size={40}
            strokeWidth={1.5}
            color="var(--primary-ink)"
            style={{
              margin:
                '0 auto 1rem auto',
            }}
          />

          <h3
            style={{
              fontSize:
                '1.25rem',

              color:
                'var(--primary-ink)',

              marginBottom:
                '0.5rem',
            }}
          >
            Academic Knowledge Discovery
          </h3>

          <p
            style={{
              maxWidth:
                '580px',

              margin:
                '0 auto 1.5rem auto',

              color:
                'var(--text-secondary)',

              fontSize:
                '0.92rem',
            }}
          >
            Smart Search uses dense
            mathematical vector
            embeddings to understand
            the semantic intent of your
            research questions rather
            than just literal keyword
            matching.
          </p>

          <div
            style={{
              display:
                'flex',

              gap:
                '0.75rem',

              justifyContent:
                'center',

              flexWrap:
                'wrap',
            }}
          >
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => {
                const sample =
                  'a detective solving crimes through observation and logical reasoning';

                setQuery(
                  sample
                );

                setSearchMode(
                  'semantic'
                );

                executeSearch(
                  sample,
                  'semantic',
                  'all',
                  topK
                );
              }}
            >
              "detective solving through observation"
            </button>

            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => {
                const sample =
                  'machine learning algorithms';

                setQuery(
                  sample
                );

                setSearchMode(
                  'semantic'
                );

                executeSearch(
                  sample,
                  'semantic',
                  'all',
                  topK
                );
              }}
            >
              "machine learning algorithms"
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default SmartSearchPage;