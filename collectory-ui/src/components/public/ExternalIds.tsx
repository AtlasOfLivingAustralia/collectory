import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import type { ExternalIdentifier } from '../../api/types';

interface ExternalIdsProps {
  entityType: string;
  entityUid: string;
}

export default function ExternalIds({ entityType, entityUid }: ExternalIdsProps) {
  const { t } = useTranslation();

  const { data: identifiers } = useQuery({
    queryKey: ['externalIds', entityType, entityUid],
    queryFn: () =>
      apiClient
        .get<ExternalIdentifier[]>(`/${entityType}/${entityUid}/externalIdentifiers`)
        .then((r) => r.data),
    enabled: !!entityUid,
  });

  if (!identifiers || identifiers.length === 0) return null;

  return (
    <section className="public-metadata" id="externalIdentifiers">
      <h4>{t('externalIds.title', 'External identifiers')}</h4>
      <ul>
        {identifiers.map((ext) => {
          const label = `${ext.source}:${ext.identifier}`;
          return (
            <li key={ext.id}>
              {ext.uri ? (
                <a href={ext.uri} className="external" target="_blank" rel="noopener noreferrer">
                  {label}
                </a>
              ) : (
                <>{label}</>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
