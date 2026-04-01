import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface HealthCheckStats {
  recordsIndexed?: number;
  shareablePercentage?: number;
  datasetsWithData?: number;
  shareableDatasets?: number;
  licenceIssues?: number;
  notShareable?: number;
  providedByGbif?: number;
  linkedCount?: number;
  notShareableNoOwner?: number;
  linkedToInstitution?: number;
  linkedToDataProvider?: number;
}

export default function GbifHealthCheck() {
  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState<{ success: boolean; message: string } | null>(null);

  const { data: stats, isLoading, error } = useQuery({
    queryKey: ['gbif', 'healthCheck'],
    queryFn: async () => {
      const { data } = await apiClient.get<HealthCheckStats>('/gbif/healthCheck');
      return data;
    },
  });

  async function handleDownloadCSV() {
    try {
      const response = await apiClient.get('/gbif/downloadCSV', { responseType: 'blob' });
      const blob = new Blob([response.data], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'gbif-health-check.csv';
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to download CSV:', err);
    }
  }

  async function handleSyncAll() {
    if (!window.confirm('Are you sure you want to sync all resources with GBIF? This may take a while.')) {
      return;
    }

    setSyncing(true);
    setSyncResult(null);
    try {
      await apiClient.post('/gbif/syncAllResources');
      setSyncResult({ success: true, message: 'Sync initiated successfully. This may take some time to complete.' });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Sync failed.';
      setSyncResult({ success: false, message });
    } finally {
      setSyncing(false);
    }
  }

  const metrics: { label: string; key: keyof HealthCheckStats; description?: string; format?: (v: number) => string }[] = [
    { label: 'Records Indexed', key: 'recordsIndexed', description: 'Total records indexed in the system', format: (v) => v.toLocaleString() },
    { label: 'Shareable %', key: 'shareablePercentage', description: 'Percentage of datasets that are shareable', format: (v) => `${v.toFixed(1)}%` },
    { label: 'Datasets with Data', key: 'datasetsWithData', description: 'Number of datasets that contain data' },
    { label: 'Shareable Datasets', key: 'shareableDatasets', description: 'Number of datasets marked as shareable' },
    { label: 'Licence Issues', key: 'licenceIssues', description: 'Datasets with licence problems' },
    { label: 'Not Shareable', key: 'notShareable', description: 'Datasets that are not shareable' },
    { label: 'Not Shareable (No Owner)', key: 'notShareableNoOwner', description: 'Datasets not shareable due to no owner' },
    { label: 'Provided by GBIF', key: 'providedByGbif', description: 'Datasets sourced from GBIF' },
    { label: 'Linked Count', key: 'linkedCount', description: 'Total linked datasets' },
    { label: 'Linked to Institution', key: 'linkedToInstitution', description: 'Datasets linked to an institution' },
    { label: 'Linked to Data Provider', key: 'linkedToDataProvider', description: 'Datasets linked to a data provider' },
  ];

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><Link to="/manage/list">Manage</Link></li>
            <li className="breadcrumb-item active">GBIF Health Check</li>
          </ol>
        </nav>
        <h1>GBIF Sync Health Check</h1>

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
          </div>
        )}

        {error && (
          <div className="alert alert-danger">
            Failed to load health check data: {error instanceof Error ? error.message : 'Unknown error'}
          </div>
        )}

        {syncResult && (
          <div className={`alert ${syncResult.success ? 'alert-success' : 'alert-danger'}`}>
            {syncResult.message}
          </div>
        )}

        {stats && (
          <>
            <table className="table table-striped mb-4">
              <thead>
                <tr>
                  <th>Metric</th>
                  <th>Description</th>
                  <th className="text-end">Value</th>
                </tr>
              </thead>
              <tbody>
                {metrics.map((m) => {
                  const value = stats[m.key];
                  return (
                    <tr key={m.key}>
                      <td className="fw-bold">{m.label}</td>
                      <td className="text-muted">{m.description || ''}</td>
                      <td className="text-end">
                        {value != null
                          ? m.format
                            ? m.format(value)
                            : value.toLocaleString()
                          : '-'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            <div className="d-flex gap-2">
              <button
                type="button"
                className="btn btn-outline-dark"
                onClick={handleDownloadCSV}
              >
                <i className="fa fa-download me-1" />
                Download CSV
              </button>
              <button
                type="button"
                className="btn btn-warning"
                disabled={syncing}
                onClick={handleSyncAll}
              >
                {syncing ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-1" role="status" />
                    Syncing...
                  </>
                ) : (
                  <>
                    <i className="fa fa-refresh me-1" />
                    Sync All Resources
                  </>
                )}
              </button>
            </div>
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
