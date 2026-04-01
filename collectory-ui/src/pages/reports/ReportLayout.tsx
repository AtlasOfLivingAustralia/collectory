import { Link } from 'react-router-dom';

interface ReportLayoutProps {
  title: string;
  children: React.ReactNode;
  isLoading?: boolean;
  error?: Error | null;
}

export default function ReportLayout({ title, children, isLoading, error }: ReportLayoutProps) {
  return (
    <div className="container-fluid mt-3">
      <div className="btn-toolbar mb-3">
        <ul className="btn-group list-unstyled mb-0">
          <li className="btn btn-outline-dark">
            <Link to="/" className="text-decoration-none text-reset">
              <i className="fa fa-home" />
            </Link>
          </li>
          <li className="btn btn-outline-dark">
            <Link to="/reports" className="text-decoration-none text-reset">
              <i className="fa fa-th-list me-1" />
              Reports
            </Link>
          </li>
        </ul>
      </div>

      <h1>{title}</h1>

      {isLoading && (
        <div className="d-flex justify-content-center p-5">
          <div className="spinner-border" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
        </div>
      )}

      {error && (
        <div className="alert alert-danger">
          <i className="fa fa-times-circle me-2" />
          Failed to load report: {error.message}
        </div>
      )}

      {!isLoading && !error && children}
    </div>
  );
}
