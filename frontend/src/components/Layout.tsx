import { Link, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Layout() {
  const { authenticated, username, login, logout } = useAuth();

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <header className="bg-white shadow-sm">
        <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
          <Link to="/" className="text-2xl font-bold text-indigo-600">
            Elite Sport Photos
          </Link>
          <nav className="flex items-center gap-4">
            <Link to="/" className="text-gray-600 hover:text-gray-900">
              Events
            </Link>
            {authenticated && (
              <>
                <Link to="/admin/orders" className="text-gray-600 hover:text-gray-900">
                  Orders
                </Link>
                <Link to="/admin/passes" className="text-gray-600 hover:text-gray-900">
                  Passes
                </Link>
                <Link
                  to="/events/new"
                  className="bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700"
                >
                  Create Event
                </Link>
              </>
            )}
            {authenticated ? (
              <button
                onClick={logout}
                className="text-sm text-gray-500 hover:text-gray-700"
              >
                Logout ({username})
              </button>
            ) : (
              <button
                onClick={login}
                className="text-sm text-indigo-600 hover:text-indigo-700 font-medium"
              >
                Admin Login
              </button>
            )}
          </nav>
        </div>
      </header>

      <main className="flex-1 max-w-6xl mx-auto w-full px-4 py-8">
        <Outlet />
      </main>

      <footer className="bg-white border-t">
        <div className="max-w-6xl mx-auto px-4 py-4 text-center text-gray-500 text-sm">
          <p>Elite Sport Photos — Find yourself in event photos</p>
          <p className="text-xs text-gray-400 mt-1">v{__APP_VERSION__}</p>
        </div>
      </footer>
    </div>
  );
}
