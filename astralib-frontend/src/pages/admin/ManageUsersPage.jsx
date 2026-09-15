import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';
import { Users2, Shield, Edit2, AlertCircle, CheckCircle2, Search } from 'lucide-react';
import { Badge, LoadingSpinner, Modal } from '../../components/common/Components';

export function ManageUsersPage() {
  const { user: currentUser } = useAuth();
  const [userList, setUserList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Role Edit Modal
  const [selectedUser, setSelectedUser] = useState(null);
  const [newRole, setNewRole] = useState('USER');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [updating, setUpdating] = useState(false);

  useEffect(() => {
    // Note: Backend currently supports update-role endpoint and me endpoint.
    // We populate known institutional accounts and active user session for administration.
    const initialUsers = [
      { id: currentUser?.id || 1, name: currentUser?.name || 'Administrator', email: currentUser?.email || 'admin@astralib.com', role: currentUser?.role || 'ADMIN', status: 'ACTIVE' },
      { id: 2, name: 'Library Desk Attendant', email: 'librarian@example.com', role: 'LIBRARIAN', status: 'ACTIVE' },
      { id: 3, name: 'Standard Academic User', email: 'testuser@astralib.com', role: 'USER', status: 'ACTIVE' },
      { id: 4, name: 'Research Scholar', email: 'researcher@university.edu', role: 'USER', status: 'ACTIVE' },
    ];
    setUserList(initialUsers);
    setLoading(false);
  }, [currentUser]);

  const handleOpenRoleModal = (u) => {
    setSelectedUser(u);
    setNewRole(u.role);
    setIsModalOpen(true);
  };

  const handleRoleUpdate = async (e) => {
    e.preventDefault();
    if (!selectedUser) return;

    setUpdating(true);
    setError('');
    setSuccess('');

    try {
      // Connect to backend: POST /api/admin/update-role/{userId}?role={newRole}
      await api.admin.updateUserRole(selectedUser.id, newRole);

      setSuccess(`Successfully updated role for ${selectedUser.email} to ${newRole}`);
      setUserList((prev) =>
        prev.map((u) => (u.id === selectedUser.id ? { ...u, role: newRole } : u))
      );
      setIsModalOpen(false);
    } catch (err) {
      console.error('Role update error:', err);
      setError(err.message || 'Failed to update user role on server.');
    } finally {
      setUpdating(false);
    }
  };

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>User Roles & Access Governance</h1>
          <p>Authorize institutional roles (USER, LIBRARIAN, ADMIN) to regulate catalogue management and administration rights.</p>
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

      <div className="table-container">
        <table className="formal-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Member Name</th>
              <th>Institutional Email</th>
              <th>Current Role</th>
              <th>Account Status</th>
              <th style={{ textAlign: 'right' }}>Authorization Action</th>
            </tr>
          </thead>
          <tbody>
            {userList.map((u) => (
              <tr key={u.id}>
                <td><code>#{u.id}</code></td>
                <td><strong>{u.name}</strong></td>
                <td><code>{u.email}</code></td>
                <td>
                  <Badge variant={u.role === 'ADMIN' ? 'error' : u.role === 'LIBRARIAN' ? 'gold' : 'primary'}>
                    {u.role}
                  </Badge>
                </td>
                <td>
                  <Badge variant="success">Active</Badge>
                </td>
                <td style={{ textAlign: 'right' }}>
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => handleOpenRoleModal(u)}
                  >
                    <Shield size={14} />
                    <span>Change Role</span>
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Role Assignment Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={`Change Role: ${selectedUser?.name}`}
      >
        <form onSubmit={handleRoleUpdate}>
          <div className="form-group">
            <label className="form-label">Member Email</label>
            <input type="text" className="form-input" value={selectedUser?.email || ''} readOnly disabled />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="select-role">Institutional Authorization Role</label>
            <select
              id="select-role"
              className="form-select"
              value={newRole}
              onChange={(e) => setNewRole(e.target.value)}
            >
              <option value="USER">USER (Search, Read, Favorite, Q&A)</option>
              <option value="LIBRARIAN">LIBRARIAN (Catalogue CRUD, Author/Category/Resource Ingestion)</option>
              <option value="ADMIN">ADMIN (Full Console, Role Modification, AI Vectors & Dataset Ingest)</option>
            </select>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setIsModalOpen(false)} disabled={updating}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={updating}>
              {updating ? 'Updating Role...' : 'Confirm Role Change'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
