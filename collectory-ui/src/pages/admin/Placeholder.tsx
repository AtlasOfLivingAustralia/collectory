import { useLocation, Link } from 'react-router-dom';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

export default function Placeholder() {
  const location = useLocation();

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <div className="card">
          <div className="card-body text-center py-5">
            <h2 className="text-muted mb-3">Page Under Construction</h2>
            <p className="text-muted">
              <code>{location.pathname}</code>
            </p>
            <Link to="/" className="btn btn-outline-dark mt-3">
              <i className="fa fa-home me-1" /> Back to Home
            </Link>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
