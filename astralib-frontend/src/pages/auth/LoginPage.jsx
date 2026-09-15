import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { BookOpen, AlertCircle } from 'lucide-react';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const user = await login(email, password);
      // Redirect based on role if appropriate
      if (user.role === 'ADMIN') {
        navigate('/admin');
      } else if (user.role === 'LIBRARIAN') {
        navigate('/librarian');
      } else {
        navigate('/dashboard');
      }
    } catch (err) {
      setError(err.message || 'Authentication failed. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-wrapper">
      <div className="auth-card">
        <div className="auth-header">
          <div className="auth-brand-logo">A</div>
          <h1 className="auth-title">AstraLib</h1>
          <p className="auth-subtitle">Digital Library & Knowledge Discovery</p>
        </div>

        {error && (
          <div className="banner banner-error" role="alert">
            <AlertCircle size={18} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="login-email">Institutional Email</label>
            <input
              id="login-email"
              type="email"
              className="form-input"
              placeholder="e.g. researcher@university.edu"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="login-password">Password</label>
            <input
              id="login-password"
              type="password"
              className="form-input"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button
            type="submit"
            className="btn btn-primary btn-lg"
            style={{ width: '100%', marginTop: '0.5rem' }}
            disabled={loading}
          >
            {loading ? 'Authenticating...' : 'Sign In to AstraLib'}
          </button>
        </form>

        <div style={{ marginTop: '1.75rem', textAlign: 'center', fontSize: '0.88rem' }}>
          <span style={{ color: 'var(--text-secondary)' }}>New researcher or student? </span>
          <Link to="/register" style={{ fontWeight: 600 }}>Create Library Account</Link>
        </div>

        {/* Demo Credentials Reference */}
        <div
          style={{
            marginTop: '1.5rem',
            padding: '0.75rem',
            background: 'var(--bg-app)',
            borderRadius: 'var(--radius-sm)',
            border: '1px solid var(--border-light)',
            fontSize: '0.78rem',
            color: 'var(--text-secondary)',
          }}
        >
          <strong>Institutional Demo Accounts:</strong>
          <div style={{ marginTop: '0.25rem' }}>• User: <code>testuser@astralib.com</code> / <code>password123</code></div>
          <div>• Admin: <code>admin@astralib.com</code> / <code>adminpassword123</code></div>
        </div>
      </div>
    </div>
  );
}
