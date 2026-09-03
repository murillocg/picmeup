import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import EventListPage from './pages/EventListPage';
import EventDetailPage from './pages/EventDetailPage';
import CreateEventPage from './pages/CreateEventPage';
import UploadPage from './pages/UploadPage';
import CheckoutPage from './pages/CheckoutPage';
import OrderPage from './pages/OrderPage';
import NoAccessPage from './pages/NoAccessPage';
import NotFoundPage from './pages/NotFoundPage';
import SignInRedirect from './components/SignInRedirect';
import AdminOrdersPage from './pages/AdminOrdersPage';
import AdminSearchStatsPage from './pages/AdminSearchStatsPage';
import AdminUsagePage from './pages/AdminUsagePage';
import AdminUsersPage from './pages/AdminUsersPage';
import PhotographerEventsPage from './pages/PhotographerEventsPage';
import FaqPage from './pages/FaqPage';
import PrivacyPolicyPage from './pages/PrivacyPolicyPage';
// import PassCheckoutPage from './pages/PassCheckoutPage';
// import AdminPassesPage from './pages/AdminPassesPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            {/* Public */}
            <Route path="/" element={<EventListPage />} />
            <Route path="/events/:slug" element={<EventDetailPage />} />
            <Route path="/events/:slug/checkout" element={<CheckoutPage />} />
            {/* <Route path="/events/:slug/pass" element={<PassCheckoutPage />} /> */}
            <Route path="/no-access" element={<NoAccessPage />} />
            {/* Addresses people type or have bookmarked when they mean "sign in". */}
            <Route path="/admin" element={<SignInRedirect />} />
            <Route path="/login" element={<SignInRedirect />} />
            <Route path="/orders/:id" element={<OrderPage />} />
            <Route path="/faq" element={<FaqPage />} />
            <Route path="/privacy-policy" element={<PrivacyPolicyPage />} />

            {/* Any signed-in user. Photographers upload to events they are assigned to;
                the assignment itself is enforced by the backend, not by the route. */}
            <Route element={<ProtectedRoute />}>
              <Route path="/events/:slug/upload" element={<UploadPage />} />
              <Route path="/my-events" element={<PhotographerEventsPage />} />
            </Route>

            {/* Admin only */}
            <Route element={<ProtectedRoute requireAdmin />}>
              <Route path="/events/new" element={<CreateEventPage />} />
              <Route path="/admin/orders" element={<AdminOrdersPage />} />
              <Route path="/admin/stats" element={<AdminSearchStatsPage />} />
              <Route path="/admin/usage" element={<AdminUsagePage />} />
              <Route path="/admin/users" element={<AdminUsersPage />} />
              {/* <Route path="/admin/passes" element={<AdminPassesPage />} /> */}
            </Route>

            {/* Without this an unmatched path renders nothing at all — a white screen. */}
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
