import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';
import { User, Mail, Shield, Calendar, BookOpen, Bookmark, History } from 'lucide-react';
import { Badge, LoadingSpinner } from '../../components/common/Components';

export function ProfilePage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState(null);
  const [readingCount, setReadingCount] = useState(0);
  const [favCount, setFavCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    async function loadUserProfile() {
      setLoading(true);
      setError('');
      try {
        const [userData, historyData, favData] = await Promise.allSettled([
          api.auth.getCurrentUser(),
          api.readingHistory.getAll(),
          api.favorites.getAll(),
        ]);

        if (userData.status === 'fulfilled') {
          setProfile(userData.value);
        } else {
          setProfile(user);
        }

        if (historyData.status === 'fulfilled' && Array.isArray(historyData.value)) {
          setReadingCount(historyData.value.length);
        }

        if (favData.status === 'fulfilled' && Array.isArray(favData.value)) {
          setFavCount(favData.value.length);
        }
      } catch (err) {
        console.error('Failed to load profile:', err);
        setError(err.message || 'Unable to load profile.');
      } finally {
        setLoading(false);
      }
    }

    loadUserProfile();
  }, [user]);

  if (loading) {
    return <LoadingSpinner message="Retrieving institutional member profile..." />;
  }

  const currentUser = profile || user;

  return (
    <div className="page-container">
      {/* Formal Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Member Profile</h1>
          <p>Institutional identity and academic credentials associated with this session.</p>
        </div>
      </div>

      {error && (
        <div className="banner banner-warning">
          <span>{error}</span>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gap: '1.75rem' }}>
        {/* Profile Card */}
        <div style={{ gridColumn: 'span 8' }}>
          <div className="card" style={{ padding: '2rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', marginBottom: '1.75rem', paddingBottom: '1.5rem', borderBottom: '1px solid var(--border-light)' }}>
              <div
                style={{
                  width: '64px',
                  height: '64px',
                  borderRadius: 'var(--radius-sm)',
                  backgroundColor: 'var(--primary-ink)',
                  color: '#FFFFFF',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '1.6rem',
                  fontFamily: 'var(--font-brand)',
                  fontWeight: 700,
                }}
              >
                {currentUser?.name ? currentUser.name[0].toUpperCase() : 'U'}
              </div>

              <div>
                <h2 style={{ fontSize: '1.4rem', color: 'var(--primary-ink)', margin: 0 }}>
                  {currentUser?.name || 'Academic Member'}
                </h2>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.35rem' }}>
                  <Badge variant={currentUser?.role === 'ADMIN' ? 'error' : currentUser?.role === 'LIBRARIAN' ? 'gold' : 'primary'}>
                    Role: {currentUser?.role || 'USER'}
                  </Badge>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    ID: #{currentUser?.id || '—'}
                  </span>
                </div>
              </div>
            </div>

            {/* Profile Table */}
            <div className="table-container">
              <table className="formal-table">
                <tbody>
                  <tr>
                    <td style={{ width: '180px', color: 'var(--text-secondary)', fontWeight: 600 }}>Full Name</td>
                    <td>{currentUser?.name || '—'}</td>
                  </tr>
                  <tr>
                    <td style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>Institutional Email</td>
                    <td><code>{currentUser?.email || '—'}</code></td>
                  </tr>
                  <tr>
                    <td style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>Account Role</td>
                    <td><strong>{currentUser?.role || 'USER'}</strong></td>
                  </tr>
                  <tr>
                    <td style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>Registration Date</td>
                    <td>
                      {currentUser?.createdAt
                        ? new Date(currentUser.createdAt).toLocaleDateString(undefined, {
                            year: 'numeric',
                            month: 'long',
                            day: 'numeric',
                          })
                        : 'Active Member'}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* Right Stats Column */}
        <div style={{ gridColumn: 'span 4' }}>
          <div className="card" style={{ marginBottom: '1.5rem' }}>
            <h3 className="card-title" style={{ marginBottom: '1rem', borderBottom: '1px solid var(--border-light)', paddingBottom: '0.5rem' }}>
              Academic Activity Metrics
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.75rem', background: 'var(--bg-app)', borderRadius: 'var(--radius-sm)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Bookmark size={16} color="var(--primary-ink)" />
                  <span style={{ fontSize: '0.88rem', fontWeight: 500 }}>Saved Favorites</span>
                </div>
                <strong>{favCount}</strong>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.75rem', background: 'var(--bg-app)', borderRadius: 'var(--radius-sm)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <History size={16} color="var(--primary-ink)" />
                  <span style={{ fontSize: '0.88rem', fontWeight: 500 }}>Reading Sessions</span>
                </div>
                <strong>{readingCount}</strong>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
