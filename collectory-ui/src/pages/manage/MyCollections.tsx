import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface EntitySummary {
  uid: string;
  name: string;
}

interface MyCollectionsData {
  collections: EntitySummary[];
  institutions: EntitySummary[];
}

export default function MyCollections() {
  const { t } = useTranslation();

  const { data, isLoading, error } = useQuery({
    queryKey: ['collection', 'myList'],
    queryFn: async () => {
      const { data } = await apiClient.get<MyCollectionsData>('/collection/myList');
      return data;
    },
  });

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb">
          <ol className="breadcrumb">
            <li className="breadcrumb-item">
              <Link to="/">{t('Home')}</Link>
            </li>
            <li className="breadcrumb-item">
              <Link to="/public/showAllCollections">{t('List all collections')}</Link>
            </li>
            <li className="breadcrumb-item">
              <Link to="/manage/createCollection">{t('Create new collection')}</Link>
            </li>
          </ol>
        </nav>

        <h1>{t('My collections')}</h1>

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">{t('Loading...')}</span>
            </div>
          </div>
        )}

        {error && (
          <div className="alert alert-danger">
            {t('Failed to load collections')}: {error instanceof Error ? error.message : t('Unknown error')}
          </div>
        )}

        {data && (
          <>
            <h4>{t('My Collections')}</h4>
            {data.collections.length > 0 ? (
              <ul className="list-group mb-4">
                {data.collections.map((c) => (
                  <li key={c.uid} className="list-group-item">
                    <Link to={`/public/show/${c.uid}`}>{c.name}</Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-muted">{t('No collections found.')}</p>
            )}

            <h4>{t('My Institutions')}</h4>
            {data.institutions.length > 0 ? (
              <ul className="list-group mb-4">
                {data.institutions.map((inst) => (
                  <li key={inst.uid} className="list-group-item">
                    <Link to={`/public/show/${inst.uid}`}>{inst.name}</Link>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-muted">{t('No institutions found.')}</p>
            )}
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
