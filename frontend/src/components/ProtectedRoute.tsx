import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from './LoadingSpinner';

// Admin pages used to mount regardless of auth, fire their request, and render
// "Failed to load ..." on the 401. Gate them at the route instead, so a signed-out
// visitor is sent to sign in rather than shown a broken page.
export default function ProtectedRoute() {
  const { authenticated, loading } = useAuth();
  const location = useLocation();

  // The session check is still in flight — rendering either branch now would
  // flash the page or bounce an admin who is in fact signed in.
  if (loading) return <LoadingSpinner />;

  if (!authenticated) {
    // Remember where they were headed so signing in can finish the journey.
    return <Navigate to="/admin" state={{ from: location }} replace />;
  }

  return <Outlet />;
}
