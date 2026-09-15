import React, { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../services/api';
import {
  Search,
  Filter,
  Grid,
  List,
  Bookmark,
  BookmarkCheck,
  BookOpen,
  ArrowUpDown,
  FileText,
} from 'lucide-react';
import { Badge, LoadingSpinner, EmptyState } from '../../components/common/Components';

export function CataloguePage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const [books, setBooks] = useState([]);
  const [categories, setCategories] = useState([]);
  const [authors, setAuthors] = useState([]);
  const [favorites, setFavorites] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filters State
  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '');
  const [selectedCategory, setSelectedCategory] = useState(searchParams.get('category') || '');
  const [selectedAuthor, setSelectedAuthor] = useState(searchParams.get('author') || '');
  const [selectedYear, setSelectedYear] = useState(searchParams.get('year') || '');
  const [viewMode, setViewMode] = useState('grid'); // 'grid' or 'list'

  // Pagination State
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 24;

  // Load Categories & Authors for filter dropdowns
  useEffect(() => {
    async function loadFilterMetadata() {
      try {
        const [cats, auths, favs] = await Promise.allSettled([
          api.categories.getAll(),
          api.authors.getAll(),
          api.favorites.getAll().catch(() => []),  // favorites may fail if not logged in
        ]);
        if (cats.status === 'fulfilled' && Array.isArray(cats.value)) setCategories(cats.value);
        if (auths.status === 'fulfilled' && Array.isArray(auths.value)) setAuthors(auths.value);
        if (favs.status === 'fulfilled' && Array.isArray(favs.value)) {
          setFavorites(new Set(favs.value.map((f) => f.bookId)));
        }
      } catch (e) {
        console.error('Filter metadata load error:', e);
      }
    }
    loadFilterMetadata();
  }, []);

  // Fetch Books with applied filters
  useEffect(() => {
    async function fetchBooks() {
      setLoading(true);
      setError('');
      try {
        const filters = {};
        if (keyword.trim()) filters.keyword = keyword.trim();
        if (selectedCategory) filters.category = selectedCategory;
        if (selectedAuthor) filters.author = selectedAuthor;
        if (selectedYear) filters.year = parseInt(selectedYear, 10);

        const data = await api.books.getAll(filters);
        setBooks(Array.isArray(data) ? data : []);
        setCurrentPage(1);
      } catch (err) {
        console.error('Failed to load books:', err);
        setError(err.message || 'Unable to retrieve catalogue records.');
      } finally {
        setLoading(false);
      }
    }

    fetchBooks();
  }, [keyword, selectedCategory, selectedAuthor, selectedYear]);

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

  const handleClearFilters = () => {
    setKeyword('');
    setSelectedCategory('');
    setSelectedAuthor('');
    setSelectedYear('');
    setSearchParams({});
  };

  // Pagination calculation
  const totalPages = Math.ceil(books.length / itemsPerPage) || 1;
  const paginatedBooks = books.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  return (
    <div className="page-container">
      {/* Formal Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Library Catalogue</h1>
          <p>Explore institutional books, research monographs, scholarly papers and academic holdings.</p>
        </div>
        <div className="page-actions">
          <div style={{ display: 'flex', border: '1px solid var(--border-default)', borderRadius: 'var(--radius-sm)' }}>
            <button
              type="button"
              className={`btn btn-sm ${viewMode === 'grid' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ border: 'none' }}
              onClick={() => setViewMode('grid')}
              title="Grid View"
            >
              <Grid size={16} />
            </button>
            <button
              type="button"
              className={`btn btn-sm ${viewMode === 'list' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ border: 'none' }}
              onClick={() => setViewMode('list')}
              title="List View"
            >
              <List size={16} />
            </button>
          </div>
        </div>
      </div>

      {/* Filter Control Bar */}
      <div className="card" style={{ marginBottom: '1.75rem', padding: '1.25rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', alignItems: 'flex-end' }}>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="cat-search">Keyword Search</label>
            <div style={{ position: 'relative' }}>
              <Search size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
              <input
                id="cat-search"
                type="text"
                className="form-input"
                style={{ paddingLeft: '2.2rem' }}
                placeholder="Title, ISBN, topic..."
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
              />
            </div>
          </div>

          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="filter-cat">Category</label>
            <select
              id="filter-cat"
              className="form-select"
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
            >
              <option value="">All Academic Categories</option>
              {categories.map((c) => (
                <option key={c.id} value={c.name}>{c.name}</option>
              ))}
            </select>
          </div>

          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="filter-auth">Author</label>
            <select
              id="filter-auth"
              className="form-select"
              value={selectedAuthor}
              onChange={(e) => setSelectedAuthor(e.target.value)}
            >
              <option value="">All Authors</option>
              {authors.map((a) => (
                <option key={a.id} value={a.name}>{a.name}</option>
              ))}
            </select>
          </div>

          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="filter-year">Publication Year</label>
            <input
              id="filter-year"
              type="number"
              className="form-input"
              placeholder="e.g. 2024"
              value={selectedYear}
              onChange={(e) => setSelectedYear(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              type="button"
              className="btn btn-secondary"
              style={{ width: '100%', height: '38px' }}
              onClick={handleClearFilters}
            >
              Reset Filters
            </button>
          </div>
        </div>
      </div>

      {/* Results Meta Info */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
        <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
          Showing <strong>{books.length}</strong> catalogue records found
        </div>
      </div>

      {error && (
        <div className="banner banner-error">
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <LoadingSpinner message="Consulting library catalogue database..." />
      ) : books.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title="No Catalogue Holdings Found"
          description="Try broadening your search keywords or clearing applied filters."
          action={
            <button type="button" className="btn btn-secondary btn-sm" onClick={handleClearFilters}>
              Reset Filters
            </button>
          }
        />
      ) : viewMode === 'grid' ? (
        /* GRID VIEW */
        <div className="catalogue-grid">
          {paginatedBooks.map((book) => {
            const isFav = favorites.has(book.id);
            return (
              <div key={book.id} className="book-card">
                <div className="book-card-cover">
                  {book.coverImage ? (
                    <img src={book.coverImage} alt={book.title} onError={(e) => { e.target.style.display = 'none'; }} />
                  ) : (
                    <div className="book-card-cover-placeholder">
                      <BookOpen size={32} />
                      <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--secondary-slate)' }}>ASTRABOOK RECORD</span>
                    </div>
                  )}
                </div>

                <div className="book-card-body">
                  <div className="book-card-meta">
                    <Badge variant="default">{book.category?.name || 'General'}</Badge>
                    {book.publicationYear && (
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{book.publicationYear}</span>
                    )}
                  </div>

                  <h3 className="book-card-title">
                    <Link to={`/books/${book.id}`}>{book.title}</Link>
                  </h3>

                  <div className="book-card-author">
                    by <strong>{book.author?.name || 'Unknown Author'}</strong>
                  </div>

                  <p className="book-card-desc">
                    {book.description || 'No formal abstract or description available for this record.'}
                  </p>

                  <div className="book-card-footer">
                    <Link to={`/books/${book.id}`} className="btn btn-outline-primary btn-sm">
                      View Details
                    </Link>

                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      onClick={() => handleFavoriteToggle(book.id)}
                      title={isFav ? 'Remove Favorite' : 'Save to Favorites'}
                    >
                      {isFav ? <BookmarkCheck size={16} color="var(--accent-gold)" /> : <Bookmark size={16} />}
                      <span>{isFav ? 'Saved' : 'Save'}</span>
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        /* FORMAL TABLE LIST VIEW */
        <div className="table-container">
          <table className="formal-table">
            <thead>
              <tr>
                <th>Title & Overview</th>
                <th>Author</th>
                <th>Category</th>
                <th>Year</th>
                <th>ISBN</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {paginatedBooks.map((book) => {
                const isFav = favorites.has(book.id);
                return (
                  <tr key={book.id}>
                    <td style={{ maxWidth: '320px' }}>
                      <Link to={`/books/${book.id}`} style={{ fontWeight: 600, color: 'var(--primary-ink)' }}>
                        {book.title}
                      </Link>
                      {book.publisher && (
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Pub: {book.publisher}</div>
                      )}
                    </td>
                    <td>{book.author?.name || '—'}</td>
                    <td>
                      <Badge variant="default">{book.category?.name || 'General'}</Badge>
                    </td>
                    <td>{book.publicationYear || '—'}</td>
                    <td><code>{book.isbn || '—'}</code></td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                        <Link to={`/books/${book.id}`} className="btn btn-outline-primary btn-sm">
                          Details
                        </Link>
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          onClick={() => handleFavoriteToggle(book.id)}
                        >
                          {isFav ? <BookmarkCheck size={14} color="var(--accent-gold)" /> : <Bookmark size={14} />}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.5rem', marginTop: '2rem' }}>
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            disabled={currentPage === 1}
            onClick={() => setCurrentPage((p) => Math.max(p - 1, 1))}
          >
            ← Previous
          </button>
          <span style={{ fontSize: '0.88rem', color: 'var(--text-secondary)', padding: '0 0.5rem' }}>
            Page {currentPage} of {totalPages}
          </span>
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            disabled={currentPage === totalPages}
            onClick={() => setCurrentPage((p) => Math.min(p + 1, totalPages))}
          >
            Next →
          </button>
        </div>
      )}
    </div>
  );
}
