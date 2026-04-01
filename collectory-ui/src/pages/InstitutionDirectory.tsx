import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useInstitutions } from '../hooks/useInstitutions';

export default function InstitutionDirectory() {
  const { t } = useTranslation();
  const { data: institutions, isLoading, isError, error } = useInstitutions();

  const sorted = institutions
    ? [...institutions].sort((a, b) => a.name.localeCompare(b.name))
    : [];

  return (
    <div className="container py-3">
      <h1>{t('public.listInstitutions.title', 'Institutions')}</h1>

      {isLoading && (
        <div className="d-flex justify-content-center py-5">
          <div className="spinner-border" role="status">
            <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
          </div>
        </div>
      )}

      {isError && (
        <div className="alert alert-danger" role="alert">
          {t('common.error', 'An error occurred while loading data.')}
          {error instanceof Error && <span className="ms-1">{error.message}</span>}
        </div>
      )}

      {!isLoading && !isError && sorted.length === 0 && (
        <p className="text-muted">{t('public.listInstitutions.noResults', 'No institutions found.')}</p>
      )}

      {sorted.length > 0 && (
        <ul className="list-group">
          {sorted.map((inst) => (
            <li key={inst.uid} className="list-group-item">
              <Link to={`/public/showInstitution/${inst.uid}`}>
                {inst.name}
              </Link>
              {inst.acronym && (
                <span className="text-muted ms-1">({inst.acronym})</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
