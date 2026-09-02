import { useEffect, useState } from 'react';
import { inviteUser, listUsers, setUserEnabled, setUserRole } from '../services/api';
import type { ManagedUser } from '../services/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import usePageTitle from '../hooks/usePageTitle';
import { getErrorMessage } from '../utils/errors';

export default function AdminUsersPage() {
  usePageTitle('People');
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [role, setRole] = useState<'ADMIN' | 'PHOTOGRAPHER'>('PHOTOGRAPHER');
  const [inviting, setInviting] = useState(false);

  useEffect(() => {
    listUsers()
      .then(setUsers)
      .catch(() => setError('Could not load people.'))
      .finally(() => setLoading(false));
  }, []);

  function replace(updated: ManagedUser) {
    setUsers((prev) => prev.map((user) => (user.id === updated.id ? updated : user)));
  }

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setInviting(true);
    try {
      const created = await inviteUser(email.trim(), name.trim(), role);
      setUsers((prev) => [created, ...prev]);
      setEmail('');
      setName('');
    } catch (err) {
      setError(getErrorMessage(err, 'Could not invite that address.'));
    } finally {
      setInviting(false);
    }
  }

  if (loading) return <LoadingSpinner />;

  return (
    <div className="max-w-4xl mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-2">People</h1>
      <p className="text-gray-600 mb-6">
        Inviting someone records their address — no email is sent. Tell them to sign in with
        that address and they will have access the first time they do.
      </p>

      {error && <div className="mb-4"><ErrorMessage message={error} /></div>}

      <form
        onSubmit={handleInvite}
        className="bg-white border border-gray-200 rounded-lg p-4 mb-8 flex flex-wrap gap-3 items-end"
      >
        <div className="flex-1 min-w-[200px]">
          <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="invite-email">
            Email address
          </label>
          <input
            id="invite-email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="name@example.com"
            className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-brand-orange"
          />
        </div>
        <div className="flex-1 min-w-[150px]">
          <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="invite-name">
            Name
          </label>
          <input
            id="invite-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-brand-orange"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="invite-role">
            Role
          </label>
          <select
            id="invite-role"
            value={role}
            onChange={(e) => setRole(e.target.value as 'ADMIN' | 'PHOTOGRAPHER')}
            className="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-brand-orange"
          >
            <option value="PHOTOGRAPHER">Photographer</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
        <button
          type="submit"
          disabled={inviting}
          className="bg-brand-orange text-white px-5 py-2 rounded-lg hover:bg-brand-orange-dark disabled:opacity-50"
        >
          {inviting ? 'Inviting...' : 'Invite'}
        </button>
      </form>

      <div className="bg-white border border-gray-200 rounded-lg overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-left">
            <tr>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Role</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Events</th>
              <th className="px-4 py-3 font-medium">Last sign-in</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id} className="border-t border-gray-100">
                <td className="px-4 py-3 text-gray-900">{user.email}</td>
                <td className="px-4 py-3 text-gray-600">{user.name || '—'}</td>
                <td className="px-4 py-3">
                  <select
                    value={user.role}
                    onChange={async (e) => {
                      const updated = await setUserRole(
                        user.id,
                        e.target.value as 'ADMIN' | 'PHOTOGRAPHER',
                      );
                      replace(updated);
                    }}
                    className="border border-gray-300 rounded px-2 py-1 text-sm"
                  >
                    <option value="PHOTOGRAPHER">Photographer</option>
                    <option value="ADMIN">Admin</option>
                  </select>
                </td>
                <td className="px-4 py-3">
                  <StatusBadge status={user.status} />
                </td>
                <td className="px-4 py-3 text-gray-600 tabular-nums">{user.assignedEvents}</td>
                <td className="px-4 py-3 text-gray-500">
                  {user.lastLoginAt
                    ? new Date(user.lastLoginAt).toLocaleDateString('en-AU')
                    : 'Never'}
                </td>
                <td className="px-4 py-3 text-right">
                  <button
                    onClick={async () => {
                      const updated = await setUserEnabled(user.id, user.status === 'DISABLED');
                      replace(updated);
                    }}
                    className={
                      user.status === 'DISABLED'
                        ? 'text-brand-orange hover:text-brand-orange-dark'
                        : 'text-red-600 hover:text-red-700'
                    }
                  >
                    {user.status === 'DISABLED' ? 'Enable' : 'Disable'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: ManagedUser['status'] }) {
  const styles = {
    ACTIVE: 'bg-green-50 text-green-700 border-green-200',
    INVITED: 'bg-amber-50 text-amber-700 border-amber-200',
    DISABLED: 'bg-gray-100 text-gray-500 border-gray-200',
  }[status];

  const label = { ACTIVE: 'Active', INVITED: 'Not signed in yet', DISABLED: 'Disabled' }[status];

  return (
    <span className={`inline-block border rounded-full px-2 py-0.5 text-xs font-medium ${styles}`}>
      {label}
    </span>
  );
}
