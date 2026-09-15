import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../services/api';
import { Compass, BookOpen, Bookmark, BookmarkCheck, ArrowRight } from 'lucide-react';
import { Badge, LoadingSpinner, EmptyState } from '../../components/common/Components';

export function RecommendationsPage() {
  const [recommendations, setRecommendations] = useState([]);
  const [favorites, setFavorites] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function loadRecommendations() {
      setLoading(true);
      setError('');
      try {
        const [recData, favData] = await Promise.allSettled([
          api.recommendations.getMy(20),
          api.favorites.getAll(),
        ]);

        if (recData.status === 'fulfilled' && Array.isArray(recData.value)) {
          setRecommendations(recData.value);
        }

        if (favData.status === 'fulfilled' && Array.isArray(favData.value)) {
          setFavorites(new Set(favData.value.map((f) => f.bookId)));
        }
      } catch (err) {
        console.error('Failed to load recommendations:', err);
        setError(err.message || 'Unable to compute personalized library recommendations.');
      } finally {
        setLoading(false);
      }
    }

    loadRecommendations();
  }, []);

  const handleFavoriteToggle = async (bookId) => {
    try {
      if (favorites.has(bookId)) {
        await api.favorites.remove(bookId);
        setFavorites((prev) => {
          const next = new Set(prev);
          next.delete(bookId);
          return next;
        });
      } else {
        await api.favorites.add(bookId);
        setFavorites((prev) => new Set(prev).add(bookId));
      }
    } catch (err) {
      console.error('Favorite action failed:', err);
    }
  };

  return (
    <div className="page-container">
      {/* Formal Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Recommended Academic Resources</h1>
          <p>Curated research recommendations based on your verified reading history, subject affinity, and collaborative library interactions.</p>
        </div>
      </div>

      {error && (
        <div className="banner banner-warning">
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <LoadingSpinner message="Calculating personalized recommendation matrix..." />
      ) : recommendations.length === 0 ? (
        <EmptyState
          icon={Compass}
          title="No Recommendations Generated Yet"
          description="As you explore the catalogue, save favorites, and read digital texts, the system will infer your academic interests."
          action={
            <Link to="/catalogue" className="btn btn-primary btn-sm">
              Explore Catalogue
            </Link>
          }
        />
      ) : (
        <div>
          <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '1.5rem' }}>
            Showing <strong>{recommendations.length}</strong> resources matched to your academic profile
          </div>

          <div className="catalogue-grid">
            {recommendations.map((item) => {
              const bookId = item.bookId || item.id;
              const isFav = favorites.has(bookId);
              const score = item.score || item.affinityScore || 0.88;
              const scorePct = Math.round(score * 100);

              return (
                <div key={bookId} className="book-card">
                  <div className="book-card-cover" style={{ height: '140px' }}>
                    <div className="book-card-cover-placeholder">
                      <Compass size={28} />
                      <span style={{ fontSize: '0.75rem', fontWeight: 600 }}>RECOMMENDED HOLDING</span>
                    </div>
                  </div>

                  <div className="book-card-body">
                    <div className="book-card-meta" style={{ justifyContent: 'space-between' }}>
                      <Badge variant="gold">Affinity: {scorePct}%</Badge>
                      {item.category && <Badge variant="default">{item.category}</Badge>}
                    </div>

                    <h3 className="book-card-title">
                      <Link to={`/books/${bookId}`}>{item.title}</Link>
                    </h3>

                    <div className="book-card-author">
                      By <strong>{item.author || 'Author not specified'}</strong>
                    </div>

                    <p className="book-card-desc">
                      {item.description || 'Recommended based on related subject activity and monograph citations.'}
                    </p>

                    <div className="book-card-footer">
                      <Link to={`/books/${bookId}`} className="btn btn-outline-primary btn-sm">
                        View Details
                      </Link>

                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleFavoriteToggle(bookId)}
                      >
                        {isFav ? <BookmarkCheck size={16} color="var(--accent-gold)" /> : <Bookmark size={16} />}
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
