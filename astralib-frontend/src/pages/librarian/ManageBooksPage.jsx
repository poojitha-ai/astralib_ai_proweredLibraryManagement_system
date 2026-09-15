import React, { useState, useEffect } from 'react';
import { api } from '../../services/api';
import { Plus, Edit2, Trash2, Search, BookOpen, AlertCircle, CheckCircle2 } from 'lucide-react';
import { Badge, LoadingSpinner, EmptyState, Modal } from '../../components/common/Components';

export function ManageBooksPage() {
  const [books, setBooks] = useState([]);
  const [authors, setAuthors] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingBook, setEditingBook] = useState(null);
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    isbn: '',
    publicationYear: '',
    publisher: '',
    language: 'English',
    authorId: '',
    categoryId: '',
    coverImage: '',
  });

  const loadAll = async () => {
    setLoading(true);
    setError('');
    try {
      const [booksData, authorsData, categoriesData] = await Promise.all([
        api.books.getAll(),
        api.authors.getAll(),
        api.categories.getAll(),
      ]);
      setBooks(Array.isArray(booksData) ? booksData : []);
      setAuthors(Array.isArray(authorsData) ? authorsData : []);
      setCategories(Array.isArray(categoriesData) ? categoriesData : []);
    } catch (err) {
      console.error('Failed to load catalogue records:', err);
      setError(err.message || 'Could not load books table.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll();
  }, []);

  const handleOpenAddModal = () => {
    setEditingBook(null);
    setFormData({
      title: '',
      description: '',
      isbn: '',
      publicationYear: new Date().getFullYear().toString(),
      publisher: '',
      language: 'English',
      authorId: authors[0]?.id || '',
      categoryId: categories[0]?.id || '',
      coverImage: '',
    });
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (book) => {
    setEditingBook(book);
    setFormData({
      title: book.title || '',
      description: book.description || '',
      isbn: book.isbn || '',
      publicationYear: book.publicationYear?.toString() || '',
      publisher: book.publisher || '',
      language: book.language || 'English',
      authorId: book.author?.id || authors[0]?.id || '',
      categoryId: book.category?.id || categories[0]?.id || '',
      coverImage: book.coverImage || '',
    });
    setIsModalOpen(true);
  };

  const handleSaveBook = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    try {
      const payload = {
        title: formData.title.trim(),
        description: formData.description.trim(),
        isbn: formData.isbn.trim(),
        publicationYear: formData.publicationYear ? parseInt(formData.publicationYear, 10) : null,
        publisher: formData.publisher.trim(),
        language: formData.language.trim(),
        authorId: Number(formData.authorId),
        categoryId: Number(formData.categoryId),
        coverImage: formData.coverImage.trim(),
      };

      if (editingBook) {
        await api.books.update(editingBook.id, payload);
        setSuccess(`Successfully updated "${formData.title}"`);
      } else {
        await api.books.create(payload);
        setSuccess(`Successfully registered "${formData.title}" into catalogue`);
      }

      setIsModalOpen(false);
      loadAll();
    } catch (err) {
      console.error('Book save error:', err);
      setError(err.message || 'Failed to save book record.');
    }
  };

  const handleDeleteBook = async (bookId, title) => {
    if (!window.confirm(`Are you sure you want to delete "${title}" from the institutional catalogue?`)) {
      return;
    }

    try {
      await api.books.delete(bookId);
      setSuccess(`Deleted "${title}"`);
      setBooks((prev) => prev.filter((b) => b.id !== bookId));
    } catch (err) {
      console.error('Delete book error:', err);
      setError(err.message || 'Failed to delete book record.');
    }
  };

  const filteredBooks = books.filter((b) => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (
      (b.title && b.title.toLowerCase().includes(q)) ||
      (b.isbn && b.isbn.toLowerCase().includes(q)) ||
      (b.author?.name && b.author.name.toLowerCase().includes(q))
    );
  });

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Catalogue Books Administration</h1>
          <p>Register, modify, update metadata and govern institutional library volumes.</p>
        </div>
        <div className="page-actions">
          <button type="button" className="btn btn-primary btn-sm" onClick={handleOpenAddModal}>
            <Plus size={16} /> Add New Book Record
          </button>
        </div>
      </div>

      {error && (
        <div className="banner banner-error">
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="banner banner-info" style={{ backgroundColor: 'var(--status-success-bg)', color: 'var(--status-success-text)', borderColor: 'var(--status-success-border)' }}>
          <CheckCircle2 size={16} />
          <span>{success}</span>
        </div>
      )}

      {/* Filter / Search Bar */}
      <div className="card" style={{ marginBottom: '1.5rem', padding: '1rem' }}>
        <div style={{ position: 'relative', maxWidth: '400px' }}>
          <Search size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
          <input
            type="text"
            className="form-input"
            style={{ paddingLeft: '2.2rem' }}
            placeholder="Filter books by title, ISBN, or author..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {loading ? (
        <LoadingSpinner message="Retrieving library holdings..." />
      ) : filteredBooks.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title="No Books Found"
          description={search ? 'No catalogue entries match your search query.' : 'No book records currently registered.'}
          action={
            <button type="button" className="btn btn-primary btn-sm" onClick={handleOpenAddModal}>
              Add First Book
            </button>
          }
        />
      ) : (
        <div className="table-container">
          <table className="formal-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Title</th>
                <th>Author</th>
                <th>Category</th>
                <th>ISBN</th>
                <th>Year</th>
                <th>Language</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredBooks.map((b) => (
                <tr key={b.id}>
                  <td><code>#{b.id}</code></td>
                  <td>
                    <strong>{b.title}</strong>
                    {b.publisher && (
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Pub: {b.publisher}</div>
                    )}
                  </td>
                  <td>{b.author?.name || '—'}</td>
                  <td>
                    <Badge variant="default">{b.category?.name || 'General'}</Badge>
                  </td>
                  <td><code>{b.isbn || '—'}</code></td>
                  <td>{b.publicationYear || '—'}</td>
                  <td>{b.language || 'English'}</td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <div style={{ display: 'inline-flex', gap: '0.4rem' }}>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleOpenEditModal(b)}
                        title="Edit Record"
                      >
                        <Edit2 size={14} />
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={() => handleDeleteBook(b.id, b.title)}
                        title="Delete Record"
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

      {/* Add / Edit Book Modal Dialog */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={editingBook ? `Edit Book Record #${editingBook.id}` : 'Register New Catalogue Book'}
      >
        <form onSubmit={handleSaveBook}>
          <div className="form-group">
            <label className="form-label" htmlFor="b-title">Book Title *</label>
            <input
              id="b-title"
              type="text"
              className="form-input"
              value={formData.title}
              onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="b-author">Author *</label>
              <select
                id="b-author"
                className="form-select"
                value={formData.authorId}
                onChange={(e) => setFormData({ ...formData, authorId: e.target.value })}
                required
              >
                {authors.map((a) => (
                  <option key={a.id} value={a.id}>{a.name}</option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="b-category">Category *</label>
              <select
                id="b-category"
                className="form-select"
                value={formData.categoryId}
                onChange={(e) => setFormData({ ...formData, categoryId: e.target.value })}
                required
              >
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="b-isbn">ISBN</label>
              <input
                id="b-isbn"
                type="text"
                className="form-input"
                placeholder="e.g. 978-0123456789"
                value={formData.isbn}
                onChange={(e) => setFormData({ ...formData, isbn: e.target.value })}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="b-year">Publication Year</label>
              <input
                id="b-year"
                type="number"
                className="form-input"
                placeholder="e.g. 2024"
                value={formData.publicationYear}
                onChange={(e) => setFormData({ ...formData, publicationYear: e.target.value })}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="b-lang">Language</label>
              <input
                id="b-lang"
                type="text"
                className="form-input"
                value={formData.language}
                onChange={(e) => setFormData({ ...formData, language: e.target.value })}
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="b-pub">Publisher</label>
            <input
              id="b-pub"
              type="text"
              className="form-input"
              placeholder="e.g. MIT Press, Academic Publishing"
              value={formData.publisher}
              onChange={(e) => setFormData({ ...formData, publisher: e.target.value })}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="b-desc">Abstract / Description</label>
            <textarea
              id="b-desc"
              className="form-textarea"
              rows={3}
              placeholder="Summary of monograph topics and chapters..."
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              {editingBook ? 'Save Changes' : 'Register Book'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
