import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface LinkedStats {
  dataResourcesWithData?: number;
  shareable?: number;
  licenceIssues?: number;
  notShareable?: number;
  providedByGBIF?: number;
  linkedToDataProvider?: number;
  linkedToInstitution?: number;
}

export default function GbifHealthCheckLinked() {
  const { t } = useTranslation();

  const { data: stats, isLoading, error } = useQuery({
    queryKey: ['gbif', 'healthCheckLinked'],
    queryFn: async () => {
      const { data } = await apiClient.get<LinkedStats>('/gbif/healthCheckLinked');
      return data;
    },
  });

  const rows: { label: string; key: keyof LinkedStats }[] = [
    { label: t('Linked data sets with data'), key: 'dataResourcesWithData' },
    { label: t('Shareable'), key: 'shareable' },
    { label: t('Licence issues'), key: 'licenceIssues' },
    { label: t('Marked as not shareable'), key: 'notShareable' },
    { label: t('Provided by GBIF'), key: 'providedByGBIF' },
    { label: t('Linked to data provider'), key: 'linkedToDataProvider' },
    { label: t('Linked to institution'), key: 'linkedToInstitution' },
  ];

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><Link to="/manage/list">Manage</Link></li>
            <li className="breadcrumb-item active">GBIF Health Check (Linked)</li>
          </ol>
        </nav>
        <h1>{t('GBIF Syncing Healthcheck - Linked resources')}</h1>

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">{t('Loading...')}</span>
            </div>
          </div>
        )}

        {error && (
          <div className="alert alert-danger">
            {t('Failed to load health check data')}: {error instanceof Error ? error.message : t('Unknown error')}
          </div>
        )}

        {stats && (
          <table className="table table-striped">
            <thead>
              <tr>
                <th>{t('Metric')}</th>
                <th className="text-end">{t('Count')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.key}>
                  <td>{row.label}</td>
                  <td className="text-end">
                    {stats[row.key] != null ? stats[row.key]!.toLocaleString() : '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </ProtectedRoute>
  );
}
