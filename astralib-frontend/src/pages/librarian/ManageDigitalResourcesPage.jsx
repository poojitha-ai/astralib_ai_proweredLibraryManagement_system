import React, { useState, useEffect } from 'react';
import { api } from '../../services/api';
import { Upload, FileText, CheckCircle2, AlertCircle, BookOpen, Download } from 'lucide-react';
import { Badge, LoadingSpinner, EmptyState } from '../../components/common/Components';

export function ManageDigitalResourcesPage() {
  const [books, setBooks] = useState([]);
  const [selectedBookId, setSelectedBookId] = useState('');
  const [resourceType, setResourceType] = useState('pdf');
  const [file, setFile] = useState(null);

  const [uploading, setUploading] = useState(false);
  const [loadingBooks, setLoadingBooks] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    async function loadBooks() {
      setLoadingBooks(true);
      try {
        const data = await api.books.getAll();
        const bList = Array.isArray(data) ? data : [];
        setBooks(bList);
        if (bList.length > 0) setSelectedBookId(bList[0].id);
      } catch (err) {
        console.error('Books load error:', err);
      } finally {
        setLoadingBooks(false);
      }
    }

    loadBooks();
  }, []);

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
    }
  };

  const handleUploadSubmit = async (e) => {
    e.preventDefault();
    if (!file) {
      setError('Please select a document file to upload.');
      return;
    }
    if (!selectedBookId) {
      setError('Please select an associated catalogue book.');
      return;
    }

    setUploading(true);
    setError('');
    setSuccess('');

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('bookId', selectedBookId);
      formData.append('resourceType', resourceType);

      const result = await api.resources.upload(formData);
      setSuccess(`Successfully uploaded digital asset "${result.fileName || file.name}" and attached to catalogue record #${selectedBookId}`);
      setFile(null);
      // Reset file input
      const fileInput = document.getElementById('digital-file-input');
      if (fileInput) fileInput.value = '';
    } catch (err) {
      console.error('Resource upload failure:', err);
      setError(err.message || 'Failed to upload digital file to local storage.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Digital Monograph & Resource Ingestion</h1>
          <p>Deposit PDF monographs, scholarly preprints, and research manuscripts into local institutional storage.</p>
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

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gap: '1.75rem' }}>
        {/* Upload Form Card */}
        <div style={{ gridColumn: 'span 7' }}>
          <div className="card" style={{ padding: '2rem' }}>
            <div className="card-header">
              <h2 className="card-title">Upload Digital Resource Asset</h2>
              <Badge variant="primary">Local Storage Ingestion</Badge>
            </div>

            {loadingBooks ? (
              <LoadingSpinner message="Checking catalogue volumes..." />
            ) : books.length === 0 ? (
              <EmptyState
                icon={BookOpen}
                title="No Books Available"
                description="You must register a catalogue book record before attaching digital resources."
              />
            ) : (
              <form onSubmit={handleUploadSubmit}>
                <div className="form-group">
                  <label className="form-label" htmlFor="select-book">
                    Associated Catalogue Monograph *
                  </label>
                  <select
                    id="select-book"
                    className="form-select"
                    value={selectedBookId}
                    onChange={(e) => setSelectedBookId(e.target.value)}
                    required
                  >
                    {books.map((b) => (
                      <option key={b.id} value={b.id}>
                        #{b.id} — {b.title} {b.author?.name ? `(${b.author.name})` : ''}
                      </option>
                    ))}
                  </select>
                  <span className="form-hint">
                    The uploaded document will be linked to this bibliographic record and indexed for reader streaming.
                  </span>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="res-type">Resource Classification</label>
                  <select
                    id="res-type"
                    className="form-select"
                    value={resourceType}
                    onChange={(e) => setResourceType(e.target.value)}
                  >
                    <option value="pdf">PDF Document / Complete Monograph</option>
                    <option value="paper">Peer-Reviewed Scholarly Paper</option>
                    <option value="manuscript">Archival Manuscript</option>
                    <option value="dataset">Supplementary Dataset</option>
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="digital-file-input">
                    Select Digital File (PDF, DOCX, TXT) *
                  </label>
                  <input
                    id="digital-file-input"
                    type="file"
                    className="form-input"
                    onChange={handleFileChange}
                    accept=".pdf,.doc,.docx,.txt,.epub"
                    required
                  />
                  {file && (
                    <div style={{ fontSize: '0.8rem', color: 'var(--primary-ink)', marginTop: '0.35rem' }}>
                      Selected: <strong>{file.name}</strong> ({Math.round(file.size / 1024)} KB)
                    </div>
                  )}
                </div>

                <button
                  type="submit"
                  className="btn btn-primary btn-lg"
                  style={{ width: '100%', marginTop: '1rem' }}
                  disabled={uploading || !file}
                >
                  {uploading ? (
                    <>
                      <div className="loading-spinner" style={{ width: '16px', height: '16px', borderWidth: '2px' }} />
                      <span>Ingesting into Repository...</span>
                    </>
                  ) : (
                    <>
                      <Upload size={16} />
                      <span>Ingest Digital Resource</span>
                    </>
                  )}
                </button>
              </form>
            )}
          </div>
        </div>

        {/* Repository Storage Architecture Notice */}
        <div style={{ gridColumn: 'span 5' }}>
          <div className="card" style={{ backgroundColor: 'var(--bg-app)', border: '1px solid var(--border-default)' }}>
            <h3 style={{ fontSize: '1.05rem', color: 'var(--primary-ink)', marginBottom: '0.75rem' }}>
              Institutional Storage Architecture
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: '1.6', marginBottom: '1rem' }}>
              Files uploaded here are stored in the server's local <code>uploads/</code> directory and tracked via the <code>digital_resources</code> database table.
            </p>
            <ul style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', paddingLeft: '1.25rem', lineHeight: '1.7' }}>
              <li>MIME-type probe verification</li>
              <li>Unique timestamp-prefixed filenames to prevent collision</li>
              <li>Automated Reading History logging when researchers stream files</li>
              <li>Ready for chunking into vector embedding records</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
