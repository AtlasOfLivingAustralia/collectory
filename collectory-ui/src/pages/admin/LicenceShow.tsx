import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import { useAuth } from '../../auth/useAuth';

interface Licence {
  id: number;
  name: string;
  acronym: string;
  licenceVersion: string;
  url: string;
  imageUrl: string;
}

export default function LicenceShow() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const licenceId = Number(id);

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const { data: licence, isLoading, error } = useQuery({
    queryKey: ['licence', licenceId],
    queryFn: async () => {
      const { data } = await apiClient.get<Licence>(`/licence/${licenceId}`);
      return data;
    },
    enabled: !isNaN(licenceId),
  });

  async function handleDelete() {
    if (isNaN(licenceId)) return;
    setDeleting(true);
    try {
      await apiClient.delete(`/licence/${licenceId}`);
      navigate('/licence/list');
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

  if (error || !licence) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load licence. ID: {id}</div>
      </div>
    );
  }

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item">
              <Link to="/licence/list">Licences</Link>
            </li>
            <li className="breadcrumb-item active">{licence.name}</li>
          </ol>
        </nav>

        <div className="d-flex justify-content-between align-items-start mb-3">
          <h1>{licence.name}</h1>
          <Link to={`/licence/edit/${id}`} className="btn btn-primary">
            <i className="fa fa-edit me-1" /> Edit
          </Link>
        </div>

        <div className="row">
          <div className="col-md-8">
            <div className="card mb-3">
              <div className="card-header">
                <strong>Licence Details</strong>
              </div>
              <div className="card-body">
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Name</div>
                  <div className="col-sm-8">{licence.name || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Acronym</div>
                  <div className="col-sm-8">{licence.acronym || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Version</div>
                  <div className="col-sm-8">{licence.licenceVersion || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">URL</div>
                  <div className="col-sm-8">
                    {licence.url ? (
                      <a href={licence.url} target="_blank" rel="noreferrer">{licence.url}</a>
                    ) : '-'}
                  </div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Image</div>
                  <div className="col-sm-8">
                    {licence.imageUrl ? (
                      <img src={licence.imageUrl} alt={licence.name} style={{ maxHeight: '60px' }} />
                    ) : '-'}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {isAdmin && (
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
                      <i className="fa fa-trash me-1" /> Delete Licence
                    </button>
                  ) : (
                    <div>
                      <p className="text-danger mb-2">
                        Are you sure you want to delete <strong>{licence.name}</strong>? This action
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
          )}
        </div>
      </div>
    </ProtectedRoute>
  );
}
