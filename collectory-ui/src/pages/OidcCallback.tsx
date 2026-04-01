import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';

/**
 * Landing page for the OIDC redirect_uri (/callback).
 * react-oidc-context processes the code/state params automatically via onSigninCallback.
 * This component just waits for auth to settle then redirects to home.
 */
export default function OidcCallback() {
  const { isLoading, error } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!isLoading) {
      navigate('/', { replace: true });
    }
  }, [isLoading, navigate]);

  if (error) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
        <div className="text-center">
          <div className="alert alert-danger">Login failed: {error.message}</div>
          <a href="/" className="btn btn-primary">Return to home</a>
        </div>
      </div>
    );
  }

  return (
    <div className="d-flex justify-content-center align-items-center" style={{ minHeight: '60vh' }}>
      <div className="spinner-border" role="status">
        <span className="visually-hidden">Completing login...</span>
      </div>
    </div>
  );
}
