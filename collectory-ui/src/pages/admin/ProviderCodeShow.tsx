import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface ProviderCode {
  id: number;
  code: string;
}

export default function ProviderCodeShow() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const codeId = Number(id);

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const { data: providerCode, isLoading, error } = useQuery({
    queryKey: ['providerCode', codeId],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderCode>(`/providerCode/${codeId}`);
      return data;
    },
    enabled: !isNaN(codeId),
  });

  async function handleDelete() {
    if (isNaN(codeId)) return;
    setDeleting(true);
    try {
      await apiClient.delete(`/providerCode/${codeId}`);
      navigate('/providerCode/list');
    } catch (err) {
      console.error('Delete failed:', err);
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
  }

  if (isLoading) {
    return (
      <div className="container mt-4 d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (error || !providerCode) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load provider code. ID: {id}</div>
      </div>
    );
  }

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item">
              <Link to="/providerCode/list">Provider Codes</Link>
            </li>
            <li className="breadcrumb-item active">{providerCode.code}</li>
          </ol>
        </nav>

        <div className="d-flex justify-content-between align-items-start mb-3">
          <h1>{providerCode.code}</h1>
          <Link to={`/providerCode/edit/${id}`} className="btn btn-primary">
            <i className="fa fa-edit me-1" /> Edit
          </Link>
        </div>

        <div className="row">
          <div className="col-md-8">
            <div className="card mb-3">
              <div className="card-header">
                <strong>Provider Code Details</strong>
              </div>
              <div className="card-body">
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Code</div>
                  <div className="col-sm-8">{providerCode.code || '-'}</div>
                </div>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card mb-3 border-danger">
              <div className="card-header bg-danger text-white">
                <strong>Danger Zone</strong>
              </div>
              <div className="card-body">
                {!showDeleteConfirm ? (
                  <button
                    className="btn btn-outline-danger w-100"
                    onClick={() => setShowDeleteConfirm(true)}
                  >
                    <i className="fa fa-trash me-1" /> Delete Provider Code
                  </button>
                ) : (
                  <div>
                    <p className="text-danger mb-2">
                      Are you sure you want to delete <strong>{providerCode.code}</strong>? This action
                      cannot be undone.
                    </p>
                    <div className="d-flex gap-2">
                      <button
                        className="btn btn-danger flex-fill"
                        onClick={handleDelete}
                        disabled={deleting}
                      >
                        {deleting ? 'Deleting...' : 'Yes, Delete'}
                      </button>
                      <button
                        className="btn btn-outline-secondary flex-fill"
                        onClick={() => setShowDeleteConfirm(false)}
                        disabled={deleting}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
