import React, { useState, useEffect } from 'react';
import { api } from '../../services/api';
import {
  SlidersHorizontal,
  Cpu,
  RefreshCw,
  Layers,
  CheckCircle2,
  AlertCircle,
  ShieldCheck,
  Zap,
} from 'lucide-react';
import { Badge, LoadingSpinner } from '../../components/common/Components';

export function AiConfigPage() {
  const [vectorStats, setVectorStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [ingesting, setIngesting] = useState(false);
  const [reingesting, setReingesting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadVectorStats = async () => {
    setLoading(true);
    setError('');
    try {
      const stats = await api.admin.getVectorStats();
      setVectorStats(stats);
    } catch (err) {
      console.error('Vector stats load failure:', err);
      setError(err.message || 'Unable to retrieve vector index statistics.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadVectorStats();
  }, []);

  const handleTriggerIngestion = async () => {
    setIngesting(true);
    setError('');
    setSuccess('');
    try {
      const res = await api.admin.triggerVectorIngest();
      setSuccess(`Vector ingestion complete. Generated ${res.bookVectorsCreated || 0} book vectors, ${res.paperVectorsCreated || 0} paper vectors, and ${res.documentVectorsCreated || 0} document vectors.`);
      loadVectorStats();
    } catch (err) {
      console.error('Ingestion failed:', err);
      setError(err.message || 'Vector ingestion pipeline failed.');
    } finally {
      setIngesting(false);
    }
  };

  const handleReingestAll = async () => {
    if (!window.confirm('Reingest all vectors? This will refresh all embedding records in MySQL.')) return;

    setReingesting(true);
    setError('');
    setSuccess('');
    try {
      const res = await api.admin.reingestVectors();
      setSuccess('Re-ingestion triggered successfully.');
      loadVectorStats();
    } catch (err) {
      console.error('Re-ingestion error:', err);
      setError(err.message || 'Vector re-ingestion failed.');
    } finally {
      setReingesting(false);
    }
  };

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Semantic Vector & AI Configuration</h1>
          <p>Governance of dense vector embeddings, chunking parameters, and similarity index status.</p>
        </div>
        <div className="page-actions">
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={loadVectorStats}
            disabled={loading || ingesting}
          >
            <RefreshCw size={14} className={loading ? 'loading-spinner' : ''} />
            Refresh Telemetry
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

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gap: '1.75rem' }}>
        {/* Left Column: Vector Index Telemetry & Controls */}
        <div style={{ gridColumn: 'span 8' }}>
          <div className="card" style={{ padding: '2rem', marginBottom: '1.75rem' }}>
            <div className="card-header">
              <h2 className="card-title">Vector Index Telemetry</h2>
              <Badge variant="gold">Milestone 5 Vector Store</Badge>
            </div>

            {loading ? (
              <LoadingSpinner message="Checking vector index..." />
            ) : (
              <div className="table-container">
                <table className="formal-table">
                  <thead>
                    <tr>
                      <th>Document Domain</th>
                      <th>Vector Chunks Count</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td><strong>Monographs & Books</strong></td>
                      <td><code>{vectorStats?.bookVectorsCount ?? 0} vectors</code></td>
                      <td><Badge variant="success">Indexed</Badge></td>
                    </tr>
                    <tr>
                      <td><strong>Scholarly Papers</strong></td>
                      <td><code>{vectorStats?.paperVectorsCount ?? 0} vectors</code></td>
                      <td><Badge variant="success">Indexed</Badge></td>
                    </tr>
                    <tr>
                      <td><strong>Archival Documents</strong></td>
                      <td><code>{vectorStats?.documentVectorsCount ?? 0} vectors</code></td>
                      <td><Badge variant="success">Indexed</Badge></td>
                    </tr>
                    <tr style={{ background: 'var(--bg-app)', fontWeight: 600 }}>
                      <td>Total Vector Records</td>
                      <td><code>{vectorStats?.totalVectorRecords ?? 0} total embeddings</code></td>
                      <td><Badge variant="primary">Active</Badge></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            )}

            {/* Ingestion Actions */}
            <div style={{ marginTop: '1.5rem', display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn-primary"
                onClick={handleTriggerIngestion}
                disabled={ingesting || reingesting}
              >
                {ingesting ? (
                  <>
                    <div className="loading-spinner" style={{ width: '16px', height: '16px', borderWidth: '2px' }} />
                    <span>Processing Ingestion...</span>
                  </>
                ) : (
                  <>
                    <Zap size={16} />
                    <span>Trigger Vector Ingestion</span>
                  </>
                )}
              </button>

              <button
                type="button"
                className="btn btn-secondary"
                onClick={handleReingestAll}
                disabled={ingesting || reingesting}
              >
                {reingesting ? 'Re-ingesting...' : 'Re-ingest All Vectors'}
              </button>
            </div>
          </div>
        </div>

        {/* Right Column: Non-Secret Pipeline Specifications */}
        <div style={{ gridColumn: 'span 4' }}>
          <div className="card" style={{ padding: '1.5rem', backgroundColor: 'var(--bg-surface)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
              <ShieldCheck size={20} color="var(--primary-ink)" />
              <h3 className="card-title" style={{ fontSize: '1.05rem' }}>Pipeline Architecture</h3>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', fontSize: '0.85rem' }}>
              <div style={{ padding: '0.65rem', background: 'var(--bg-app)', borderRadius: 'var(--radius-sm)' }}>
                <strong>Vector Dimension:</strong> 384-dimensional dense embeddings
              </div>
              <div style={{ padding: '0.65rem', background: 'var(--bg-app)', borderRadius: 'var(--radius-sm)' }}>
                <strong>Chunk Size:</strong> 500 characters (50 char overlap)
              </div>
              <div style={{ padding: '0.65rem', background: 'var(--bg-app)', borderRadius: 'var(--radius-sm)' }}>
                <strong>Similarity Metric:</strong> Cosine Similarity ($A \cdot B / (||A|| \cdot ||B||)$)
              </div>
              <div style={{ padding: '0.65rem', background: 'var(--bg-app)', borderRadius: 'var(--radius-sm)' }}>
                <strong>RAG Synthesis:</strong> Grounded context injection with citation extraction
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
