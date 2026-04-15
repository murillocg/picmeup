import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { PayPalScriptProvider, PayPalButtons } from '@paypal/react-paypal-js';
import { createPass, capturePassPayment, getPayPalClientId, getPassPrice } from '../services/api';
import ErrorMessage from '../components/ErrorMessage';
import LoadingSpinner from '../components/LoadingSpinner';

export default function PassCheckoutPage() {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [emailConfirmed, setEmailConfirmed] = useState(false);
  const [passId, setPassId] = useState<string | null>(null);
  const [paypalOrderId, setPaypalOrderId] = useState<string | null>(null);
  const [paypalClientId, setPaypalClientId] = useState<string | null>(null);
  const [passPrice, setPassPrice] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!slug) return;
    Promise.all([getPayPalClientId(), getPassPrice(slug)])
      .then(([clientId, price]) => {
        setPaypalClientId(clientId);
        setPassPrice(price);
      })
      .catch(() => setError('Failed to load payment provider'))
      .finally(() => setLoading(false));
  }, [slug]);

  async function handleEmailSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!slug) return;
    setError('');
    setLoading(true);
    try {
      const pass = await createPass(slug, email);
      setPassId(pass.id);
      setPaypalOrderId(pass.paypalOrderId);
      setEmailConfirmed(true);
    } catch {
      setError('Failed to create pass. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <LoadingSpinner />;

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Buy Photo Pass</h1>

      {error && <ErrorMessage message={error} />}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Photo Pass</h2>
        <p className="text-gray-600 mb-4">
          Get access to all your photos from this event. After the event, search with a selfie
          and download every photo you appear in.
        </p>
        <div className="border-t pt-2 mt-2 flex justify-between font-semibold text-gray-900">
          <span>Total</span>
          <span>${passPrice?.toFixed(2)} AUD</span>
        </div>
      </div>

      {!emailConfirmed ? (
        <form onSubmit={handleEmailSubmit} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Email address
          </label>
          <p className="text-xs text-gray-500 mb-3">
            You'll use this email to redeem your pass and download your photos.
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
      ) : paypalClientId && paypalOrderId && passId ? (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <p className="text-sm text-gray-600 mb-4">
            Pass for <span className="font-medium">{email}</span>
          </p>
          <PayPalScriptProvider
            options={{
              clientId: paypalClientId,
              currency: 'AUD',
              locale: 'en_AU',
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
                  await capturePassPayment(slug!, passId);
                  navigate(`/events/${slug}?pass=purchased`);
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
