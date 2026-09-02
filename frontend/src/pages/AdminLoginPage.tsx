import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import usePageTitle from '../hooks/usePageTitle';

/**
 * Sign-in is a redirect to Cognito, handled entirely by the backend — there is no form
 * here because no credential ever passes through this page.
 */
export default function AdminLoginPage() {
  usePageTitle('Sign in');
  const { authenticated, isPhotographer, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!authenticated) return;
    // Land back on the page that bounced them here; photographers have their own home.
    const from = (location.state as { from?: { pathname: string } } | null)?.from;
    navigate(from?.pathname ?? (isPhotographer ? '/my-events' : '/'), { replace: true });
  }, [authenticated, isPhotographer, navigate, location.state]);

  return (
    <div className="max-w-sm mx-auto text-center">
      <h1 className="text-2xl font-bold text-gray-900 mb-2">Sign in</h1>
      <p className="text-gray-600 mb-6">For photographers and staff.</p>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <button
          onClick={signIn}
          className="w-full bg-brand-orange text-white py-3 rounded-lg hover:bg-brand-orange-dark font-semibold"
        >
          Continue
        </button>
        <p className="text-sm text-gray-500 mt-4">
          Sign in with Google, or have a one-time code emailed to you. No password needed.
        </p>
      </div>

      <p className="text-xs text-gray-400 mt-6">
        Access is by invitation. If you are not set up yet, ask the team to add your email
        address.
      </p>
    </div>
  );
}
