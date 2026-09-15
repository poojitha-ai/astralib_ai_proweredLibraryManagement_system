import React, { useState, useEffect } from 'react';
import { api } from '../../services/api';
import { Plus, Edit2, Trash2, Tags, AlertCircle, CheckCircle2 } from 'lucide-react';
import { LoadingSpinner, EmptyState, Modal } from '../../components/common/Components';

export function ManageCategoriesPage() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCat, setEditingCat] = useState(null);
  const [formData, setFormData] = useState({ name: '', description: '' });

  const loadCategories = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await api.categories.getAll();
      setCategories(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Categories load error:', err);
      setError(err.message || 'Unable to retrieve category taxonomy.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCategories();
  }, []);

  const handleOpenAdd = () => {
    setEditingCat(null);
    setFormData({ name: '', description: '' });
    setIsModalOpen(true);
  };

  const handleOpenEdit = (cat) => {
    setEditingCat(cat);
    setFormData({ name: cat.name || '', description: cat.description || '' });
    setIsModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    try {
      if (editingCat) {
        await api.categories.update(editingCat.id, formData);
        setSuccess(`Updated category "${formData.name}"`);
      } else {
        await api.categories.create(formData);
        setSuccess(`Created category "${formData.name}"`);
      }
      setIsModalOpen(false);
      loadCategories();
    } catch (err) {
      console.error('Category save error:', err);
      setError(err.message || 'Failed to save category.');
    }
  };

  const handleDelete = async (catId, name) => {
    if (!window.confirm(`Delete category "${name}"?`)) return;
    try {
      await api.categories.delete(catId);
      setSuccess(`Deleted category "${name}"`);
      setCategories((prev) => prev.filter((c) => c.id !== catId));
    } catch (err) {
      console.error('Delete category error:', err);
      setError(err.message || 'Failed to delete category.');
    }
  };

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Subject & Category Taxonomies</h1>
          <p>Govern the classification taxonomies used across institutional catalogue discovery.</p>
        </div>
        <div className="page-actions">
          <button type="button" className="btn btn-primary btn-sm" onClick={handleOpenAdd}>
            <Plus size={16} /> Add New Category
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
        <LoadingSpinner message="Loading academic categories..." />
      ) : categories.length === 0 ? (
        <EmptyState
          icon={Tags}
          title="No Categories Defined"
          description="Create standard classifications for library holdings."
          action={
            <button type="button" className="btn btn-primary btn-sm" onClick={handleOpenAdd}>
              Add Category
            </button>
          }
        />
      ) : (
        <div className="table-container">
          <table className="formal-table">
            <thead>
              <tr>
                <th style={{ width: '80px' }}>ID</th>
                <th>Classification Name</th>
                <th>Scope & Description</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {categories.map((c) => (
                <tr key={c.id}>
                  <td><code>#{c.id}</code></td>
                  <td><strong>{c.name}</strong></td>
                  <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{c.description || '—'}</td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <div style={{ display: 'inline-flex', gap: '0.4rem' }}>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleOpenEdit(c)}
                        title="Edit Category"
                      >
                        <Edit2 size={14} />
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={() => handleDelete(c.id, c.name)}
                        title="Delete Category"
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
        title={editingCat ? `Edit Category #${editingCat.id}` : 'Create New Category'}
      >
        <form onSubmit={handleSave}>
          <div className="form-group">
            <label className="form-label" htmlFor="c-name">Category Name *</label>
            <input
              id="c-name"
              type="text"
              className="form-input"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="c-desc">Taxonomy Scope / Description</label>
            <textarea
              id="c-desc"
              className="form-textarea"
              rows={3}
              placeholder="e.g. Holdings covering quantum mechanics, condensed matter, and optics..."
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              {editingCat ? 'Save Changes' : 'Create Category'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
