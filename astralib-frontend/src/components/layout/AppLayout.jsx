import React, { useState } from 'react';
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  BookOpen,
  LayoutDashboard,
  Library,
  Search,
  Bot,
  Network,
  Sparkles,
  Bookmark,
  History,
  Users2,
  Bell,
  User,
  LogOut,
  FolderKanban,
  FileText,
  Tags,
  ShieldAlert,
  SlidersHorizontal,
  Database,
  Menu,
  X,
  Compass,
  NotebookText,
} from 'lucide-react';

export function AppLayout() {
  const { user, logout, isLibrarian, isAdmin } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [topSearch, setTopSearch] = useState('');
  const [profileDropdownOpen, setProfileDropdownOpen] = useState(false);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (topSearch.trim()) {
      navigate(`/search?q=${encodeURIComponent(topSearch.trim())}`);
      setTopSearch('');
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const getInitials = (name) => {
    if (!name) return 'U';
    return name
      .split(' ')
      .map((part) => part[0])
      .join('')
      .substring(0, 2)
      .toUpperCase();
  };

  return (
    <div className="app-container">
      {/* Institutional Left Sidebar */}
      <aside className={`app-sidebar ${sidebarOpen ? 'open' : ''}`}>
        <div className="sidebar-header">
          <div className="brand-logo-icon">A</div>
          <div className="brand-title-group">
            <span className="brand-name">AstraLib</span>
            <span className="brand-subtitle">Digital Knowledge</span>
          </div>
          <button
            type="button"
            className="btn btn-sm btn-secondary"
            style={{ marginLeft: 'auto', display: 'none' }}
            onClick={() => setSidebarOpen(false)}
          >
            <X size={16} />
          </button>
        </div>

        <nav className="sidebar-nav">
          <div className="sidebar-section-title">Knowledge Base</div>

          <NavLink
            to="/dashboard"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setSidebarOpen(false)}
          >
            <LayoutDashboard size={18} />
            <span>Dashboard</span>
          </NavLink>

          <NavLink
            to="/catalogue"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setSidebarOpen(false)}
          >
            <BookOpen size={18} />
            <span>Catalogue</span>
          </NavLink>

          <NavLink
            to="/search"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setSidebarOpen(false)}
          >
            <Search size={18} />
            <span>Smart Search</span>
          </NavLink>

          <NavLink
            to="/research"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setSidebarOpen(false)}
          >
            <Bot size={18} />
            <span>Research Assistant</span>
          </NavLink>
          <NavLink
            to="/knowledge-graph"
            className={({ isActive }) =>
            `sidebar-link ${
              isActive
                ? 'active'
                : ''
            }`
          }
          onClick={() =>
            setSidebarOpen(false)
          }
        >
          <Network size={18} />
          <span>
            Knowledge Graph
          </span>
        </NavLink>
          <NavLink
            to="/recommendations"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setSidebarOpen(false)}
          >
            <Compass size={18} />
            <span>Recommendations</span>
          </NavLink>

          <div className="sidebar-section-title">My Research</div>
          <NavLink
            to="/my-research"
            className={({ isActive }) =>
                `sidebar-link ${isActive ? 'active' : ''}`
            }
            onClick={() => setSidebarOpen(false)}
          >
            <NotebookText size={18} />
            <span>Saved Research</span>
          </NavLink>
          <NavLink
            to="/favorites"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setSidebarOpen(false)}
          >
            <Bookmark size={18} />
            <span>Favorites</span>
          </NavLink>

          <NavLink
            to="/history"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setSidebarOpen(false)}
          >
            <History size={18} />
            <span>Reading History</span>
          </NavLink>

          <NavLink
            to="/study-hall"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setSidebarOpen(false)}
          >
            <Users2 size={18} />
            <span>Study Hall</span>
          </NavLink>

          {/* Librarian Tools */}
          {isLibrarian() && (
            <>
              <div className="sidebar-section-title">Librarian Desk</div>

              <NavLink
                to="/librarian"
                end
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <Library size={18} />
                <span>Desk Overview</span>
              </NavLink>

              <NavLink
                to="/librarian/books"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <FolderKanban size={18} />
                <span>Manage Books</span>
              </NavLink>

              <NavLink
                to="/librarian/authors"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <User size={18} />
                <span>Authors</span>
              </NavLink>

              <NavLink
                to="/librarian/categories"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <Tags size={18} />
                <span>Categories</span>
              </NavLink>

              <NavLink
                to="/librarian/resources"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <FileText size={18} />
                <span>Digital Uploads</span>
              </NavLink>
            </>
          )}

          {/* Administrator Tools */}
          {isAdmin() && (
            <>
              <div className="sidebar-section-title">Administration</div>

              <NavLink
                to="/admin"
                end
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <ShieldAlert size={18} />
                <span>Admin Console</span>
              </NavLink>

              <NavLink
                to="/admin/users"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <Users2 size={18} />
                <span>User Roles</span>
              </NavLink>

              <NavLink
                to="/admin/ai-config"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <SlidersHorizontal size={18} />
                <span>AI & Vectors</span>
              </NavLink>

              <NavLink
                to="/admin/datasets"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setSidebarOpen(false)}
              >
                <Database size={18} />
                <span>Dataset Loader</span>
              </NavLink>
            </>
          )}
        </nav>

        <div className="sidebar-footer">
          <div>AstraLib System v1.2</div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Academic Knowledge Portal</div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="app-main">
        {/* Topbar Header */}
        <header className="app-topbar">
          <div className="topbar-left">
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              style={{ display: 'none' }}
              onClick={() => setSidebarOpen(!sidebarOpen)}
            >
              <Menu size={18} />
            </button>

            <form onSubmit={handleSearchSubmit} className="topbar-search-form">
              <Search size={16} className="topbar-search-icon" />
              <input
                type="search"
                className="topbar-search-input"
                placeholder="Search catalogue, authors, or topics..."
                value={topSearch}
                onChange={(e) => setTopSearch(e.target.value)}
              />
            </form>
          </div>

          <div className="topbar-right">
            <NavLink to="/notifications" className="btn btn-secondary btn-sm" title="System Notifications">
              <Bell size={16} />
            </NavLink>

            {/* User Profile Pill */}
            <div style={{ position: 'relative' }}>
              <button
                type="button"
                className="user-profile-btn"
                onClick={() => setProfileDropdownOpen(!profileDropdownOpen)}
              >
                <div className="user-avatar-initials">{getInitials(user?.name)}</div>
                <div className="user-meta">
                  <div className="user-name-text">{user?.name || 'Academic User'}</div>
                  <div className="user-role-tag">{user?.role || 'USER'}</div>
                </div>
              </button>

              {profileDropdownOpen && (
                <div
                  className="card"
                  style={{
                    position: 'absolute',
                    right: 0,
                    top: '100%',
                    marginTop: '0.5rem',
                    width: '210px',
                    padding: '0.5rem',
                    zIndex: 100,
                    boxShadow: 'var(--shadow-dropdown)',
                  }}
                  onMouseLeave={() => setProfileDropdownOpen(false)}
                >
                  <NavLink
                    to="/profile"
                    className="sidebar-link"
                    onClick={() => setProfileDropdownOpen(false)}
                  >
                    <User size={16} />
                    <span>My Profile</span>
                  </NavLink>
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="sidebar-link"
                    style={{ width: '100%', border: 'none', background: 'transparent', textAlign: 'left', cursor: 'pointer' }}
                  >
                    <LogOut size={16} />
                    <span>Sign Out</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Dynamic Page View */}
        <main style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
