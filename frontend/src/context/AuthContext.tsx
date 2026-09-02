import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import {
  checkAuth as apiCheckAuth,
  login as apiLogin,
  logout as apiLogout,
  beginCognitoLogin,
} from '../services/api';
import type { Role } from '../services/api';

interface AuthState {
  authenticated: boolean;
  username: string | null;
  role: Role;
  isAdmin: boolean;
  isPhotographer: boolean;
  loading: boolean;
  /** Redirects to Cognito. The backend completes the exchange; no token reaches here. */
  signIn: () => void;
  /** The username/password path, kept until the session login is retired. */
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState>({
  authenticated: false,
  username: null,
  role: '',
  isAdmin: false,
  isPhotographer: false,
  loading: true,
  signIn: () => {},
  login: async () => {},
  logout: async () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(false);
  const [username, setUsername] = useState<string | null>(null);
  const [role, setRole] = useState<Role>('');
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const data = await apiCheckAuth();
      setAuthenticated(data.authenticated);
      setUsername(data.username ?? null);
      setRole(data.role ?? '');
    } catch {
      setAuthenticated(false);
      setUsername(null);
      setRole('');
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
    setRole(data.role ?? '');
  }, []);

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      setAuthenticated(false);
      setUsername(null);
      setRole('');
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{
        authenticated,
        username,
        role,
        // Derived rather than compared at each call site, so a role string never has to
        // be matched by hand in a component.
        isAdmin: role === 'ADMIN',
        isPhotographer: role === 'PHOTOGRAPHER',
        loading,
        signIn: beginCognitoLogin,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
