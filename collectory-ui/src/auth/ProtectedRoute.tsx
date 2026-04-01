import type { ReactNode } from 'react';
import { useAuth } from './useAuth';

interface Props {
  children: ReactNode;
  requiredRole?: 'ROLE_ADMIN' | 'ROLE_EDITOR';
}

export function ProtectedRoute({ children, requiredRole = 'ROLE_EDITOR' }: Props) {
  const { isAuthenticated, isLoading, isAdmin, isEditor, login } = useAuth();

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    login();
    return null;
  }

  const hasRole = requiredRole === 'ROLE_ADMIN' ? isAdmin : isEditor;
  if (!hasRole) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">
          You do not have permission to access this page.
          Required role: {requiredRole}
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
