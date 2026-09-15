import React, { createContext, useContext, useState, useEffect } from 'react';
import { api } from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('astralib_token'));
  const [loading, setLoading] = useState(true);

  // Initialize auth state
  useEffect(() => {
    async function initAuth() {
      const storedToken = localStorage.getItem('astralib_token');
      const storedUser = localStorage.getItem('astralib_user');

      if (storedToken && storedUser) {
        try {
          setUser(JSON.parse(storedUser));
          // Optionally fetch freshest user profile from backend
          const freshUser = await api.auth.getCurrentUser();
          if (freshUser) {
            setUser(freshUser);
            localStorage.setItem('astralib_user', JSON.stringify(freshUser));
          }
        } catch (err) {
          console.warn('Could not refresh user profile:', err.message);
          // If token invalid, clear
          if (err.status === 401 || err.status === 403) {
            logout();
          }
        }
      }
      setLoading(false);
    }

    initAuth();
  }, []);

  const login = async (email, password) => {
    const data = await api.auth.login(email, password);
    // Backend returns: { token, userId, name, role }
    const authUser = {
      id: data.userId,
      name: data.name,
      email: email,
      role: data.role || 'USER',
    };

    setToken(data.token);
    setUser(authUser);

    localStorage.setItem('astralib_token', data.token);
    localStorage.setItem('astralib_user', JSON.stringify(authUser));

    return authUser;
  };

  const register = async (name, email, password) => {
    const data = await api.auth.register(name, email, password);
    return data;
  };

  const logout = () => {
    setUser(null);
    setToken(null);
    localStorage.removeItem('astralib_token');
    localStorage.removeItem('astralib_user');
  };

  const isRole = (roleName) => {
    if (!user || !user.role) return false;
    return user.role.toUpperCase() === roleName.toUpperCase();
  };

  const isUser = () => isRole('USER');
  const isLibrarian = () => isRole('LIBRARIAN') || isRole('ADMIN');
  const isAdmin = () => isRole('ADMIN');

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        loading,
        login,
        register,
        logout,
        isAuthenticated: !!token && !!user,
        isUser,
        isLibrarian,
        isAdmin,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
