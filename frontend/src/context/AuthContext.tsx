import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import { checkAuth as apiCheckAuth, loginWithBasicAuth } from '../services/api';

interface AuthState {
  authenticated: boolean;
  username: string | null;
  loading: boolean;
  login: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthState>({
  authenticated: false,
  username: null,
  loading: true,
  login: () => {},
  logout: () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(false);
  const [username, setUsername] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const data = await apiCheckAuth();
      setAuthenticated(data.authenticated);
      setUsername(data.username ?? null);
    } catch {
      setAuthenticated(false);
      setUsername(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const login = useCallback(async () => {
    try {
      const data = await loginWithBasicAuth();
      setAuthenticated(data.authenticated);
      setUsername(data.username ?? null);
    } catch {
      // User cancelled the dialog or credentials were wrong
    }
  }, []);

  const logout = useCallback(() => {
    // Clear browser-cached credentials by sending a request with wrong credentials
    // This forces the browser to forget the Basic Auth session
    fetch('/api/auth/login', {
      headers: { Authorization: 'Basic ' + btoa('logout:logout') },
    }).catch(() => {});
    setAuthenticated(false);
    setUsername(null);
  }, []);

  return (
    <AuthContext.Provider value={{ authenticated, username, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
