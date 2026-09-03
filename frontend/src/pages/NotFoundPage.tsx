import { Link } from 'react-router-dom';
import usePageTitle from '../hooks/usePageTitle';

export default function NotFoundPage() {
  usePageTitle('Page not found');

  return (
    <div className="max-w-md mx-auto text-center py-16">
      <p className="text-5xl font-bold text-brand-orange mb-3">404</p>
      <h1 className="text-2xl font-bold text-gray-900 mb-2">We couldn&rsquo;t find that page</h1>
      <p className="text-gray-600 mb-8">
        The link may be out of date, or the address may have a typo in it.
      </p>

      <div className="flex flex-col sm:flex-row gap-3 justify-center">
        <Link
          to="/"
          className="bg-brand-orange text-white px-6 py-3 rounded-lg hover:bg-brand-orange-dark font-medium"
        >
          Browse events
        </Link>
        <Link
          to="/faq"
          className="border border-gray-300 text-gray-700 px-6 py-3 rounded-lg hover:bg-gray-50"
        >
          Read the FAQ
        </Link>
      </div>
    </div>
  );
}
