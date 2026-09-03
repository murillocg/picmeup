import { useSearchParams } from 'react-router-dom';
import usePageTitle from '../hooks/usePageTitle';

/**
 * Where a successful sign-in with no access lands. The single most useful thing this can
 * show is the address they actually used — signing in with a different Google account
 * than the invited one is the common cause, and without naming it the failure is a
 * mystery to both sides.
 */
export default function NoAccessPage() {
  usePageTitle('No access');
  const [params] = useSearchParams();
  const reason = params.get('reason');
  const email = params.get('email');

  const message = {
    DISABLED: 'Access for this address has been turned off.',
    UNVERIFIED_EMAIL: 'That sign-in did not provide a verified email address.',
    NOT_INVITED: 'This address has not been given access yet.',
  }[reason ?? 'NOT_INVITED'] ?? 'This address has not been given access yet.';

  return (
    <div className="max-w-md mx-auto text-center py-12">
      <h1 className="text-2xl font-bold text-gray-900 mb-3">You&rsquo;re signed in, but not set up</h1>

      <div className="bg-white border border-gray-200 rounded-lg p-6">
        <p className="text-gray-700">{message}</p>

        {email && (
          <p className="text-sm text-gray-500 mt-4">
            You signed in as <span className="font-mono text-gray-900">{email}</span>
          </p>
        )}

        <p className="text-sm text-gray-500 mt-4">
          {reason === 'DISABLED'
            ? 'Ask the team if this was expected.'
            : 'If you have more than one account, you may have used a different one than the address the team invited.'}
        </p>
      </div>

      <a
        href="/api/auth/authorize/cognito"
        className="inline-block mt-6 text-brand-orange hover:text-brand-orange-dark underline"
      >
        Try a different account
      </a>
    </div>
  );
}
