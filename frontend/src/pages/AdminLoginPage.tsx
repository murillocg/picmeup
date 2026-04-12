import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from '../components/LoadingSpinner';

export default function AdminLoginPage() {
  const { authenticated, login } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (authenticated) {
      navigate('/', { replace: true });
    } else {
      login();
    }
  }, [authenticated, login, navigate]);

  return <LoadingSpinner />;
}
