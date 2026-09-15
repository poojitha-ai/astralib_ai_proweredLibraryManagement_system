import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../services/api';
import {
  ShieldAlert,
  Database,
  SlidersHorizontal,
  Users2,
  Cpu,
  Layers,
  BookOpen,
  Activity,
  CheckCircle,
} from 'lucide-react';
import { Badge, LoadingSpinner } from '../../components/common/Components';

export function AdminDashboardPage() {
  const [datasetStats, setDatasetStats] = useState(null);
  const [vectorStats, setVectorStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function loadAdminMetrics() {
      setLoading(true);
      setError('');
      try {
        const [ds, vs] = await Promise.allSettled([
          api.admin.getDatasetStats(),
          api.admin.getVectorStats(),
        ]);

        if (ds.status === 'fulfilled') setDatasetStats(ds.value);
        if (vs.status === 'fulfilled') setVectorStats(vs.value);
      } catch (err) {
        console.error('Admin stats failure:', err);
        setError('Failed to fetch system administrator metrics.');
      } finally {
        setLoading(false);
      }
    }

    loadAdminMetrics();
  }, []);

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>System Administration Console</h1>
          <p>Global repository governance, dataset loader status, vector indexes, and user role management.</p>
        </div>
      </div>

      {error && (
        <div className="banner banner-warning">
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <LoadingSpinner message="Querying system telemetry and index records..." />
      ) : (
        <div>
          {/* Institutional Telemetry Tiles */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
            <div className="card" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.78rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 }}>Catalogue Books</span>
                <BookOpen size={20} color="var(--primary-ink)" />
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--primary-ink)', fontFamily: 'var(--font-serif)' }}>
                {datasetStats?.booksCount ?? '—'}
              </div>
              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '0.4rem' }}>
                Authors: {datasetStats?.authorsCount ?? '—'} | Categories: {datasetStats?.categoriesCount ?? '—'}
              </div>
            </div>

            <div className="card" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.78rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 }}>Registered Users</span>
                <Users2 size={20} color="var(--primary-ink)" />
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--primary-ink)', fontFamily: 'var(--font-serif)' }}>
                {datasetStats?.usersCount ?? '—'}
              </div>
              <Link to="/admin/users" style={{ fontSize: '0.8rem', color: 'var(--primary-ink)', marginTop: '0.4rem', display: 'inline-block' }}>
                Manage User Roles →
              </Link>
            </div>

            <div className="card" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.78rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 }}>Vector Chunks</span>
                <Cpu size={20} color="var(--accent-gold)" />
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--accent-gold)', fontFamily: 'var(--font-serif)' }}>
                {vectorStats?.totalVectorRecords ?? '—'}
              </div>
              <Link to="/admin/ai-config" style={{ fontSize: '0.8rem', color: 'var(--primary-ink)', marginTop: '0.4rem', display: 'inline-block' }}>
                AI Vector Index Controls →
              </Link>
            </div>

            <div className="card" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.78rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 }}>Activity Interactions</span>
                <Activity size={20} color="var(--primary-ink)" />
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--secondary-slate)', fontFamily: 'var(--font-serif)' }}>
                {datasetStats?.activitiesProcessedCount ?? '—'}
              </div>
              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '0.4rem' }}>
                Favorites: {datasetStats?.favoritesCount ?? '—'} | Reads: {datasetStats?.readingHistoryCount ?? '—'}
              </div>
            </div>
          </div>

          {/* Quick Management Shortcuts */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '1.5rem' }}>
            <div className="card">
              <div className="card-header">
                <h3 className="card-title">User Role Governance</h3>
                <Badge variant="primary">Security</Badge>
              </div>
              <p style={{ fontSize: '0.88rem', color: 'var(--text-secondary)', marginBottom: '1.25rem' }}>
                Assign and modify role authorizations (USER, LIBRARIAN, ADMIN) across registered research members.
              </p>
              <Link to="/admin/users" className="btn btn-secondary btn-sm" style={{ width: '100%' }}>
                Open Role Manager
              </Link>
            </div>

            <div className="card">
              <div className="card-header">
                <h3 className="card-title">AI & Embedding Vectors</h3>
                <Badge variant="gold">Vector Index</Badge>
              </div>
              <p style={{ fontSize: '0.88rem', color: 'var(--text-secondary)', marginBottom: '1.25rem' }}>
                Inspect non-secret embedding dimensions, model names, and trigger full vector ingestion.
              </p>
              <Link to="/admin/ai-config" className="btn btn-secondary btn-sm" style={{ width: '100%' }}>
                Configure Vector System
              </Link>
            </div>

            <div className="card">
              <div className="card-header">
                <h3 className="card-title">Synthetic Dataset Loader</h3>
                <Badge variant="default">Datasets</Badge>
              </div>
              <p style={{ fontSize: '0.88rem', color: 'var(--text-secondary)', marginBottom: '1.25rem' }}>
                Trigger institutional dataset loading pipelines to populate books, authors, papers, and user activities.
              </p>
              <Link to="/admin/datasets" className="btn btn-secondary btn-sm" style={{ width: '100%' }}>
                Open Dataset Loader
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
