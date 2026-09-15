import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { api } from '../../services/api';
import {
  BookOpen,
  Bookmark,
  BookmarkCheck,
  FileText,
  Bot,
  ArrowLeft,
  Calendar,
  Building,
  Globe,
  Tag,
  Hash,
} from 'lucide-react';
import { Badge, LoadingSpinner, EmptyState } from '../../components/common/Components';

export function ResourceDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [book, setBook] = useState(null);
  const [relatedBooks, setRelatedBooks] = useState([]);
  const [isFavorite, setIsFavorite] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function loadBookData() {
      setLoading(true);
      setError('');
      try {
        const bookData = await api.books.getById(id);
        setBook(bookData);

        // Check if favorite
        try {
          const favs = await api.favorites.getAll();
          if (Array.isArray(favs)) {
            setIsFavorite(favs.some((f) => f.bookId === Number(id)));
          }
        } catch (favErr) {
          console.warn('Could not check favorites:', favErr);
        }

        // Fetch related books by category
        if (bookData?.category?.name) {
          try {
            const relData = await api.books.getAll({ category: bookData.category.name });
            if (Array.isArray(relData)) {
              setRelatedBooks(relData.filter((b) => b.id !== Number(id)).slice(0, 3));
            }
          } catch (e) {
            console.warn('Could not load related resources:', e);
          }
        }
      } catch (err) {
        console.error('Resource load error:', err);
        setError(err.message || 'Resource record not found in AstraLib catalogue.');
      } finally {
        setLoading(false);
      }
    }

    loadBookData();
  }, [id]);

  const handleToggleFavorite = async () => {
    try {
      if (isFavorite) {
        await api.favorites.remove(id);
        setIsFavorite(false);
      } else {
        await api.favorites.add(id);
        setIsFavorite(true);
      }
    } catch (err) {
      console.error('Failed to toggle favorite:', err);
    }
  };

  if (loading) {
    return <LoadingSpinner message="Retrieving bibliographic record..." />;
  }

  if (error || !book) {
    return (
      <div className="page-container">
        <div className="page-header">
          <Link to="/catalogue" className="btn btn-secondary btn-sm">
            <ArrowLeft size={16} /> Back to Catalogue
          </Link>
        </div>
        <EmptyState
          icon={BookOpen}
          title="Resource Not Found"
          description={error || 'The requested library record does not exist or has been deaccessioned.'}
          action={
            <Link to="/catalogue" className="btn btn-primary btn-sm">
              Return to Catalogue
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="page-container">
      {/* Navigation Breadcrumb */}
      <div style={{ marginBottom: '1.5rem' }}>
        <Link to="/catalogue" className="btn btn-secondary btn-sm" style={{ gap: '0.4rem' }}>
          <ArrowLeft size={16} />
          <span>Catalogue Directory</span>
        </Link>
      </div>

      <div className="card" style={{ padding: '2rem', marginBottom: '2rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(200px, 240px) 1fr', gap: '2.5rem' }}>
          {/* Left: Book Cover & Quick Actions */}
          <div>
            <div
              style={{
                height: '300px',
                backgroundColor: 'var(--bg-subtle)',
                border: '1px solid var(--border-default)',
                borderRadius: 'var(--radius-sm)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                overflow: 'hidden',
                marginBottom: '1.25rem',
              }}
            >
              {book.coverImage ? (
                <img src={book.coverImage} alt={book.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                <div style={{ textAlign: 'center', color: 'var(--secondary-slate)', padding: '1rem' }}>
                  <BookOpen size={48} strokeWidth={1.5} />
                  <div style={{ fontSize: '0.78rem', marginTop: '0.5rem', fontWeight: 600 }}>ASTRABOOK RECORD</div>
                </div>
              )}
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
              <button
                type="button"
                className={`btn ${isFavorite ? 'btn-secondary' : 'btn-outline-primary'}`}
                style={{ width: '100%' }}
                onClick={handleToggleFavorite}
              >
                {isFavorite ? <BookmarkCheck size={16} color="var(--accent-gold)" /> : <Bookmark size={16} />}
                <span>{isFavorite ? 'Saved in Favorites' : 'Save to Favorites'}</span>
              </button>

              <Link
                to={`/reader/${book.id}`}
                className="btn btn-primary"
                style={{ width: '100%' }}
              >
                <BookOpen size={16} />
                <span>Open in Document Reader</span>
              </Link>

              <Link
                to={`/research?q=${encodeURIComponent(`What does ${book.title} discuss?`)}`}
                className="btn btn-secondary btn-sm"
                style={{ width: '100%' }}
              >
                <Bot size={16} />
                <span>Ask Research Assistant</span>
              </Link>
            </div>
          </div>

          {/* Right: Bibliographic Record Metadata */}
          <div>
            <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
              <Badge variant="primary">{book.category?.name || 'Monograph'}</Badge>
              {book.language && <Badge variant="default">{book.language.toUpperCase()}</Badge>}
              {book.publicationYear && <Badge variant="gold">Pub: {book.publicationYear}</Badge>}
            </div>

            <h1 style={{ fontFamily: 'var(--font-serif)', fontSize: '2rem', color: 'var(--primary-ink)', marginBottom: '0.5rem', lineHeight: '1.2' }}>
              {book.title}
            </h1>

            <div style={{ fontSize: '1.1rem', color: 'var(--text-secondary)', marginBottom: '1.5rem' }}>
              Authored by <strong style={{ color: 'var(--text-primary)' }}>{book.author?.name || 'Unknown Author'}</strong>
            </div>

            {/* Metadata Summary Table */}
            <div className="table-container" style={{ marginBottom: '1.75rem' }}>
              <table className="formal-table">
                <tbody>
                  <tr>
                    <td style={{ width: '180px', color: 'var(--text-secondary)', fontWeight: 600 }}>Publisher</td>
                    <td>{book.publisher || 'Institutional Press / Not specified'}</td>
                  </tr>
                  <tr>
                    <td style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>Publication Year</td>
                    <td>{book.publicationYear || '—'}</td>
                  </tr>
                  <tr>
                    <td style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>ISBN Identifier</td>
                    <td><code>{book.isbn || 'N/A'}</code></td>
                  </tr>
                  <tr>
                    <td style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>Language</td>
                    <td>{book.language || 'English'}</td>
                  </tr>
                  <tr>
                    <td style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>Category</td>
                    <td>{book.category?.name || 'General Academic'}</td>
                  </tr>
                  {book.author?.bio && (
                    <tr>
                      <td style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>Author Biography</td>
                      <td style={{ fontSize: '0.85rem' }}>{book.author.bio}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Abstract / Description */}
            <div>
              <h3 style={{ color: 'var(--primary-ink)', marginBottom: '0.5rem', fontSize: '1.15rem' }}>Abstract & Overview</h3>
              <div style={{ fontSize: '0.95rem', color: 'var(--text-primary)', lineHeight: '1.7', whiteSpace: 'pre-line' }}>
                {book.description || 'No formal abstract deposited for this catalogue entry.'}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Related Resources */}
      {relatedBooks.length > 0 && (
        <div className="card">
          <h3 style={{ color: 'var(--primary-ink)', marginBottom: '1rem', fontSize: '1.15rem' }}>
            Related Resources in {book.category?.name}
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem' }}>
            {relatedBooks.map((rel) => (
              <div
                key={rel.id}
                style={{
                  padding: '1rem',
                  border: '1px solid var(--border-light)',
                  borderRadius: 'var(--radius-sm)',
                  background: 'var(--bg-app)',
                }}
              >
                <h4 style={{ fontSize: '0.95rem', color: 'var(--primary-ink)', marginBottom: '0.25rem' }}>
                  <Link to={`/books/${rel.id}`}>{rel.title}</Link>
                </h4>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.75rem' }}>
                  {rel.author?.name || 'Unknown Author'}
                </div>
                <Link to={`/books/${rel.id}`} className="btn btn-secondary btn-sm">
                  View Record
                </Link>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
