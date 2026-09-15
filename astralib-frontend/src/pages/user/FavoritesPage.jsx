import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../services/api';
import { Bookmark, Trash2, BookOpen, ExternalLink } from 'lucide-react';
import { Badge, LoadingSpinner, EmptyState } from '../../components/common/Components';

export function FavoritesPage() {
  const [favorites, setFavorites] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadFavorites = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await api.favorites.getAll();
      setFavorites(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to load favorites:', err);
      setError(err.message || 'Unable to retrieve saved favorites.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFavorites();
  }, []);

  const handleRemoveFavorite = async (bookId) => {
    try {
      await api.favorites.remove(bookId);
      setFavorites((prev) => prev.filter((f) => f.bookId !== bookId));
    } catch (err) {
      console.error('Failed to remove favorite:', err);
    }
  };

  return (
    <div className="page-container">
      {/* Formal Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Saved Library Resources</h1>
          <p>Your curated bookshelf of saved research papers, monographs, and institutional holdings.</p>
        </div>
      </div>

      {error && (
        <div className="banner banner-error">
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <LoadingSpinner message="Loading saved library resources..." />
      ) : favorites.length === 0 ? (
        <EmptyState
          icon={Bookmark}
          title="No Saved Resources Found"
          description="You haven't saved any books or papers to your institutional collection yet."
          action={
            <Link to="/catalogue" className="btn btn-primary btn-sm">
              Browse Catalogue
            </Link>
          }
        />
      ) : (
        <div className="table-container">
          <table className="formal-table">
            <thead>
              <tr>
                <th>Resource Title</th>
                <th>Saved Date</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {favorites.map((fav) => (
                <tr key={fav.id}>
                  <td>
                    <Link to={`/books/${fav.bookId}`} style={{ fontWeight: 600, color: 'var(--primary-ink)' }}>
                      {fav.title || `Resource #${fav.bookId}`}
                    </Link>
                    <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Catalogue ID: {fav.bookId}</div>
                  </td>
                  <td>
                    {fav.createdAt ? new Date(fav.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : 'Recent'}
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                      <Link to={`/books/${fav.bookId}`} className="btn btn-outline-primary btn-sm">
                        View Details
                      </Link>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={() => handleRemoveFavorite(fav.bookId)}
                        title="Remove from favorites"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
