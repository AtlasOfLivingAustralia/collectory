import { useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface RepatriationResource {
  guid: string;
  name: string;
  doi?: string;
  status: string;
  existingUid?: string;
  existingName?: string;
  sourceUpdated?: string;
  recordCount?: number;
}

interface ResourceSelection {
  guid: string;
  name: string;
  addResource: boolean;
  updateMetadata: boolean;
  updateConnection: boolean;
}

export default function RepatriateReview() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const country = searchParams.get('country') || '';
  const dataProviderUid = searchParams.get('dataProviderUid') || '';
  const maxDatasets = searchParams.get('maxDatasets') || '25';
  const minRecordCount = searchParams.get('minRecordCount') || '10000';
  const maxRecordCount = searchParams.get('maxRecordCount') || '1000000';

  const { data: resources = [], isLoading } = useQuery({
    queryKey: ['repatriate', 'search', country, dataProviderUid, maxDatasets, minRecordCount, maxRecordCount],
    queryFn: async () => {
      const { data } = await apiClient.post<RepatriationResource[]>('/manage/searchForResources', {
        country,
        dataProviderUid,
        maxDatasets: Number(maxDatasets),
        minRecordCount: Number(minRecordCount),
        maxRecordCount: Number(maxRecordCount),
        repatriate: true,
      });
      return data;
    },
  });

  const [selections, setSelections] = useState<Record<string, ResourceSelection>>({});

  // Initialize selections when resources load
  if (resources.length > 0 && Object.keys(selections).length === 0) {
    const init: Record<string, ResourceSelection> = {};
    resources.forEach((r) => {
      init[r.guid] = {
        guid: r.guid,
        name: r.name,
        addResource: !r.existingUid,
        updateMetadata: !!r.existingUid,
        updateConnection: !!r.existingUid,
      };
    });
    if (Object.keys(init).length > 0) {
      setSelections(init);
    }
  }

  function updateSelection(guid: string, field: keyof ResourceSelection, value: string | boolean) {
    setSelections((prev) => {
      const existing = prev[guid];
      if (!existing) return prev;
      return {
        ...prev,
        [guid]: { ...existing, [field]: value } as ResourceSelection,
      };
    });
  }

  function selectAll(field: 'addResource' | 'updateMetadata' | 'updateConnection') {
    setSelections((prev) => {
      const next: Record<string, ResourceSelection> = {};
      Object.entries(prev).forEach(([guid, sel]) => {
        next[guid] = { ...sel, [field]: true } as ResourceSelection;
      });
      return next;
    });
  }

  function deselectAll(field: 'addResource' | 'updateMetadata' | 'updateConnection') {
    setSelections((prev) => {
      const next: Record<string, ResourceSelection> = {};
      Object.entries(prev).forEach(([guid, sel]) => {
        next[guid] = { ...sel, [field]: false } as ResourceSelection;
      });
      return next;
    });
  }

  async function handleLoad() {
    setLoading(true);
    setError(null);
    try {
      const selected = Object.values(selections).filter(
        (s) => s.addResource || s.updateMetadata || s.updateConnection,
      );
      const { data } = await apiClient.post<{ loadGuid: string }>('/manage/updateFromExternalSources', {
        resources: selected,
        country,
        dataProviderUid,
        repatriate: true,
      });
      navigate(`/manage/externalLoad/status?loadGuid=${data.loadGuid}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to start load.');
      setLoading(false);
    }
  }

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><Link to="/manage/list">Manage</Link></li>
            <li className="breadcrumb-item"><Link to="/manage/repatriate">Repatriate</Link></li>
            <li className="breadcrumb-item active">Review</li>
          </ol>
        </nav>
        <h1>Review Repatriation Datasets</h1>

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Searching GBIF...</span>
            </div>
            <span className="ms-2">Searching GBIF for repatriation candidates...</span>
          </div>
        )}

        {error && <div className="alert alert-danger">{error}</div>}

        {!isLoading && resources.length === 0 && (
          <div className="alert alert-info">
            No repatriation candidates found matching your criteria.{' '}
            <button className="btn btn-link p-0" onClick={() => navigate('/manage/repatriate')}>
              Try a different search
            </button>
          </div>
        )}

        {!isLoading && resources.length > 0 && (
          <>
            <p className="text-muted mb-3">
              Found {resources.length} candidate dataset(s). Select which to load and click <strong>Load</strong>.
            </p>

            <div className="table-responsive">
              <table className="table table-striped table-hover">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>DOI</th>
                    <th>Status</th>
                    <th>Existing Resource</th>
                    <th>Source Updated</th>
                    <th>Record Count</th>
                    <th>
                      Add Resource
                      <div className="btn-group btn-group-sm ms-1">
                        <button className="btn btn-outline-secondary btn-sm" onClick={() => selectAll('addResource')}>All</button>
                        <button className="btn btn-outline-secondary btn-sm" onClick={() => deselectAll('addResource')}>None</button>
                      </div>
                    </th>
                    <th>
                      Update Metadata
                      <div className="btn-group btn-group-sm ms-1">
                        <button className="btn btn-outline-secondary btn-sm" onClick={() => selectAll('updateMetadata')}>All</button>
                        <button className="btn btn-outline-secondary btn-sm" onClick={() => deselectAll('updateMetadata')}>None</button>
                      </div>
                    </th>
                    <th>
                      Update Connection
                      <div className="btn-group btn-group-sm ms-1">
                        <button className="btn btn-outline-secondary btn-sm" onClick={() => selectAll('updateConnection')}>All</button>
                        <button className="btn btn-outline-secondary btn-sm" onClick={() => deselectAll('updateConnection')}>None</button>
                      </div>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {resources.map((r) => {
                    const sel = selections[r.guid];
                    if (!sel) return null;
                    return (
                      <tr key={r.guid}>
                        <td>
                          <input
                            type="text"
                            className="form-control form-control-sm"
                            value={sel.name}
                            onChange={(e) => updateSelection(r.guid, 'name', e.target.value)}
                          />
                        </td>
                        <td>
                          {r.doi ? (
                            <a href={`https://doi.org/${r.doi}`} target="_blank" rel="noopener noreferrer">
                              {r.doi}
                            </a>
                          ) : (
                            '-'
                          )}
                        </td>
                        <td>{r.status}</td>
                        <td>
                          {r.existingUid ? (
                            <a href={`/dataResource/show/${r.existingUid}`}>{r.existingName || r.existingUid}</a>
                          ) : (
                            <span className="text-muted">New</span>
                          )}
                        </td>
                        <td>{r.sourceUpdated || '-'}</td>
                        <td>{r.recordCount?.toLocaleString() ?? '-'}</td>
                        <td className="text-center">
                          <input
                            type="checkbox"
                            className="form-check-input"
                            checked={sel.addResource}
                            onChange={(e) => updateSelection(r.guid, 'addResource', e.target.checked)}
                          />
                        </td>
                        <td className="text-center">
                          <input
                            type="checkbox"
                            className="form-check-input"
                            checked={sel.updateMetadata}
                            onChange={(e) => updateSelection(r.guid, 'updateMetadata', e.target.checked)}
                          />
                        </td>
                        <td className="text-center">
                          <input
                            type="checkbox"
                            className="form-check-input"
                            checked={sel.updateConnection}
                            onChange={(e) => updateSelection(r.guid, 'updateConnection', e.target.checked)}
                          />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="d-flex gap-2 mt-3">
              <button
                type="button"
                className="btn btn-primary"
                disabled={loading}
                onClick={handleLoad}
              >
                {loading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-1" role="status" />
                    Loading...
                  </>
                ) : (
                  <>
                    <i className="fa fa-download me-1" />
                    Load
                  </>
                )}
              </button>
              <button
                type="button"
                className="btn btn-outline-dark"
                onClick={() => navigate('/manage/repatriate')}
              >
                Back
              </button>
            </div>
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
