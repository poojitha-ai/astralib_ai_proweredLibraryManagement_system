import React, { useState, useEffect, useMemo } from 'react';
import { api } from '../../services/api';
import { Plus, Edit2, Trash2, Search, BookOpen, AlertCircle, CheckCircle2, Upload, FileText } from 'lucide-react';
import { Badge, LoadingSpinner, EmptyState, Modal } from '../../components/common/Components';

/* Language labels are stored as genres by the curated manifest. */
const PROGRAMMING_LANGUAGES = [
  'Python',
  'Java',
  'C',
  'C++',
  'JavaScript',
  'C#',
  'Go',
  'Rust',
];

export function ManageBooksPage() {
  const [books, setBooks] = useState([]);
  const [authors, setAuthors] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [languageFilter, setLanguageFilter] = useState('');
  const [levelFilter, setLevelFilter] = useState('');
  // bookId -> levels assigned by pathway curation
  const [levelsByBook, setLevelsByBook] = useState({});
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // EPUB import + automatic embedding
  const [showImport, setShowImport] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState('');
  const [importJob, setImportJob] = useState(null);
  const [importForm, setImportForm] = useState({
    title: '',
    resourceId: '',
    author: '',
    category: '',
    description: '',
    publicationYear: '',
    publisher: '',
    language: 'English',
    isbn: '',
    subjects: '',
  });

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

      // Difficulty labels live on pathway memberships; a failed call
      // only costs the difficulty filter, never the table itself.
      try {
        const memberships = await api.pathways.getMemberships();

        if (Array.isArray(memberships)) {
          const byBook = {};

          memberships.forEach((link) => {
            if (!link.bookId) return;
            if (!byBook[link.bookId]) byBook[link.bookId] = [];
            byBook[link.bookId].push(link.level);
          });

          setLevelsByBook(byBook);
        }
      } catch (levelErr) {
        console.warn('Could not load pathway levels:', levelErr);
      }
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

  // =========================================================
  // EPUB IMPORT
  // =========================================================

  const handleImportSubmit = async (e) => {
    e.preventDefault();
    setImportError('');
    setSuccess('');

    if (!importFile) {
      setImportError('Please choose an EPUB file to import.');
      return;
    }

    setImporting(true);
    setImportJob(null);

    try {
      const formData = new FormData();
      formData.append('file', importFile);

      Object.entries(importForm).forEach(([key, value]) => {
        if (value !== '' && value !== null) formData.append(key, value);
      });

      const result = await api.admin.importEpub(formData);

      setImportJob(result.ingestion || null);
      setSuccess(
        `Imported "${result.title}" (#${result.bookId}). ` +
          'Embedding started automatically.'
      );
      setImportFile(null);

      const input = document.getElementById('epub-file-input');
      if (input) input.value = '';

      loadAll();
    } catch (err) {
      console.error('EPUB import failed:', err);
      setImportError(err.message || 'EPUB import failed.');
    } finally {
      setImporting(false);
    }
  };

  // Poll the embedding job until it finishes.
  useEffect(() => {
    if (!importJob || importJob.finished) {
      return undefined;
    }

    const timer = setInterval(async () => {
      try {
        const status = await api.admin.getImportStatus(importJob.jobId);
        setImportJob(status);

        if (status.finished) {
          loadAll();
        }
      } catch (err) {
        console.error('Embedding status failed:', err);
        setImportError(err.message || 'Could not read embedding status.');
        clearInterval(timer);
      }
    }, 1500);

    return () => clearInterval(timer);
  }, [importJob]);

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

  const languageOptions = useMemo(
    () =>
      Array.from(
        new Set(
          books
            .flatMap((b) => b.genres || [])
            .filter((genre) => PROGRAMMING_LANGUAGES.includes(genre))
        )
      ).sort(),
    [books]
  );

  const filteredBooks = books.filter((b) => {
    if (languageFilter && !(b.genres || []).includes(languageFilter)) return false;

    if (levelFilter && !(levelsByBook[b.id] || []).includes(levelFilter)) return false;

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
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => setShowImport((open) => !open)}
          >
            <Upload size={16} /> {showImport ? 'Hide EPUB Import' : 'Import EPUB'}
          </button>
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

      {showImport && (
        <div className="card" style={{ marginBottom: '1.5rem', padding: '1.5rem' }}>
          <div className="card-header">
            <h2 className="card-title">Import EPUB &amp; Generate Embeddings</h2>
            <Badge variant="primary">Automatic indexing</Badge>
          </div>

          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            The EPUB is stored in the library data folder, the book is created or matched by
            resource id / ISBN, and full-text embedding starts immediately.
          </p>

          <form onSubmit={handleImportSubmit}>
            <div className="form-group">
              <label className="form-label" htmlFor="epub-file-input">EPUB File *</label>
              <input
                id="epub-file-input"
                type="file"
                className="form-input"
                accept=".epub,application/epub+zip"
                onChange={(e) => setImportFile(e.target.files?.[0] || null)}
              />
              {importFile && (
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.35rem' }}>
                  <FileText size={12} /> {importFile.name} ({Math.round(importFile.size / 1024)} KB)
                </div>
              )}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <div className="form-group">
                <label className="form-label" htmlFor="imp-title">Title *</label>
                <input
                  id="imp-title"
                  type="text"
                  className="form-input"
                  value={importForm.title}
                  onChange={(e) => setImportForm({ ...importForm, title: e.target.value })}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="imp-resource">Resource ID *</label>
                <input
                  id="imp-resource"
                  type="text"
                  className="form-input"
                  placeholder="e.g. GUTENBERG-1661"
                  value={importForm.resourceId}
                  onChange={(e) => setImportForm({ ...importForm, resourceId: e.target.value })}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="imp-author">Author</label>
                <input
                  id="imp-author"
                  type="text"
                  className="form-input"
                  value={importForm.author}
                  onChange={(e) => setImportForm({ ...importForm, author: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="imp-category">Category</label>
                <input
                  id="imp-category"
                  type="text"
                  className="form-input"
                  placeholder="e.g. Classic Literature"
                  value={importForm.category}
                  onChange={(e) => setImportForm({ ...importForm, category: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="imp-year">Publication Year</label>
                <input
                  id="imp-year"
                  type="number"
                  className="form-input"
                  value={importForm.publicationYear}
                  onChange={(e) => setImportForm({ ...importForm, publicationYear: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="imp-language">Language</label>
                <input
                  id="imp-language"
                  type="text"
                  className="form-input"
                  value={importForm.language}
                  onChange={(e) => setImportForm({ ...importForm, language: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="imp-isbn">ISBN</label>
                <input
                  id="imp-isbn"
                  type="text"
                  className="form-input"
                  value={importForm.isbn}
                  onChange={(e) => setImportForm({ ...importForm, isbn: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="imp-publisher">Publisher</label>
                <input
                  id="imp-publisher"
                  type="text"
                  className="form-input"
                  value={importForm.publisher}
                  onChange={(e) => setImportForm({ ...importForm, publisher: e.target.value })}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="imp-description">Description</label>
              <textarea
                id="imp-description"
                className="form-textarea"
                rows={2}
                value={importForm.description}
                onChange={(e) => setImportForm({ ...importForm, description: e.target.value })}
              />
            </div>

            {importError && (
              <div className="banner banner-error" style={{ marginBottom: '1rem' }}>
                <AlertCircle size={16} />
                <span>{importError}</span>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button type="submit" className="btn btn-primary" disabled={importing}>
                <Upload size={16} /> {importing ? 'Importing…' : 'Import & Start Embedding'}
              </button>
            </div>
          </form>

          {importJob && (
            <div
              className={`banner ${importJob.status === 'FAILED' ? 'banner-error' : 'banner-info'}`}
              style={{ marginTop: '1rem', alignItems: 'flex-start' }}
            >
              {importJob.status === 'FAILED' ? <AlertCircle size={16} /> : <CheckCircle2 size={16} />}
              <div style={{ display: 'grid', gap: '0.2rem' }}>
                <strong>
                  Embedding status: {importJob.status}
                  {importJob.title ? ` — ${importJob.title}` : ''}
                </strong>
                <span style={{ fontSize: '0.8rem' }}>{importJob.message}</span>
                {importJob.stats && (
                  <span style={{ fontSize: '0.75rem' }}>
                    chunks {importJob.stats.chunksCreated ?? 0} · stored{' '}
                    {importJob.stats.vectorsStored ?? 0} · already indexed{' '}
                    {importJob.stats.duplicatesSkipped ?? 0} · failed{' '}
                    {importJob.stats.failedRecords ?? 0} · total vectors for book{' '}
                    {importJob.stats.vectorRecordsForBook ?? 0}
                  </span>
                )}
                {!importJob.finished && (
                  <span style={{ fontSize: '0.75rem' }}>Indexing in progress — this view refreshes automatically.</span>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Filter / Search Bar */}
      <div className="card" style={{ marginBottom: '1.5rem', padding: '1rem' }}>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{ position: 'relative', maxWidth: '400px', flex: '1 1 260px' }}>
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

        <select
          className="form-select"
          style={{ maxWidth: '200px' }}
          value={languageFilter}
          onChange={(e) => setLanguageFilter(e.target.value)}
          aria-label="Programming language"
        >
          <option value="">All programming languages</option>
          {languageOptions.map((language) => (
            <option key={language} value={language}>{language}</option>
          ))}
        </select>

        <select
          className="form-select"
          style={{ maxWidth: '180px' }}
          value={levelFilter}
          onChange={(e) => setLevelFilter(e.target.value)}
          aria-label="Difficulty"
        >
          <option value="">All difficulties</option>
          <option value="BEGINNER">Beginner</option>
          <option value="INTERMEDIATE">Intermediate</option>
          <option value="ADVANCED">Advanced</option>
        </select>

        {(languageFilter || levelFilter) && (
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => {
              setLanguageFilter('');
              setLevelFilter('');
            }}
          >
            Clear filters
          </button>
        )}
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
                <th>Reading status</th>
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
                  <td>
                    {(b.digitalResources || []).length > 0 ? (
                      <Badge variant="success">Available</Badge>
                    ) : (
                      <Badge variant="warning">No digital edition</Badge>
                    )}
                  </td>
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
