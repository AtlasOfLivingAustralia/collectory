import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface DatasetInfo {
  guid: string;
  repatriationCountry?: string;
  name?: string;
}

export default function GbifDatasetDownload() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { data: dataset, isLoading } = useQuery({
    queryKey: ['gbifDataset', id],
    queryFn: async () => {
      const { data } = await apiClient.get<DatasetInfo>(`/manage/gbifDataset/${id}`);
      return data;
    },
    enabled: !!id,
  });

  const { data: dataResource } = useQuery({
    queryKey: ['dataResource', id],
    queryFn: async () => {
      const { data } = await apiClient.get<{ name?: string }>(`/dataResource/${id}`);
      return data;
    },
    enabled: !!id,
  });

  async function handleReload() {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const { data } = await apiClient.post<{ loadGuid: string }>('/manage/loadDataset', {
        guid: id,
      });
      navigate(`/manage/externalLoad/status?loadGuid=${data.loadGuid}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to reload dataset.');
      setLoading(false);
    }
  }

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <h1>Reload Dataset{dataResource?.name ? ` - ${dataResource.name}` : ''}</h1>

        {error && <div className="alert alert-danger">{error}</div>}

        <div className="row">
          <div className="col-md-6">
            <div className="card card-body">
              {dataset?.name && (
                <div className="mb-3">
                  <label className="form-label fw-bold">Dataset Name</label>
                  <p>{dataset.name}</p>
                </div>
              )}

              <div className="mb-3">
                <label htmlFor="guid" className="form-label fw-bold">GUID</label>
                <input
                  type="text"
                  id="guid"
                  className="form-control"
                  value={id || ''}
                  readOnly
                />
              </div>

              <div className="mb-3">
                <label htmlFor="repatCountry" className="form-label fw-bold">Repatriation Country</label>
                <input
                  type="text"
                  id="repatCountry"
                  className="form-control"
                  value={dataset?.repatriationCountry || '-'}
                  readOnly
                />
              </div>

              <div className="d-flex gap-2">
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={loading}
                  onClick={handleReload}
                >
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-1" role="status" />
                      Reloading...
                    </>
                  ) : (
                    <>
                      <i className="fa fa-refresh me-1" />
                      Reload
                    </>
                  )}
                </button>
                <button
                  type="button"
                  className="btn btn-outline-dark"
                  onClick={() => navigate(-1)}
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
