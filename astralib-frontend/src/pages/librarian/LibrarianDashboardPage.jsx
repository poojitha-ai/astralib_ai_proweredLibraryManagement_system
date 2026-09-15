import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../services/api';
import {
  Library,
  BookOpen,
  User,
  Tags,
  FileText,
  Plus,
  ArrowRight,
  Database,
  CheckCircle,
} from 'lucide-react';
import { Badge, LoadingSpinner } from '../../components/common/Components';

export function LibrarianDashboardPage() {
  const [stats, setStats] = useState({
    booksCount: 0,
    authorsCount: 0,
    categoriesCount: 0,
  });
  const [recentBooks, setRecentBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function loadLibrarianData() {
      setLoading(true);
      setError('');
      try {
        const [booksData, authorsData, categoriesData] = await Promise.allSettled([
          api.books.getAll(),
          api.authors.getAll(),
          api.categories.getAll(),
        ]);

        const books = booksData.status === 'fulfilled' && Array.isArray(booksData.value) ? booksData.value : [];
        const authors = authorsData.status === 'fulfilled' && Array.isArray(authorsData.value) ? authorsData.value : [];
        const categories = categoriesData.status === 'fulfilled' && Array.isArray(categoriesData.value) ? categoriesData.value : [];

        setStats({
          booksCount: books.length,
          authorsCount: authors.length,
          categoriesCount: categories.length,
        });

        setRecentBooks(books.slice(0, 5));
      } catch (err) {
        console.error('Librarian desk error:', err);
        setError('Could not retrieve full librarian records from backend.');
      } finally {
        setLoading(false);
      }
    }

    loadLibrarianData();
  }, []);

  return (
    <div className="page-container">
      {/* Formal Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Librarian Desk Overview</h1>
          <p>Cataloguing administration, metadata governance, and digital monograph management.</p>
        </div>
        <div className="page-actions">
          <Link to="/librarian/books" className="btn btn-primary btn-sm">
            <Plus size={16} /> Manage Catalogue Books
          </Link>
        </div>
      </div>

      {error && (
        <div className="banner banner-warning">
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <LoadingSpinner message="Querying catalogue statistics..." />
      ) : (
        <div>
          {/* Institutional Metric Tiles */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
            <div className="card" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.8rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 }}>Catalogue Volumes</span>
                <BookOpen size={20} color="var(--primary-ink)" />
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--primary-ink)', fontFamily: 'var(--font-serif)' }}>
                {stats.booksCount}
              </div>
              <Link to="/librarian/books" style={{ fontSize: '0.8rem', color: 'var(--primary-ink)', marginTop: '0.5rem', display: 'inline-block' }}>
                View & Edit Books →
              </Link>
            </div>

            <div className="card" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.8rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 }}>Registered Authors</span>
                <User size={20} color="var(--primary-ink)" />
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--primary-ink)', fontFamily: 'var(--font-serif)' }}>
                {stats.authorsCount}
              </div>
              <Link to="/librarian/authors" style={{ fontSize: '0.8rem', color: 'var(--primary-ink)', marginTop: '0.5rem', display: 'inline-block' }}>
                View & Add Authors →
              </Link>
            </div>

            <div className="card" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.8rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 }}>Academic Categories</span>
                <Tags size={20} color="var(--primary-ink)" />
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--primary-ink)', fontFamily: 'var(--font-serif)' }}>
                {stats.categoriesCount}
              </div>
              <Link to="/librarian/categories" style={{ fontSize: '0.8rem', color: 'var(--primary-ink)', marginTop: '0.5rem', display: 'inline-block' }}>
                Manage Taxonomies →
              </Link>
            </div>

            <div className="card" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.8rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 }}>Digital Repository</span>
                <FileText size={20} color="var(--primary-ink)" />
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--accent-gold)', fontFamily: 'var(--font-serif)' }}>
                Active
              </div>
              <Link to="/librarian/resources" style={{ fontSize: '0.8rem', color: 'var(--primary-ink)', marginTop: '0.5rem', display: 'inline-block' }}>
                Upload Monograph PDFs →
              </Link>
            </div>
          </div>

          {/* Recent Catalogue Changes Table */}
          <div className="card">
            <div className="card-header">
              <h2 className="card-title">Catalogue Holdings Overview</h2>
              <Link to="/librarian/books" style={{ fontSize: '0.85rem' }}>
                Manage All Records →
              </Link>
            </div>

            <div className="table-container">
              <table className="formal-table">
                <thead>
                  <tr>
                    <th>Title</th>
                    <th>Author</th>
                    <th>Category</th>
                    <th>ISBN</th>
                    <th>Year</th>
                  </tr>
                </thead>
                <tbody>
                  {recentBooks.map((b) => (
                    <tr key={b.id}>
                      <td>
                        <strong>{b.title}</strong>
                      </td>
                      <td>{b.author?.name || '—'}</td>
                      <td>
                        <Badge variant="default">{b.category?.name || 'General'}</Badge>
                      </td>
                      <td><code>{b.isbn || '—'}</code></td>
                      <td>{b.publicationYear || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
