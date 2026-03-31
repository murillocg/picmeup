import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Layout from './components/Layout';
import EventListPage from './pages/EventListPage';
import EventDetailPage from './pages/EventDetailPage';
import CreateEventPage from './pages/CreateEventPage';
import UploadPage from './pages/UploadPage';
import CheckoutPage from './pages/CheckoutPage';
import OrderPage from './pages/OrderPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<EventListPage />} />
            <Route path="/events/new" element={<CreateEventPage />} />
            <Route path="/events/:slug" element={<EventDetailPage />} />
            <Route path="/events/:slug/upload" element={<UploadPage />} />
            <Route path="/events/:slug/checkout" element={<CheckoutPage />} />
            <Route path="/orders/:id" element={<OrderPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
