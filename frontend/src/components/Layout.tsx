import { Link, Outlet } from 'react-router-dom';

export default function Layout() {
  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <header className="bg-white shadow-sm">
        <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
          <Link to="/" className="text-2xl font-bold text-indigo-600">
            PicMeUp
          </Link>
          <nav className="flex gap-4">
            <Link to="/" className="text-gray-600 hover:text-gray-900">
              Events
            </Link>
            <Link
              to="/events/new"
              className="bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700"
            >
              Create Event
            </Link>
          </nav>
        </div>
      </header>

      <main className="flex-1 max-w-6xl mx-auto w-full px-4 py-8">
        <Outlet />
      </main>

      <footer className="bg-white border-t">
        <div className="max-w-6xl mx-auto px-4 py-4 text-center text-gray-500 text-sm">
          PicMeUp — Find yourself in event photos
        </div>
      </footer>
    </div>
  );
}
