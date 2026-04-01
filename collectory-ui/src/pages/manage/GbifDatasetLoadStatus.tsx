import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface GbifDatasetSummary {
  phase: string;
  message?: string;
  dataResourceUid?: string;
  dataResourceName?: string;
}

export default function GbifDatasetLoadStatus() {
  const { datasetKey } = useParams<{ datasetKey: string }>();
  const { t } = useTranslation();

  const { data, isLoading, error } = useQuery({
    queryKey: ['gbif', 'datasetLoadStatus', datasetKey],
    queryFn: async () => {
      const { data } = await apiClient.get<GbifDatasetSummary>(
        `/gbif/datasetLoadStatus/${datasetKey}`,
      );
      return data;
    },
    enabled: !!datasetKey,
    refetchInterval: (query) => {
      const phase = query.state.data?.phase?.toUpperCase();
      if (phase === 'COMPLETED' || phase === 'ERROR') return false;
      return 15000;
    },
  });

  const completed = data?.phase?.toUpperCase() === 'COMPLETED';
  const hasError = data?.phase?.toUpperCase() === 'ERROR';

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <h1>{t('Reloading GBIF dataset')} {datasetKey}</h1>

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">{t('Loading...')}</span>
            </div>
          </div>
        )}

        {error && (
          <div className="alert alert-danger">
            {t('Failed to load status')}: {error instanceof Error ? error.message : t('Unknown error')}
          </div>
        )}

        {data && (
          <>
            <div className="card card-body mb-3">
              <div className="mb-2">
                <strong>{t('Phase')}:</strong>{' '}
                <span className={`badge ${completed ? 'bg-success' : hasError ? 'bg-danger' : 'bg-primary'}`}>
                  {data.phase}
                </span>
              </div>

              {data.message && (
                <div className="mb-2">
                  <strong>{t('Status')}:</strong> {data.message}
                </div>
              )}
            </div>

            {!completed && !hasError && (
              <div className="alert alert-info">
                <i className="fa fa-refresh fa-spin me-1" />
                {t('Please keep this window open. It will automatically refresh.')}
              </div>
            )}

            {hasError && (
              <div className="alert alert-danger">
                <i className="fa fa-exclamation-triangle me-1" />
                {t('An error occurred during loading.')}
              </div>
            )}

            {completed && (
              <>
                <div className="alert alert-success">
                  <i className="fa fa-check me-1" />
                  {t('Load complete.')}
                </div>
                {data.dataResourceUid && (
                  <Link
                    to={`/dataResource/show/${data.dataResourceUid}`}
                    className="btn btn-primary"
                  >
                    <i className="fa fa-arrow-right me-1" />
                    {t('View data resource')} {data.dataResourceName || data.dataResourceUid}
                  </Link>
                )}
              </>
            )}
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
