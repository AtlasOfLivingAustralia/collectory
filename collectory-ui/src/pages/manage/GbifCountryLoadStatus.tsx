import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface ResourceLoadStatus {
  name: string;
  phase: string;
  dataResourceUid?: string;
}

interface CountryLoadSummary {
  country: string;
  totalResources?: number;
  percentComplete?: number;
  startTime?: string;
  finishTime?: string;
  resources: ResourceLoadStatus[];
}

export default function GbifCountryLoadStatus() {
  const { country } = useParams<{ country: string }>();
  const { t } = useTranslation();

  const { data, isLoading, error } = useQuery({
    queryKey: ['gbif', 'countryLoadStatus', country],
    queryFn: async () => {
      const { data } = await apiClient.get<CountryLoadSummary>(
        `/gbif/countryLoadStatus/${country}`,
      );
      return data;
    },
    enabled: !!country,
    refetchInterval: (query) => {
      if (query.state.data?.finishTime) return false;
      return 15000;
    },
  });

  const allComplete = !!data?.finishTime;

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <h1>
          {t('GBIF Auto-load status for')} {country}
          {data?.totalResources != null && (
            <small className="text-muted ms-2">
              ({data.totalResources} {t('resources')})
            </small>
          )}
        </h1>

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
              <div className="row mb-2">
                <div className="col-md-4">
                  <strong>{t('Started')}:</strong>{' '}
                  {data.startTime ? new Date(data.startTime).toLocaleString() : '-'}
                </div>
                <div className="col-md-4">
                  <strong>{t('Finished')}:</strong>{' '}
                  {data.finishTime ? new Date(data.finishTime).toLocaleString() : t('In progress...')}
                </div>
                <div className="col-md-4">
                  <strong>{t('Progress')}:</strong>{' '}
                  {data.percentComplete != null ? `${data.percentComplete}%` : '-'}
                </div>
              </div>

              {data.percentComplete != null && (
                <div className="progress" style={{ height: '25px' }}>
                  <div
                    className={`progress-bar ${allComplete ? 'bg-success' : ''}`}
                    role="progressbar"
                    style={{ width: `${data.percentComplete}%` }}
                    aria-valuenow={data.percentComplete}
                    aria-valuemin={0}
                    aria-valuemax={100}
                  >
                    {data.percentComplete}%
                  </div>
                </div>
              )}
            </div>

            {!allComplete && (
              <div className="alert alert-info">
                <i className="fa fa-refresh fa-spin me-1" />
                {t('Auto-refreshing every 15 seconds...')}
              </div>
            )}

            {allComplete && (
              <div className="alert alert-success">
                <i className="fa fa-check me-1" />
                {t('Load complete.')}
              </div>
            )}

            <h4>{t('Resources')}</h4>
            <table className="table table-striped">
              <thead>
                <tr>
                  <th>{t('Resource name')}</th>
                  <th>{t('Status / Phase')}</th>
                  <th>{t('Data resource')}</th>
                </tr>
              </thead>
              <tbody>
                {data.resources.map((r, i) => (
                  <tr key={i}>
                    <td>{r.name}</td>
                    <td>
                      <span
                        className={`badge ${
                          r.phase?.toUpperCase() === 'COMPLETED'
                            ? 'bg-success'
                            : r.phase?.toUpperCase() === 'ERROR'
                              ? 'bg-danger'
                              : 'bg-primary'
                        }`}
                      >
                        {r.phase}
                      </span>
                    </td>
                    <td>
                      {r.dataResourceUid ? (
                        <Link to={`/dataResource/show/${r.dataResourceUid}`}>
                          {r.dataResourceUid}
                        </Link>
                      ) : (
                        '-'
                      )}
                    </td>
                  </tr>
                ))}
                {data.resources.length === 0 && (
                  <tr>
                    <td colSpan={3} className="text-center text-muted">
                      {t('No resources')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
