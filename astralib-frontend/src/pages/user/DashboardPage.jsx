import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';

import {
  Search,
  BookOpen,
  Bookmark,
  Compass,
  BookMarked,
  Layers,
  Clock,
} from 'lucide-react';

import {
  Badge,
  LoadingSpinner,
} from '../../components/common/Components';

export function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [searchQuery, setSearchQuery] = useState('');
  const [searchMode, setSearchMode] = useState('keyword');

  const [historyItems, setHistoryItems] = useState([]);
  const [recommendations, setRecommendations] = useState([]);
  const [recentBooks, setRecentBooks] = useState([]);
  const [favorites, setFavorites] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // =========================================================
  // LOAD DASHBOARD DATA
  // =========================================================

  useEffect(() => {
    async function loadDashboardData() {
      setLoading(true);
      setError('');

      try {
        const [
          booksData,
          favData,
          historyData,
          recData,
        ] = await Promise.allSettled([
          api.books.getAll(),
          api.favorites.getAll(),
          api.readingHistory.getAll(),
          api.recommendations.getMy(6),
        ]);

        if (
          booksData.status === 'fulfilled' &&
          Array.isArray(booksData.value)
        ) {
          setRecentBooks(
            booksData.value.slice(0, 6)
          );
        }

        if (
          favData.status === 'fulfilled' &&
          Array.isArray(favData.value)
        ) {
          setFavorites(
            favData.value.slice(0, 5)
          );
        }

        if (
          historyData.status === 'fulfilled' &&
          Array.isArray(historyData.value)
        ) {
          setHistoryItems(
            historyData.value.slice(0, 4)
          );
        }

        if (
          recData.status === 'fulfilled' &&
          Array.isArray(recData.value)
        ) {
          setRecommendations(
            recData.value.slice(0, 6)
          );
        }
      } catch (err) {
        console.error(
          'Error loading dashboard data:',
          err
        );

        setError(
          'Some dashboard sections could not be loaded from the backend.'
        );
      } finally {
        setLoading(false);
      }
    }

    loadDashboardData();
  }, []);

  // =========================================================
  // SEARCH
  // =========================================================

  const handleSearch = (e) => {
    e.preventDefault();

    if (!searchQuery.trim()) {
      return;
    }

    if (searchMode === 'semantic') {
      navigate(
        `/search?mode=semantic&q=${encodeURIComponent(
          searchQuery.trim()
        )}`
      );
    } else {
      navigate(
        `/catalogue?keyword=${encodeURIComponent(
          searchQuery.trim()
        )}`
      );
    }
  };

  // =========================================================
  // MAIN DASHBOARD
  // =========================================================

  return (
    <div className="page-container">

      {/* =====================================================
          HEADER
      ====================================================== */}

      <div className="page-header">
        <div className="page-title-group">
          <h1>
            Welcome, {user?.name || 'Researcher'}
          </h1>

          <p>
            Access your institutional library,
            research resources and academic
            reading activity.
          </p>
        </div>

        <div className="page-actions">
          <Link
            to="/catalogue"
            className="btn btn-secondary btn-sm"
          >
            <BookOpen size={16} />

            Browse Full Catalogue
          </Link>
        </div>
      </div>

      {/* =====================================================
          WARNING
      ====================================================== */}

      {error && (
        <div className="banner banner-warning">
          <span>{error}</span>
        </div>
      )}

      {/* =====================================================
          MAIN SEARCH
      ====================================================== */}

      <div
        className="card"
        style={{
          marginBottom: '2rem',
          backgroundColor: '#FFFFFF',
          border:
            '1px solid var(--border-default)',
        }}
      >
        <form onSubmit={handleSearch}>
          <div
            style={{
              display: 'flex',
              gap: '0.75rem',
              flexWrap: 'wrap',
              alignItems: 'center',
            }}
          >
            <div
              style={{
                flex: 1,
                position: 'relative',
                minWidth: '280px',
              }}
            >
              <Search
                size={18}
                style={{
                  position: 'absolute',
                  left: '1rem',
                  top: '50%',
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
                  paddingLeft: '2.75rem',
                  height: '48px',
                  fontSize: '1rem',
                }}
                placeholder={
                  searchMode === 'semantic'
                    ? 'Describe research concept, paper abstract, or semantic topic...'
                    : 'Search books, papers, authors, ISBN or subject keywords...'
                }
                value={searchQuery}
                onChange={(e) =>
                  setSearchQuery(
                    e.target.value
                  )
                }
              />
            </div>

            {/* Search Mode */}

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                background:
                  'var(--bg-app)',
                borderRadius:
                  'var(--radius-sm)',
                padding: '3px',
                border:
                  '1px solid var(--border-default)',
              }}
            >
              <button
                type="button"
                className={`btn btn-sm ${
                  searchMode === 'keyword'
                    ? 'btn-primary'
                    : 'btn-secondary'
                }`}
                style={{
                  border: 'none',
                  boxShadow: 'none',
                }}
                onClick={() =>
                  setSearchMode('keyword')
                }
              >
                Keyword Search
              </button>

              <button
                type="button"
                className={`btn btn-sm ${
                  searchMode === 'semantic'
                    ? 'btn-primary'
                    : 'btn-secondary'
                }`}
                style={{
                  border: 'none',
                  boxShadow: 'none',
                }}
                onClick={() =>
                  setSearchMode('semantic')
                }
              >
                Smart Search
              </button>
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{
                height: '48px',
                padding: '0 1.5rem',
              }}
            >
              Search Library
            </button>
          </div>
        </form>
      </div>

      {/* =====================================================
          DASHBOARD CONTENT
      ====================================================== */}

      {loading ? (
        <LoadingSpinner
          message="Loading your academic dashboard..."
        />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns:
              'repeat(12, 1fr)',
            gap: '1.75rem',
          }}
        >

          {/* =================================================
              LEFT COLUMN
          ================================================== */}

          <div
            style={{
              gridColumn: 'span 8',
            }}
          >

            {/* ===============================================
                CONTINUE READING
            ================================================ */}

            <div
              className="card"
              style={{
                marginBottom: '1.75rem',
              }}
            >
              <div className="card-header">
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.5rem',
                  }}
                >
                  <Clock
                    size={18}
                    color="var(--primary-ink)"
                  />

                  <h2 className="card-title">
                    Continue Reading
                  </h2>
                </div>

                <Link
                  to="/history"
                  style={{
                    fontSize: '0.85rem',
                  }}
                >
                  View All History →
                </Link>
              </div>

              {historyItems.length === 0 ? (
                <p
                  style={{
                    fontSize: '0.9rem',
                    color:
                      'var(--text-muted)',
                  }}
                >
                  No recent reading sessions
                  recorded. Open a digital book
                  to begin reading.
                </p>
              ) : (
                <div
                  style={{
                    display: 'flex',
                    flexDirection:
                      'column',
                    gap: '0.75rem',
                  }}
                >
                  {historyItems.map(
                    (item) => {
                      const progress =
                        typeof item.progressPercent ===
                        'number'
                          ? item.progressPercent
                          : 0;

                      return (
                        <div
                          key={item.id}
                          style={{
                            padding:
                              '0.85rem',
                            border:
                              '1px solid var(--border-light)',
                            borderRadius:
                              'var(--radius-sm)',
                            background:
                              'var(--bg-app)',
                          }}
                        >
                          <div
                            style={{
                              display:
                                'flex',
                              alignItems:
                                'center',
                              justifyContent:
                                'space-between',
                              gap: '1rem',
                            }}
                          >
                            {/* Book Information */}

                            <div
                              style={{
                                flex: 1,
                              }}
                            >
                              <div
                                style={{
                                  fontWeight:
                                    600,
                                  color:
                                    'var(--primary-ink)',
                                  fontSize:
                                    '0.95rem',
                                }}
                              >
                                {item.bookTitle ||
                                  'Academic Resource'}
                              </div>

                              <div
                                style={{
                                  fontSize:
                                    '0.78rem',
                                  color:
                                    'var(--text-muted)',
                                  marginTop:
                                    '0.15rem',
                                }}
                              >
                                {item.accessTime
                                  ? `Last read ${new Date(
                                      item.accessTime
                                    ).toLocaleString()}`
                                  : 'Reading activity'}

                                {item.resourceFile
                                  ? ` • ${item.resourceFile}`
                                  : ''}
                              </div>

                              {/* Progress */}

                              <div
                                style={{
                                  marginTop:
                                    '0.6rem',
                                  display:
                                    'flex',
                                  alignItems:
                                    'center',
                                  gap:
                                    '0.75rem',
                                }}
                              >
                                <div
                                  style={{
                                    flex: 1,
                                    height:
                                      '6px',
                                    background:
                                      '#E5E7EB',
                                    borderRadius:
                                      '4px',
                                    overflow:
                                      'hidden',
                                    maxWidth:
                                      '320px',
                                  }}
                                >
                                  <div
                                    style={{
                                      width: `${Math.min(
                                        100,
                                        Math.max(
                                          0,
                                          progress
                                        )
                                      )}%`,
                                      height:
                                        '100%',
                                      background:
                                        'var(--primary-ink)',
                                      transition:
                                        'width 0.25s ease',
                                    }}
                                  />
                                </div>

                                <span
                                  style={{
                                    fontSize:
                                      '0.75rem',
                                    fontWeight:
                                      600,
                                    color:
                                      'var(--text-secondary)',
                                    minWidth:
                                      '55px',
                                  }}
                                >
                                  {progress}% read
                                </span>
                              </div>
                            </div>

                            {/* IMPORTANT:
                                Reader expects BOOK ID,
                                not DigitalResource ID.
                            */}

                            {item.bookId ? (
                              <Link
                                to={`/reader/${item.bookId}`}
                                className="btn btn-outline-primary btn-sm"
                              >
                                Resume Reader
                              </Link>
                            ) : null}
                          </div>
                        </div>
                      );
                    }
                  )}
                </div>
              )}
            </div>

            {/* ===============================================
                RECOMMENDED RESOURCES
            ================================================ */}

            <div
              className="card"
              style={{
                marginBottom: '1.75rem',
              }}
            >
              <div className="card-header">
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.5rem',
                  }}
                >
                  <Compass
                    size={18}
                    color="var(--primary-ink)"
                  />

                  <h2 className="card-title">
                    Recommended Resources
                  </h2>
                </div>

                <Link
                  to="/recommendations"
                  style={{
                    fontSize: '0.85rem',
                  }}
                >
                  Explore All →
                </Link>
              </div>

              {recommendations.length ===
              0 ? (
                <p
                  style={{
                    fontSize: '0.9rem',
                    color:
                      'var(--text-muted)',
                  }}
                >
                  Personalized recommendations
                  will appear here as you read
                  and favorite catalogue
                  resources.
                </p>
              ) : (
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'repeat(auto-fill, minmax(220px, 1fr))',
                    gap: '1rem',
                  }}
                >
                  {recommendations.map(
                    (rec) => (
                      <div
                        key={
                          rec.bookId ||
                          rec.id
                        }
                        style={{
                          padding:
                            '1rem',
                          border:
                            '1px solid var(--border-light)',
                          borderRadius:
                            'var(--radius-sm)',
                          backgroundColor:
                            '#FFFFFF',
                          display:
                            'flex',
                          flexDirection:
                            'column',
                          justifyContent:
                            'space-between',
                        }}
                      >
                        <div>
                          <Badge
                            variant="gold"
                            style={{
                              marginBottom:
                                '0.5rem',
                            }}
                          >
                            Score:{' '}
                            {Math.round(
                              (rec.score ||
                                rec.affinityScore ||
                                0.85) *
                                100
                            )}
                            %
                          </Badge>

                          <h4
                            style={{
                              fontSize:
                                '0.95rem',
                              color:
                                'var(--primary-ink)',
                              marginBottom:
                                '0.35rem',
                              lineHeight:
                                '1.3',
                            }}
                          >
                            {rec.title}
                          </h4>

                          <div
                            style={{
                              fontSize:
                                '0.8rem',
                              color:
                                'var(--text-secondary)',
                              marginBottom:
                                '0.5rem',
                            }}
                          >
                            {rec.author ||
                              'Author not listed'}
                          </div>
                        </div>

                        <Link
                          to={`/books/${
                            rec.bookId ||
                            rec.id
                          }`}
                          className="btn btn-secondary btn-sm"
                          style={{
                            alignSelf:
                              'flex-start',
                            marginTop:
                              '0.5rem',
                          }}
                        >
                          View Resource
                        </Link>
                      </div>
                    )
                  )}
                </div>
              )}
            </div>

            {/* ===============================================
                RECENT CATALOGUE
            ================================================ */}

            <div className="card">
              <div className="card-header">
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.5rem',
                  }}
                >
                  <Layers
                    size={18}
                    color="var(--primary-ink)"
                  />

                  <h2 className="card-title">
                    Recently Added to Catalogue
                  </h2>
                </div>

                <Link
                  to="/catalogue"
                  style={{
                    fontSize: '0.85rem',
                  }}
                >
                  Full Catalogue (
                  {recentBooks.length} items) →
                </Link>
              </div>

              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns:
                    'repeat(auto-fill, minmax(220px, 1fr))',
                  gap: '1rem',
                }}
              >
                {recentBooks
                  .slice(0, 3)
                  .map((book) => (
                    <div
                      key={book.id}
                      style={{
                        padding:
                          '1rem',
                        border:
                          '1px solid var(--border-light)',
                        borderRadius:
                          'var(--radius-sm)',
                        backgroundColor:
                          'var(--bg-app)',
                      }}
                    >
                      <div
                        style={{
                          fontSize:
                            '0.75rem',
                          color:
                            'var(--accent-gold)',
                          fontWeight:
                            600,
                          textTransform:
                            'uppercase',
                        }}
                      >
                        {book.category
                          ?.name ||
                          'General'}
                      </div>

                      <h4
                        style={{
                          margin:
                            '0.25rem 0',
                          color:
                            'var(--primary-ink)',
                          fontSize:
                            '0.95rem',
                        }}
                      >
                        {book.title}
                      </h4>

                      <p
                        style={{
                          fontSize:
                            '0.8rem',
                          color:
                            'var(--text-secondary)',
                          marginBottom:
                            '0.75rem',
                        }}
                      >
                        {book.author
                          ?.name ||
                          'Unknown Author'}
                      </p>

                      <Link
                        to={`/books/${book.id}`}
                        className="btn btn-secondary btn-sm"
                      >
                        Details
                      </Link>
                    </div>
                  ))}
              </div>
            </div>
          </div>

          {/* =================================================
              RIGHT COLUMN
          ================================================== */}

          <div
            style={{
              gridColumn: 'span 4',
            }}
          >

            {/* ===============================================
                SAVED RESOURCES
            ================================================ */}

            <div
              className="card"
              style={{
                marginBottom: '1.75rem',
              }}
            >
              <div className="card-header">
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.5rem',
                  }}
                >
                  <Bookmark
                    size={18}
                    color="var(--primary-ink)"
                  />

                  <h2 className="card-title">
                    Saved Resources
                  </h2>
                </div>

                <Link
                  to="/favorites"
                  style={{
                    fontSize: '0.85rem',
                  }}
                >
                  All ({favorites.length})
                </Link>
              </div>

              {favorites.length === 0 ? (
                <p
                  style={{
                    fontSize: '0.88rem',
                    color:
                      'var(--text-muted)',
                  }}
                >
                  You haven't saved any
                  catalogue resources yet.
                  Click "Save to Favorites" on
                  any resource page.
                </p>
              ) : (
                <div
                  style={{
                    display: 'flex',
                    flexDirection:
                      'column',
                    gap: '0.65rem',
                  }}
                >
                  {favorites.map(
                    (fav) => (
                      <div
                        key={fav.id}
                        style={{
                          padding:
                            '0.65rem',
                          border:
                            '1px solid var(--border-light)',
                          borderRadius:
                            'var(--radius-sm)',
                          background:
                            'var(--bg-app)',
                          display:
                            'flex',
                          justifyContent:
                            'space-between',
                          alignItems:
                            'center',
                        }}
                      >
                        <div
                          style={{
                            flex: 1,
                            paddingRight:
                              '0.5rem',
                          }}
                        >
                          <div
                            style={{
                              fontSize:
                                '0.88rem',
                              fontWeight:
                                600,
                              color:
                                'var(--primary-ink)',
                            }}
                          >
                            {fav.title ||
                              `Book #${fav.bookId}`}
                          </div>

                          <div
                            style={{
                              fontSize:
                                '0.75rem',
                              color:
                                'var(--text-muted)',
                            }}
                          >
                            Saved{' '}
                            {fav.createdAt
                              ? new Date(
                                  fav.createdAt
                                ).toLocaleDateString()
                              : ''}
                          </div>
                        </div>

                        <Link
                          to={`/books/${fav.bookId}`}
                          className="btn btn-secondary btn-sm"
                          style={{
                            padding:
                              '0.2rem 0.5rem',
                          }}
                        >
                          View
                        </Link>
                      </div>
                    )
                  )}
                </div>
              )}
            </div>

            {/* ===============================================
                RESEARCH ASSISTANT
            ================================================ */}

            <div
              className="card"
              style={{
                backgroundColor:
                  'var(--bg-subtle)',

                border:
                  '1px solid var(--border-default)',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  marginBottom:
                    '0.75rem',
                }}
              >
                <BookMarked
                  size={18}
                  color="var(--primary-ink)"
                />

                <h3
                  style={{
                    color:
                      'var(--primary-ink)',
                    fontSize:
                      '1.05rem',
                  }}
                >
                  Research Assistant
                </h3>
              </div>

              <p
                style={{
                  fontSize: '0.85rem',
                  color:
                    'var(--text-secondary)',
                  marginBottom: '1rem',
                }}
              >
                Conduct grounded Q&A over
                AstraLib documents with exact
                bibliographic source citations.
              </p>

              <Link
                to="/research"
                className="btn btn-primary btn-sm"
                style={{
                  width: '100%',
                }}
              >
                Open Research Assistant
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default DashboardPage;