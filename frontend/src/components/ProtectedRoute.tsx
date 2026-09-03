import { useEffect } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from './LoadingSpinner';

interface ProtectedRouteProps {
  /** Omit to allow any signed-in user. */
  requireAdmin?: boolean;
}

// Admin pages used to mount regardless of auth, fire their request, and render
// "Failed to load ..." on the 401. Gate them at the route instead, so a signed-out
// visitor is sent to sign in rather than shown a broken page.
export default function ProtectedRoute({ requireAdmin = false }: ProtectedRouteProps) {
  const { authenticated, isAdmin, isPhotographer, loading, signIn } = useAuth();

  // Straight to Cognito rather than via a page whose only content is a button that
  // does this. A full-page navigation, so it cannot be a router redirect.
  useEffect(() => {
    if (!loading && !authenticated) signIn();
  }, [loading, authenticated, signIn]);

  // The session check is still in flight — rendering either branch now would
  // flash the page or bounce someone who is in fact signed in.
  if (loading || !authenticated) return <LoadingSpinner />;

  // Signed in, but not for this page. A photographer following an admin link should land
  // somewhere useful rather than being bounced to a sign-in they have already completed.
  if (requireAdmin && !isAdmin) {
    return <Navigate to={isPhotographer ? '/my-events' : '/'} replace />;
  }

  return <Outlet />;
}
