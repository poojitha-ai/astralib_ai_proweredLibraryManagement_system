import React from 'react';

import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
} from 'react-router-dom';

import {
  AuthProvider,
} from './context/AuthContext';

import {
  AppLayout,
} from './components/layout/AppLayout';

import {
  ProtectedRoute,
} from './components/layout/ProtectedRoute';

// =========================================================
// AUTH PAGES
// =========================================================

import {
  LoginPage,
} from './pages/auth/LoginPage';

import {
  RegisterPage,
} from './pages/auth/RegisterPage';

// =========================================================
// USER PAGES
// =========================================================

import {
  DashboardPage,
} from './pages/user/DashboardPage';

import {
  CataloguePage,
} from './pages/catalogue/CataloguePage';

import {
  ResourceDetailPage,
} from './pages/catalogue/ResourceDetailPage';

import {
  DocumentReaderPage,
} from './pages/reader/DocumentReaderPage';

import {
  SmartSearchPage,
} from './pages/search/SmartSearchPage';

import {
  ResearchAssistantPage,
} from './pages/research/ResearchAssistantPage';
import {
  KnowledgeGraphPage,
} from './pages/knowledge/KnowledgeGraphPage';
import {
  MyResearchPage,
} from './pages/research/MyResearchPage';

import {
  RecommendationsPage,
} from './pages/user/RecommendationsPage';

import {
  FavoritesPage,
} from './pages/user/FavoritesPage';

import {
  ReadingHistoryPage,
} from './pages/user/ReadingHistoryPage';

import {
  StudyHallPage,
} from './pages/user/StudyHallPage';

import {
  StudyRoomPage,
} from './pages/user/StudyRoomPage';

import {
  NotificationsPage,
} from './pages/user/NotificationsPage';

import {
  ProfilePage,
} from './pages/user/ProfilePage';

// =========================================================
// LIBRARIAN PAGES
// =========================================================

import {
  LibrarianDashboardPage,
} from './pages/librarian/LibrarianDashboardPage';

import {
  ManageBooksPage,
} from './pages/librarian/ManageBooksPage';

import {
  ManageAuthorsPage,
} from './pages/librarian/ManageAuthorsPage';

import {
  ManageCategoriesPage,
} from './pages/librarian/ManageCategoriesPage';

import {
  ManageDigitalResourcesPage,
} from './pages/librarian/ManageDigitalResourcesPage';

// =========================================================
// ADMIN PAGES
// =========================================================

import {
  AdminDashboardPage,
} from './pages/admin/AdminDashboardPage';

import {
  ManageUsersPage,
} from './pages/admin/ManageUsersPage';

import {
  AiConfigPage,
} from './pages/admin/AiConfigPage';

import {
  DatasetLoaderPage,
} from './pages/admin/DatasetLoaderPage';

export function App() {
  return (
    <AuthProvider>

      <BrowserRouter>

        <Routes>

          {/* ================================================
              PUBLIC AUTH ROUTES
          ================================================= */}

          <Route
            path="/login"
            element={<LoginPage />}
          />

          <Route
            path="/register"
            element={<RegisterPage />}
          />

          {/* ================================================
              PROTECTED APPLICATION
          ================================================= */}

          <Route
            element={<ProtectedRoute />}
          >

            <Route
              element={<AppLayout />}
            >

              <Route
                path="/"
                element={
                  <Navigate
                    to="/dashboard"
                    replace
                  />
                }
              />

              <Route
                path="/dashboard"
                element={<DashboardPage />}
              />

              {/* ============================================
                  CATALOGUE
              ============================================= */}

              <Route
                path="/catalogue"
                element={<CataloguePage />}
              />

              <Route
                path="/books/:id"
                element={<ResourceDetailPage />}
              />

              <Route
                path="/digital-library"
                element={
                  <Navigate
                    to="/catalogue"
                    replace
                  />
                }
              />

              <Route
                path="/reader/:resourceId"
                element={<DocumentReaderPage />}
              />

              {/* ============================================
                  SEARCH + RESEARCH
              ============================================= */}

              <Route
                path="/search"
                element={<SmartSearchPage />}
              />

              <Route
                path="/research"
                element={<ResearchAssistantPage />}
              />
              <Route
                path="/knowledge-graph"
                element={<KnowledgeGraphPage />}
              />
              {/* ============================================
                  USER RESEARCH
              ============================================= */}

              <Route
                path="/recommendations"
                element={<RecommendationsPage />}
              />

              <Route
                path="/my-research"
                element={<MyResearchPage />}
              />

              <Route
                path="/favorites"
                element={<FavoritesPage />}
              />

              <Route
                path="/history"
                element={<ReadingHistoryPage />}
              />

              {/* ============================================
                  STUDY HALL
              ============================================= */}

              <Route
                path="/study-hall"
                element={<StudyHallPage />}
              />

              <Route
                path="/study-hall/:groupId/room"
                element={<StudyRoomPage />}
              />

              {/* ============================================
                  USER SETTINGS
              ============================================= */}

              <Route
                path="/notifications"
                element={<NotificationsPage />}
              />

              <Route
                path="/profile"
                element={<ProfilePage />}
              />

              {/* ============================================
                  LIBRARIAN
              ============================================= */}

              <Route
                element={
                  <ProtectedRoute
                    allowedRoles={[
                      'LIBRARIAN',
                      'ADMIN',
                    ]}
                  />
                }
              >

                <Route
                  path="/librarian"
                  element={
                    <LibrarianDashboardPage />
                  }
                />

                <Route
                  path="/librarian/books"
                  element={
                    <ManageBooksPage />
                  }
                />

                <Route
                  path="/librarian/authors"
                  element={
                    <ManageAuthorsPage />
                  }
                />

                <Route
                  path="/librarian/categories"
                  element={
                    <ManageCategoriesPage />
                  }
                />

                <Route
                  path="/librarian/resources"
                  element={
                    <ManageDigitalResourcesPage />
                  }
                />

              </Route>

              {/* ============================================
                  ADMIN
              ============================================= */}

              <Route
                element={
                  <ProtectedRoute
                    allowedRoles={[
                      'ADMIN',
                    ]}
                  />
                }
              >

                <Route
                  path="/admin"
                  element={
                    <AdminDashboardPage />
                  }
                />

                <Route
                  path="/admin/users"
                  element={
                    <ManageUsersPage />
                  }
                />

                <Route
                  path="/admin/ai-config"
                  element={
                    <AiConfigPage />
                  }
                />

                <Route
                  path="/admin/datasets"
                  element={
                    <DatasetLoaderPage />
                  }
                />

              </Route>

            </Route>

          </Route>

          {/* ================================================
              FALLBACK
          ================================================= */}

          <Route
            path="*"
            element={
              <Navigate
                to="/dashboard"
                replace
              />
            }
          />

        </Routes>

      </BrowserRouter>

    </AuthProvider>
  );
}

export default App;