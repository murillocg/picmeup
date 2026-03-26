import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import { checkAuth as apiCheckAuth } from '../services/api';

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

  const login = useCallback(() => {
    // Trigger Basic Auth by making an authenticated request
    // The browser will show the credential dialog on 401
    const credentials = window.prompt('Enter credentials as username:password');
    if (!credentials) return;

    const [user, ...passParts] = credentials.split(':');
    const pass = passParts.join(':');

    // Store credentials and verify
    sessionStorage.setItem('auth', btoa(`${user}:${pass}`));
    refresh();
  }, [refresh]);

  const logout = useCallback(() => {
    sessionStorage.removeItem('auth');
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
