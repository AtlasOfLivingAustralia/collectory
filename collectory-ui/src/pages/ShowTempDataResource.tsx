import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../api/client';
import type { TempDataResource } from '../api/types';

export default function ShowTempDataResource() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const { data: resource, isLoading, error } = useQuery<TempDataResource>({
    queryKey: ['tempDataResource', id],
    queryFn: () => apiClient.get<TempDataResource>(`/tempDataResource/${id}`).then((r) => r.data),
    enabled: !!id,
  });

  if (isLoading) {
    return (
      <div className="container mt-4">
        <div className="d-flex justify-content-center">
          <div className="spinner-border" role="status">
            <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
          </div>
        </div>
      </div>
    );
  }

  if (error || !resource) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">
          {t('error.notFound', 'Entity not found or an error occurred.')}
        </div>
      </div>
    );
  }

  const contactName = [resource.firstName, resource.lastName].filter(Boolean).join(' ');

  return (
    <div className="container mt-4">
      <h1>{resource.name ?? resource.uid}</h1>

      <div className="row">
        {/* Main content */}
        <div className="col-md-9">
          {resource.description && (
            <div className="mb-3">
              <h4>{t('tempDataResource.description', 'Description')}</h4>
              <p>{resource.description}</p>
            </div>
          )}

          <div className="mb-3">
            <h4>{t('tempDataResource.details', 'Details')}</h4>
            <table className="table table-sm">
              <tbody>
                <tr>
                  <th>{t('tempDataResource.uid', 'UID')}</th>
                  <td>{resource.uid}</td>
                </tr>
                {resource.status && (
                  <tr>
                    <th>{t('tempDataResource.status', 'Status')}</th>
                    <td>{resource.status}</td>
                  </tr>
                )}
                <tr>
                  <th>{t('tempDataResource.numberOfRecords', 'Number of records')}</th>
                  <td>{resource.numberOfRecords?.toLocaleString() ?? 0}</td>
                </tr>
                {resource.license && (
                  <tr>
                    <th>{t('tempDataResource.license', 'License')}</th>
                    <td>{resource.license}</td>
                  </tr>
                )}
                {resource.citation && (
                  <tr>
                    <th>{t('tempDataResource.citation', 'Citation')}</th>
                    <td>{resource.citation}</td>
                  </tr>
                )}
                <tr>
                  <th>{t('tempDataResource.created', 'Created')}</th>
                  <td>{new Date(resource.dateCreated).toLocaleDateString()}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <p className="text-muted small">
            {t('common.lastUpdated', 'Last updated')}: {new Date(resource.lastUpdated).toLocaleDateString()}
          </p>
        </div>

        {/* Sidebar */}
        <div className="col-md-3">
          {(contactName || resource.email) && (
            <div className="card mb-3">
              <div className="card-header">
                <h5 className="card-title mb-0">
                  <i className="fa fa-user me-2" />
                  {t('contacts.title', 'Contact')}
                </h5>
              </div>
              <div className="card-body">
                {contactName && <p className="fw-bold mb-1">{contactName}</p>}
                {resource.email && (
                  <p className="mb-0">
                    <i className="fa fa-envelope me-1" />
                    <a href={`mailto:${resource.email}`}>{resource.email}</a>
                  </p>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
