import { useRef, useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Modal } from 'bootstrap';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface ExternalResource {
  guid: string;
  name: string;
  status: string;
  existingUid?: string;
  existingName?: string;
  sourceUpdated?: string;
  recordCount?: number;
}

interface DataResourceSummary {
  uid: string;
  name: string;
}

interface ResourceSelection {
  guid: string;
  name: string;
  uid?: string;
  addResource: boolean;
  updateMetadata: boolean;
  updateConnection: boolean;
}

export default function ExternalLoadReview() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const country = searchParams.get('country') || '';
  const name = searchParams.get('name') || '';
  const dataProviderUid = searchParams.get('dataProviderUid') || '';
  const maxDatasets = searchParams.get('maxDatasets') || '10';

  const { data: resources = [], isLoading } = useQuery({
    queryKey: ['externalLoad', 'search', country, name, dataProviderUid, maxDatasets],
    queryFn: async () => {
      const { data } = await apiClient.post<ExternalResource[]>('/manage/searchForResources', {
        country,
        name,
        dataProviderUid,
        maxDatasets: Number(maxDatasets),
      });
      return data;
    },
  });

  const [selections, setSelections] = useState<Record<string, ResourceSelection>>({});
  const [uidOverrides, setUidOverrides] = useState<Record<string, string>>({});
  const [modalGuid, setModalGuid] = useState<string | null>(null);
  const [modalSearch, setModalSearch] = useState('');
  const modalRef = useRef<HTMLDivElement>(null);

  // Fetch all data resources for the mapping modal
  const { data: allDataResources = [] } = useQuery({
    queryKey: ['dataResources', 'all'],
    queryFn: async () => {
      const { data } = await apiClient.get<DataResourceSummary[]>('/dataResource');
      return data;
    },
  });

  // Initialize selections when resources load
  const initSelections = (res: ExternalResource[]) => {
    const sel: Record<string, ResourceSelection> = {};
    res.forEach((r) => {
      if (!selections[r.guid]) {
        sel[r.guid] = {
          guid: r.guid,
          name: r.name,
          addResource: !r.existingUid,
          updateMetadata: !!r.existingUid,
          updateConnection: !!r.existingUid,
        };
      } else {
        sel[r.guid] = selections[r.guid]!;
      }
    });
    return sel;
  };

  // Lazily initialize
  if (resources.length > 0 && Object.keys(selections).length === 0) {
    const init = initSelections(resources);
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

  function openMappingModal(guid: string) {
    setModalGuid(guid);
    setModalSearch('');
    // Show the Bootstrap modal
    requestAnimationFrame(() => {
      if (modalRef.current) {
        const bsModal = Modal.getOrCreateInstance(modalRef.current);
        bsModal.show();
      }
    });
  }

  function closeMappingModal() {
    if (modalRef.current) {
      const bsModal = Modal.getInstance(modalRef.current);
      bsModal?.hide();
    }
    setModalGuid(null);
  }

  function confirmMapping(uid: string, _uidName: string) {
    if (!modalGuid) return;
    setUidOverrides((prev) => ({ ...prev, [modalGuid]: uid }));
    // Also update the selection to carry the uid
    setSelections((prev) => {
      const existing = prev[modalGuid];
      if (!existing) return prev;
      return { ...prev, [modalGuid]: { ...existing, uid } };
    });
    closeMappingModal();
  }

  const filteredDataResources = allDataResources.filter((dr) => {
    if (!modalSearch) return true;
    const term = modalSearch.toLowerCase();
    return dr.uid.toLowerCase().includes(term) || dr.name.toLowerCase().includes(term);
  });

  async function handleLoad() {
    setLoading(true);
    setError(null);
    try {
      const selected = Object.values(selections)
        .filter((s) => s.addResource || s.updateMetadata || s.updateConnection)
        .map((s) => ({
          ...s,
          uid: uidOverrides[s.guid] || s.uid,
        }));
      const { data } = await apiClient.post<{ loadGuid: string }>('/manage/updateFromExternalSources', {
        resources: selected,
        country,
        dataProviderUid,
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
            <li className="breadcrumb-item"><Link to="/manage/externalLoad">External Data Load</Link></li>
            <li className="breadcrumb-item active">Review</li>
          </ol>
        </nav>
        <h1>Review External Datasets</h1>

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Searching GBIF...</span>
            </div>
            <span className="ms-2">Searching GBIF for matching datasets...</span>
          </div>
        )}

        {error && <div className="alert alert-danger">{error}</div>}

        {!isLoading && resources.length === 0 && (
          <div className="alert alert-info">
            No datasets found matching your criteria.{' '}
            <button className="btn btn-link p-0" onClick={() => navigate('/manage/externalLoad')}>
              Try a different search
            </button>
          </div>
        )}

        {!isLoading && resources.length > 0 && (
          <>
            <p className="text-muted mb-3">
              Found {resources.length} dataset(s). Select which to load and click <strong>Load</strong>.
            </p>

            <div className="table-responsive">
              <table className="table table-striped table-hover">
                <thead>
                  <tr>
                    <th>Name</th>
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
                        <td>{r.status}</td>
                        <td>
                          {(() => {
                            const overrideUid = uidOverrides[r.guid];
                            const displayUid = overrideUid || r.existingUid;
                            const displayName = overrideUid
                              ? allDataResources.find((dr) => dr.uid === overrideUid)?.name || overrideUid
                              : r.existingName || r.existingUid;
                            return displayUid ? (
                              <span>
                                <a href={`/dataResource/show/${displayUid}`}>{displayName}</a>
                                {overrideUid && <span className="badge bg-info ms-1">changed</span>}
                              </span>
                            ) : (
                              <span className="text-muted">New</span>
                            );
                          })()}
                          <button
                            type="button"
                            className="btn btn-outline-secondary btn-sm ms-1 py-0 px-1"
                            title="Map to existing data resource"
                            onClick={() => openMappingModal(r.guid)}
                          >
                            &hellip;
                          </button>
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
                onClick={() => navigate('/manage/externalLoad')}
              >
                Back
              </button>
            </div>
          </>
        )}
        {/* Resource mapping modal */}
        <div className="modal fade" ref={modalRef} tabIndex={-1} aria-hidden="true">
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Select Existing Data Resource</h5>
                <button type="button" className="btn-close" onClick={closeMappingModal} />
              </div>
              <div className="modal-body">
                <input
                  type="text"
                  className="form-control mb-3"
                  placeholder="Search by UID or name..."
                  value={modalSearch}
                  onChange={(e) => setModalSearch(e.target.value)}
                />
                <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
                  <table className="table table-striped table-hover mb-0">
                    <thead className="sticky-top bg-white">
                      <tr>
                        <th>UID</th>
                        <th>Name</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredDataResources.map((dr) => (
                        <tr
                          key={dr.uid}
                          role="button"
                          className={
                            modalGuid && uidOverrides[modalGuid] === dr.uid
                              ? 'table-primary'
                              : ''
                          }
                          onClick={() => confirmMapping(dr.uid, dr.name)}
                          style={{ cursor: 'pointer' }}
                        >
                          <td>{dr.uid}</td>
                          <td>{dr.name}</td>
                        </tr>
                      ))}
                      {filteredDataResources.length === 0 && (
                        <tr>
                          <td colSpan={2} className="text-muted text-center">
                            No data resources found.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-outline-dark" onClick={closeMappingModal}>
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
