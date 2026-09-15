import React, { useState, useEffect } from 'react';
import { api } from '../../services/api';
import { Plus, Edit2, Trash2, User, AlertCircle, CheckCircle2 } from 'lucide-react';
import { LoadingSpinner, EmptyState, Modal } from '../../components/common/Components';

export function ManageAuthorsPage() {
  const [authors, setAuthors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingAuthor, setEditingAuthor] = useState(null);
  const [formData, setFormData] = useState({ name: '', bio: '' });

  const loadAuthors = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await api.authors.getAll();
      setAuthors(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Authors load error:', err);
      setError(err.message || 'Unable to retrieve authors directory.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAuthors();
  }, []);

  const handleOpenAdd = () => {
    setEditingAuthor(null);
    setFormData({ name: '', bio: '' });
    setIsModalOpen(true);
  };

  const handleOpenEdit = (author) => {
    setEditingAuthor(author);
    setFormData({ name: author.name || '', bio: author.bio || '' });
    setIsModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    try {
      if (editingAuthor) {
        await api.authors.update(editingAuthor.id, formData);
        setSuccess(`Updated author "${formData.name}"`);
      } else {
        await api.authors.create(formData);
        setSuccess(`Added author "${formData.name}"`);
      }
      setIsModalOpen(false);
      loadAuthors();
    } catch (err) {
      console.error('Author save error:', err);
      setError(err.message || 'Failed to save author.');
    }
  };

  const handleDelete = async (authorId, name) => {
    if (!window.confirm(`Delete author "${name}"?`)) return;
    try {
      await api.authors.delete(authorId);
      setSuccess(`Deleted author "${name}"`);
      setAuthors((prev) => prev.filter((a) => a.id !== authorId));
    } catch (err) {
      console.error('Delete author error:', err);
      setError(err.message || 'Failed to delete author.');
    }
  };

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Authors Directory Administration</h1>
          <p>Maintain author records, academic biographies, and institutional contributor profiles.</p>
        </div>
        <div className="page-actions">
          <button type="button" className="btn btn-primary btn-sm" onClick={handleOpenAdd}>
            <Plus size={16} /> Add New Author
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

      {loading ? (
        <LoadingSpinner message="Loading authors list..." />
      ) : authors.length === 0 ? (
        <EmptyState
          icon={User}
          title="No Authors Registered"
          description="Register the first scholar or monograph author."
          action={
            <button type="button" className="btn btn-primary btn-sm" onClick={handleOpenAdd}>
              Add Author
            </button>
          }
        />
      ) : (
        <div className="table-container">
          <table className="formal-table">
            <thead>
              <tr>
                <th style={{ width: '80px' }}>ID</th>
                <th>Author Name</th>
                <th>Academic Biography</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {authors.map((a) => (
                <tr key={a.id}>
                  <td><code>#{a.id}</code></td>
                  <td><strong>{a.name}</strong></td>
                  <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{a.bio || '—'}</td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <div style={{ display: 'inline-flex', gap: '0.4rem' }}>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleOpenEdit(a)}
                        title="Edit Author"
                      >
                        <Edit2 size={14} />
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={() => handleDelete(a.id, a.name)}
                        title="Delete Author"
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

      {/* Modal Dialog */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={editingAuthor ? `Edit Author #${editingAuthor.id}` : 'Register New Author'}
      >
        <form onSubmit={handleSave}>
          <div className="form-group">
            <label className="form-label" htmlFor="a-name">Author Name *</label>
            <input
              id="a-name"
              type="text"
              className="form-input"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="a-bio">Academic Biography / Affiliation</label>
            <textarea
              id="a-bio"
              className="form-textarea"
              rows={3}
              placeholder="e.g. Professor of Computer Science, Stanford University..."
              value={formData.bio}
              onChange={(e) => setFormData({ ...formData, bio: e.target.value })}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              {editingAuthor ? 'Save Changes' : 'Register Author'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
