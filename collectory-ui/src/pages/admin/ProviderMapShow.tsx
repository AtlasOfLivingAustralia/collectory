import { useState } from 'react';
import { useParams, Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface ProviderMap {
  id: number;
  institution?: { uid: string; name: string };
  collection?: { uid: string; name: string };
  exact: boolean;
  matchAnyCollectionCode: boolean;
  institutionCodes: string;
  collectionCodes: string;
  warning: string;
}

export default function ProviderMapShow() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const mapId = Number(id);

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const { data: providerMap, isLoading, error } = useQuery({
    queryKey: ['providerMap', mapId],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderMap>(`/providerMap/${mapId}`);
      return data;
    },
    enabled: !isNaN(mapId),
  });

  async function handleDelete() {
    if (isNaN(mapId)) return;
    setDeleting(true);
    try {
      await apiClient.delete(`/providerMap/${mapId}`);
      const returnTo = searchParams.get('returnTo');
      navigate(returnTo || '/providerMap/list');
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

  if (error || !providerMap) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load provider map. ID: {id}</div>
      </div>
    );
  }

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[{ url: '/providerMap/list', label: 'Provider Maps' }]}
          current={`Provider Map #${providerMap.id}`}
        />

        <div className="d-flex justify-content-between align-items-start mb-3">
          <h1>Provider Map #{providerMap.id}</h1>
          <Link to={`/providerMap/edit/${id}`} className="btn btn-primary">
            <i className="fa fa-edit me-1" /> Edit
          </Link>
        </div>

        <div className="row">
          <div className="col-md-8">
            <div className="card mb-3">
              <div className="card-header">
                <strong>Provider Map Details</strong>
              </div>
              <div className="card-body">
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">ID</div>
                  <div className="col-sm-8">{providerMap.id}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Institution</div>
                  <div className="col-sm-8">
                    {providerMap.institution ? (
                      <Link to={`/institution/show/${providerMap.institution.uid}`}>
                        {providerMap.institution.name}
                      </Link>
                    ) : '-'}
                  </div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Collection</div>
                  <div className="col-sm-8">
                    {providerMap.collection ? (
                      <Link to={`/collection/show/${providerMap.collection.uid}`}>
                        {providerMap.collection.name}
                      </Link>
                    ) : '-'}
                  </div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Exact</div>
                  <div className="col-sm-8">
                    {providerMap.exact ? (
                      <span className="badge bg-success">Yes</span>
                    ) : (
                      <span className="badge bg-secondary">No</span>
                    )}
                  </div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Match Any Collection Code</div>
                  <div className="col-sm-8">
                    {providerMap.matchAnyCollectionCode ? (
                      <span className="badge bg-success">Yes</span>
                    ) : (
                      <span className="badge bg-secondary">No</span>
                    )}
                  </div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Institution Codes</div>
                  <div className="col-sm-8">{providerMap.institutionCodes || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Collection Codes</div>
                  <div className="col-sm-8">{providerMap.collectionCodes || '-'}</div>
                </div>
                {providerMap.warning && (
                  <div className="row mb-1">
                    <div className="col-sm-4 text-muted">Warning</div>
                    <div className="col-sm-8">{providerMap.warning}</div>
                  </div>
                )}
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
                    <i className="fa fa-trash me-1" /> Delete Provider Map
                  </button>
                ) : (
                  <div>
                    <p className="text-danger mb-2">
                      Are you sure you want to delete this provider map? This action
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
