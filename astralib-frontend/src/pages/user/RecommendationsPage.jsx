
import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../services/api';
import {
  Compass,
  Bookmark,
  BookmarkCheck,
  BookOpen,
  RefreshCw
} from 'lucide-react';
import {
  Badge,
  LoadingSpinner,
  EmptyState
} from '../../components/common/Components';

export function RecommendationsPage() {
  const [recommendations, setRecommendations] = useState([]);
  const [favorites, setFavorites] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadRecommendations();
  }, []);

  async function loadRecommendations() {
    setLoading(true);
    setError('');

    try {
      const [recData, favData] = await Promise.all([
        api.recommendations.getMy(6),
        api.favorites.getAll()
      ]);

      if (Array.isArray(recData)) {
        // Prevent duplicate books and enforce the six-book limit.
        const uniqueRecommendations = Array.from(
          new Map(
            recData.map((item) => [
              item.bookId || item.id,
              item
            ])
          ).values()
        ).slice(0, 6);

        setRecommendations(uniqueRecommendations);
      } else {
        setRecommendations([]);
      }

      if (Array.isArray(favData)) {
        setFavorites(
          new Set(favData.map((item) => item.bookId))
        );
      }

    } catch (err) {
      console.error('Failed to load recommendations:', err);

      setError(
        err.message ||
        'Unable to load personalized recommendations.'
      );
    } finally {
      setLoading(false);
    }
  }

  const handleFavoriteToggle = async (bookId) => {
    try {
      if (favorites.has(bookId)) {
        await api.favorites.remove(bookId);

        setFavorites((previous) => {
          const updated = new Set(previous);
          updated.delete(bookId);
          return updated;
        });

      } else {
        await api.favorites.add(bookId);

        setFavorites((previous) => {
          const updated = new Set(previous);
          updated.add(bookId);
          return updated;
        });
      }

    } catch (err) {
      console.error('Favorite action failed:', err);
    }
  };

  return (
    <div className="page-container">

      {/* Page Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Recommended Academic Resources</h1>

          <p>
            Discover books selected according to the subjects,
            categories, and authors you've explored.
          </p>
        </div>

        <button
          type="button"
          className="btn btn-outline-primary btn-sm"
          onClick={loadRecommendations}
          disabled={loading}
        >
          <RefreshCw size={15} />
          Refresh
        </button>
      </div>

      {/* Error Message */}
      {error && (
        <div className="banner banner-warning">
          <span>{error}</span>
        </div>
      )}

      {/* Loading */}
      {loading ? (
        <LoadingSpinner
          message="Finding books related to your interests..."
        />

      ) : recommendations.length === 0 ? (

        /* Empty State */
        <EmptyState
          icon={Compass}
          title="No Strong Recommendations Yet"
          description="Explore books, read resources, and save your favorites. AstraLib will use those interests to discover relevant books for you."
          action={
            <Link
              to="/catalogue"
              className="btn btn-primary btn-sm"
            >
              Explore Catalogue
            </Link>
          }
        />

      ) : (

        /* Recommendations */
        <div>

          <div
            style={{
              fontSize: '0.9rem',
              color: 'var(--text-secondary)',
              marginBottom: '1.5rem'
            }}
          >
            Showing <strong>{recommendations.length}</strong>{' '}
            carefully matched resources.
          </div>

          <div className="catalogue-grid">

            {recommendations.map((item) => {
              const bookId = item.bookId || item.id;
              const isFav = favorites.has(bookId);

              return (
                <div
                  key={bookId}
                  className="book-card"
                >

                  {/* Cover */}
                  <div
                    className="book-card-cover"
                    style={{ height: '140px' }}
                  >
                    <div className="book-card-cover-placeholder">
                      <BookOpen size={30} />

                      <span
                        style={{
                          fontSize: '0.75rem',
                          fontWeight: 600
                        }}
                      >
                        RECOMMENDED BOOK
                      </span>
                    </div>
                  </div>

                  {/* Book Information */}
                  <div className="book-card-body">

                    {/* Category */}
                    <div className="book-card-meta">
                      {item.category && (
                        <Badge variant="default">
                          {item.category}
                        </Badge>
                      )}
                    </div>

                    {/* Title */}
                    <h3 className="book-card-title">
                      <Link to={`/books/${bookId}`}>
                        {item.title}
                      </Link>
                    </h3>

                    {/* Author */}
                    <div className="book-card-author">
                      By{' '}
                      <strong>
                        {item.author || 'Author not specified'}
                      </strong>
                    </div>

                    {/* Actual Recommendation Reason */}
                    {item.reason && (
                      <div
                        style={{
                          marginTop: '1rem',
                          padding: '0.85rem',
                          background: 'var(--bg-secondary)',
                          borderRadius: '8px',
                          fontSize: '0.85rem',
                          lineHeight: '1.6'
                        }}
                      >
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            marginBottom: '6px',
                            fontWeight: 600
                          }}
                        >
                          <Compass size={15} />
                          Why this book?
                        </div>

                        <p
                          style={{
                            margin: 0,
                            color: 'var(--text-secondary)'
                          }}
                        >
                          {item.reason}
                        </p>
                      </div>
                    )}

                    {/* Actions */}
                    <div className="book-card-footer">

                      <Link
                        to={`/books/${bookId}`}
                        className="btn btn-outline-primary btn-sm"
                      >
                        View Details
                      </Link>

                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleFavoriteToggle(bookId)}
                        aria-label={
                          isFav
                            ? 'Remove from favorites'
                            : 'Add to favorites'
                        }
                      >
                        {isFav ? (
                          <BookmarkCheck
                            size={16}
                            color="var(--accent-gold)"
                          />
                        ) : (
                          <Bookmark size={16} />
                        )}
                      </button>

                    </div>
                  </div>
                </div>
              );
            })}

          </div>
        </div>
      )}

    </div>
  );
}
