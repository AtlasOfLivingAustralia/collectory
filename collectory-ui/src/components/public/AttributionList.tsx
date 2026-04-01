import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';

interface Attribution {
  name: string;
  url?: string;
}

interface AttributionListProps {
  entityUid: string;
}

export default function AttributionList({ entityUid }: AttributionListProps) {
  const { t } = useTranslation();

  // Determine entity type from uid prefix
  const entityType = (() => {
    if (entityUid.startsWith('co')) return 'collection';
    if (entityUid.startsWith('in')) return 'institution';
    if (entityUid.startsWith('dr')) return 'dataResource';
    if (entityUid.startsWith('dp')) return 'dataProvider';
    if (entityUid.startsWith('dh')) return 'dataHub';
    return 'collection';
  })();

  const { data: attributions } = useQuery<Attribution[]>({
    queryKey: ['attributions', entityUid],
    queryFn: async () => {
      const { data } = await apiClient.get<Attribution[]>(
        `/${entityType}/${entityUid}/attributions`,
      );
      return data;
    },
    enabled: !!entityUid,
    staleTime: 60_000,
  });

  if (!attributions || attributions.length === 0) return null;

  return (
    <section className="public-metadata" id="infoSourceList">
      <h4>{t('public.sdr.infosourcelist.title', 'Contributors to this page')}</h4>
      <ul>
        {attributions.map((attr, i) => (
          <li key={i}>
            {attr.url ? (
              <a href={attr.url} className="external" target="_blank" rel="noopener noreferrer">
                {attr.name}
              </a>
            ) : (
              attr.name
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
