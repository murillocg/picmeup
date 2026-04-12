import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { PayPalScriptProvider, PayPalButtons } from '@paypal/react-paypal-js';
import { createOrder, capturePayment, getPayPalClientId } from '../services/api';
import ErrorMessage from '../components/ErrorMessage';
import LoadingSpinner from '../components/LoadingSpinner';

export default function CheckoutPage() {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [emailConfirmed, setEmailConfirmed] = useState(false);
  const [orderId, setOrderId] = useState<string | null>(null);
  const [paypalOrderId, setPaypalOrderId] = useState<string | null>(null);
  const [paypalClientId, setPaypalClientId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const cart: string[] = slug
    ? JSON.parse(localStorage.getItem(`cart-${slug}`) || '[]')
    : [];

  useEffect(() => {
    getPayPalClientId()
      .then(setPaypalClientId)
      .catch(() => setError('Failed to load payment provider'))
      .finally(() => setLoading(false));
  }, []);

  if (cart.length === 0) {
    return (
      <div className="text-center py-12">
        <h1 className="text-2xl font-bold text-gray-900 mb-4">Your cart is empty</h1>
        <p className="text-gray-600">Go back to the event and select some photos first.</p>
      </div>
    );
  }

  const totalPrice = cart.length * 25;

  async function handleEmailSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const order = await createOrder(email, cart);
      setOrderId(order.id);
      setPaypalOrderId(order.paypalOrderId);
      setEmailConfirmed(true);
    } catch {
      setError('Failed to create order. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  function handlePaymentSuccess() {
    if (!orderId || !slug) return;

    const orders: string[] = JSON.parse(localStorage.getItem('orders') || '[]');
    if (!orders.includes(orderId)) {
      orders.push(orderId);
      localStorage.setItem('orders', JSON.stringify(orders));
    }

    localStorage.removeItem(`cart-${slug}`);
    navigate(`/orders/${orderId}`);
  }

  if (loading) return <LoadingSpinner />;

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

      {!emailConfirmed ? (
        <form onSubmit={handleEmailSubmit} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Email address
          </label>
          <p className="text-xs text-gray-500 mb-3">
            We'll use this to identify your order.
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
            className="w-full bg-indigo-600 text-white py-3 rounded-lg hover:bg-indigo-700 font-semibold"
          >
            Continue to payment
          </button>
        </form>
      ) : paypalClientId && paypalOrderId && orderId ? (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <p className="text-sm text-gray-600 mb-4">
            Order for <span className="font-medium">{email}</span>
          </p>
          <PayPalScriptProvider
            options={{
              clientId: paypalClientId,
              currency: 'AUD',
            }}
          >
            <PayPalButtons
              style={{ layout: 'vertical', shape: 'rect' }}
              createOrder={async () => {
                return paypalOrderId;
              }}
              onApprove={async () => {
                setError('');
                try {
                  await capturePayment(orderId);
                  handlePaymentSuccess();
                } catch {
                  setError('Payment capture failed. Please contact support.');
                }
              }}
              onError={() => {
                setError('Payment failed. Please try again.');
              }}
              onCancel={() => {
                setError('Payment cancelled.');
              }}
            />
          </PayPalScriptProvider>
        </div>
      ) : null}
    </div>
  );
}
