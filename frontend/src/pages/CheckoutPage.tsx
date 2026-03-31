import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { createOrder } from '../services/api';
import ErrorMessage from '../components/ErrorMessage';

export default function CheckoutPage() {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const cart: string[] = slug
    ? JSON.parse(localStorage.getItem(`cart-${slug}`) || '[]')
    : [];

  if (cart.length === 0) {
    return (
      <div className="text-center py-12">
        <h1 className="text-2xl font-bold text-gray-900 mb-4">Your cart is empty</h1>
        <p className="text-gray-600">Go back to the event and select some photos first.</p>
      </div>
    );
  }

  const totalPrice = cart.length * 10;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!slug) return;
    setSubmitting(true);
    setError('');
    try {
      const order = await createOrder(email, cart);

      // Save order ID for download access
      const orders: string[] = JSON.parse(localStorage.getItem('orders') || '[]');
      if (!orders.includes(order.id)) {
        orders.push(order.id);
        localStorage.setItem('orders', JSON.stringify(orders));
      }

      // Clear cart
      localStorage.removeItem(`cart-${slug}`);

      navigate(`/orders/${order.id}`);
    } catch {
      setError('Failed to create order. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Checkout</h1>

      {error && <ErrorMessage message={error} />}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Order Summary</h2>
        <div className="flex justify-between text-gray-600 mb-2">
          <span>{cart.length} photo{cart.length !== 1 ? 's' : ''}</span>
          <span>${totalPrice}.00 AUD</span>
        </div>
        <div className="border-t pt-2 mt-2 flex justify-between font-semibold text-gray-900">
          <span>Total</span>
          <span>${totalPrice}.00 AUD</span>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          Email address
        </label>
        <p className="text-xs text-gray-500 mb-3">
          We'll use this to identify your order. Your download links will be saved in this browser.
        </p>
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
          className="w-full border border-gray-300 rounded-lg px-4 py-2 mb-4 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-green-600 text-white py-3 rounded-lg hover:bg-green-700 disabled:opacity-50 font-semibold"
        >
          {submitting ? 'Processing...' : `Pay $${totalPrice}.00 AUD`}
        </button>
      </form>
    </div>
  );
}
