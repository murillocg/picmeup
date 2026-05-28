import { Link, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Layout() {
  const { authenticated, username, logout } = useAuth();

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <header className="bg-brand-charcoal shadow-sm">
        <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
          <Link to="/">
            <img src="/logo.png" alt="Elite Sport Photography" className="h-8 sm:h-10 w-auto" />
          </Link>
          <nav className="flex items-center gap-4">
            <Link to="/" className="text-gray-300 hover:text-white">
              Events
            </Link>
            <Link to="/faq" className="text-gray-300 hover:text-white">
              FAQ
            </Link>
            {authenticated && (
              <>
                <Link to="/admin/orders" className="text-gray-300 hover:text-white">
                  Orders
                </Link>
                <Link to="/admin/stats" className="text-gray-300 hover:text-white">
                  Stats
                </Link>
                {/* <Link to="/admin/passes" className="text-gray-300 hover:text-white">
                  Passes
                </Link> */}
                <Link
                  to="/events/new"
                  className="bg-brand-orange text-white px-4 py-2 rounded-lg hover:bg-brand-orange-dark"
                >
                  Create Event
                </Link>
              </>
            )}
            {authenticated && (
              <button
                onClick={logout}
                className="text-sm text-gray-400 hover:text-gray-200"
              >
                Logout ({username})
              </button>
            )}
          </nav>
        </div>
      </header>

      <main className="flex-1 max-w-6xl mx-auto w-full px-4 py-8">
        <Outlet />
      </main>

      <footer className="bg-brand-charcoal border-t border-brand-charcoal-light">
        <div className="max-w-6xl mx-auto px-4 py-4 text-center text-gray-400 text-sm">
          <p>Elite Sport Photos — Find yourself in event photos</p>
          <p className="mt-1 flex items-center justify-center gap-4">
            <Link to="/faq" className="text-brand-orange hover:text-brand-orange-dark">
              FAQ
            </Link>
            <span className="text-gray-600">|</span>
            <Link to="/privacy-policy" className="text-brand-orange hover:text-brand-orange-dark">
              Privacy Policy
            </Link>
          </p>
          <p className="text-xs text-gray-500 mt-1">v{__APP_VERSION__}</p>
        </div>
      </footer>
    </div>
  );
}
