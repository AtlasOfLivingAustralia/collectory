import { useEffect, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface ResourceStatus {
  name: string;
  phase: string;
  uid?: string;
}

interface LoadStatus {
  loadGuid: string;
  startTime?: string;
  finishTime?: string;
  percentComplete?: number;
  resources: ResourceStatus[];
}

const phaseKeyframes = `
@keyframes phaseGenerating {
  0% { background-color: yellow; }
  50% { background-color: orange; }
  100% { background-color: yellow; }
}
@keyframes phaseDownloading {
  0% { background-color: cornflowerblue; }
  50% { background-color: orange; }
  100% { background-color: cornflowerblue; }
}
`;

function getPhaseStyle(phase: string): React.CSSProperties {
  const upper = phase?.toUpperCase() || '';
  const base: React.CSSProperties = {
    padding: '4px 8px',
    borderRadius: '4px',
    fontWeight: 'bold',
    display: 'inline-block',
  };
  switch (upper) {
    case 'GENERATING':
      return { ...base, animation: 'phaseGenerating 2s ease-in-out infinite', backgroundColor: 'yellow' };
    case 'DOWNLOADING':
      return { ...base, animation: 'phaseDownloading 2s ease-in-out infinite', backgroundColor: 'cornflowerblue' };
    case 'QUEUED':
      return { ...base, backgroundColor: 'lightpink' };
    case 'ERROR':
      return { ...base, backgroundColor: 'crimson', color: 'white' };
    case 'COMPLETED':
      return { ...base, backgroundColor: 'green', color: 'white' };
    default:
      return { ...base, backgroundColor: '#6c757d', color: 'white' };
  }
}

export default function ExternalLoadStatus() {
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const loadGuid = searchParams.get('loadGuid') || '';

  const { data: status, isLoading, error } = useQuery({
    queryKey: ['externalLoadStatus', loadGuid],
    queryFn: async () => {
      const { data } = await apiClient.get<LoadStatus>('/manage/externalLoadStatus', {
        params: { loadGuid },
      });
      return data;
    },
    enabled: !!loadGuid,
  });

  const allComplete = useMemo(() => {
    if (!status) return false;
    return !!status.finishTime;
  }, [status]);

  // Auto-refresh every 15 seconds until complete
  useEffect(() => {
    if (allComplete || !loadGuid) return;

    const interval = setInterval(() => {
      queryClient.invalidateQueries({ queryKey: ['externalLoadStatus', loadGuid] });
    }, 15000);

    return () => clearInterval(interval);
  }, [allComplete, loadGuid, queryClient]);

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <style dangerouslySetInnerHTML={{ __html: phaseKeyframes }} />
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><Link to="/manage/list">Manage</Link></li>
            <li className="breadcrumb-item"><Link to="/manage/externalLoad">External Data Load</Link></li>
            <li className="breadcrumb-item active">Status</li>
          </ol>
        </nav>
        <h1>External Load Status</h1>

        {!loadGuid && (
          <div className="alert alert-warning">No load GUID specified.</div>
        )}

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Loading status...</span>
            </div>
          </div>
        )}

        {error && (
          <div className="alert alert-danger">
            Failed to load status: {error instanceof Error ? error.message : 'Unknown error'}
          </div>
        )}

        {status && (
          <>
            <div className="card card-body mb-3">
              <div className="row">
                <div className="col-md-4">
                  <strong>Start time:</strong>{' '}
                  {status.startTime ? new Date(status.startTime).toLocaleString() : '-'}
                </div>
                <div className="col-md-4">
                  <strong>Finish time:</strong>{' '}
                  {status.finishTime ? new Date(status.finishTime).toLocaleString() : 'In progress...'}
                </div>
                <div className="col-md-4">
                  <strong>Progress:</strong>{' '}
                  {status.percentComplete != null ? `${status.percentComplete}%` : '-'}
                </div>
              </div>

              {status.percentComplete != null && (
                <div className="progress mt-3" style={{ height: '25px' }}>
                  <div
                    className={`progress-bar ${allComplete ? 'bg-success' : ''}`}
                    role="progressbar"
                    style={{ width: `${status.percentComplete}%` }}
                    aria-valuenow={status.percentComplete}
                    aria-valuemin={0}
                    aria-valuemax={100}
                  >
                    {status.percentComplete}%
                  </div>
                </div>
              )}
            </div>

            {!allComplete && (
              <div className="alert alert-info">
                <i className="fa fa-refresh fa-spin me-1" />
                Auto-refreshing every 15 seconds...
              </div>
            )}

            {allComplete && (
              <div className="alert alert-success">
                <i className="fa fa-check me-1" />
                Load complete.
              </div>
            )}

            <h4>Resources</h4>
            <table className="table table-striped">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Phase</th>
                  <th>UID</th>
                </tr>
              </thead>
              <tbody>
                {status.resources.map((r, i) => (
                  <tr key={i}>
                    <td>{r.name}</td>
                    <td>
                      <span style={getPhaseStyle(r.phase)}>
                        {r.phase}
                      </span>
                    </td>
                    <td>
                      {r.uid ? (
                        <Link to={`/dataResource/show/${r.uid}`}>{r.uid}</Link>
                      ) : (
                        '-'
                      )}
                    </td>
                  </tr>
                ))}
                {status.resources.length === 0 && (
                  <tr>
                    <td colSpan={3} className="text-center text-muted">No resources</td>
                  </tr>
                )}
              </tbody>
            </table>

            <Link to="/manage/externalLoad" className="btn btn-outline-dark">
              <i className="fa fa-arrow-left me-1" />
              Back to Search
            </Link>
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
