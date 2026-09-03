import { useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from './LoadingSpinner';

/**
 * Catches the addresses people type or have bookmarked when they mean "sign in" —
 * /admin, /login — and starts the real flow instead of leaving them on a dead URL.
 * Someone already signed in is sent home rather than bounced through Cognito again.
 */
export default function SignInRedirect() {
  const { authenticated, loading, signIn } = useAuth();

  useEffect(() => {
    if (!loading && !authenticated) signIn();
  }, [loading, authenticated, signIn]);

  if (authenticated) return <Navigate to="/" replace />;
  return <LoadingSpinner />;
}
