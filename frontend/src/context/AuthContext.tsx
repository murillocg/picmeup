import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import { checkAuth as apiCheckAuth, login as apiLogin, logout as apiLogout } from '../services/api';

interface AuthState {
  authenticated: boolean;
  username: string | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState>({
  authenticated: false,
  username: null,
  loading: true,
  login: async () => {},
  logout: async () => {},
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

  const login = useCallback(async (user: string, password: string) => {
    const data = await apiLogin(user, password);
    setAuthenticated(data.authenticated);
    setUsername(data.username ?? null);
  }, []);

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      setAuthenticated(false);
      setUsername(null);
    }
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
