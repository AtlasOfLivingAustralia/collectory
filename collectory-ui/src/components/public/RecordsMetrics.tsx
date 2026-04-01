import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

interface RecordsMetricsProps {
  entityUid: string;
  facetField: string;
  biocacheServicesUrl?: string;
  biocacheUiUrl?: string;
  /** For collections: show digitisation progress bar */
  numRecords?: number;
  numRecordsDigitised?: number;
  /** Formatted collection name (with "Collection" suffix if needed) */
  collectionName?: string;
  /** Noun for the collection type (e.g. "specimens", "cultures") */
  noun?: string;
  /** Warning text when records do not exactly match this collection */
  inexactMappingWarning?: string;
}

export default function RecordsMetrics({
  entityUid,
  facetField,
  biocacheUiUrl,
  numRecords,
  numRecordsDigitised,
  collectionName,
  noun,
  inexactMappingWarning,
}: RecordsMetricsProps) {
  const { t } = useTranslation();

  const { data: biocacheCount, isLoading } = useQuery({
    queryKey: ['biocacheCount', entityUid, facetField],
    queryFn: async () => {
      try {
        const { data } = await apiClient.get<{ totalRecords: number }>(
          `/proxy/biocache/occurrences/search`,
          { params: { q: `${facetField}:${entityUid}`, pageSize: 0 } },
        );
        return data.totalRecords ?? 0;
      } catch {
        return 0;
      }
    },
    enabled: !!entityUid,
    staleTime: 60_000,
  });

  const searchUrl = `${biocacheUiUrl ?? ''}/occurrences/search?q=${facetField}:${entityUid}`;
  const count = biocacheCount ?? 0;

  // numRecords == -1 means "not set" in Grails convention
  const hasEstimate = numRecords != null && numRecords !== -1 && numRecords > 0;
  const showDigitisation =
    hasEstimate && numRecordsDigitised != null && numRecordsDigitised !== -1;
  const digitisedPct = showDigitisation
    ? Math.min(100, Math.round(((numRecordsDigitised ?? 0) / (numRecords ?? 1)) * 100))
    : 0;

  // Percentage of numRecords that are digitised (1 decimal place, matching reference "4.2 %")
  const digitisedPctDisplay = showDigitisation
    ? (((numRecordsDigitised ?? 0) / (numRecords ?? 1)) * 100).toFixed(1).replace(/\.0$/, '')
    : null;

  // Percentage of numRecords accessible via biocache (1 decimal place)
  const biocachePct = hasEstimate && count > 0 && numRecords
    ? (((count) / numRecords) * 100).toFixed(1).replace(/\.0$/, '')
    : null;

  return (
    <div>
      <h2>{t('records.title', 'Digitised records available through the Atlas')}</h2>

      {collectionName && hasEstimate && (
        <p>
          {collectionName} has an estimated {numRecords!.toLocaleString()} {noun ?? 'specimens'}.
        </p>
      )}

      {inexactMappingWarning && (
        <div id="warnings">
          <p className="text-warning">Records do not exactly match this collection.<br />{inexactMappingWarning}</p>
        </div>
      )}

      {!isLoading && (
        <p>
          {showDigitisation && (
            <>The collection has databased {digitisedPctDisplay} % of these ({numRecordsDigitised!.toLocaleString()} records). </>
          )}
          <strong>{count.toLocaleString()} records</strong>{' '}
          can be accessed through the Atlas of Living Australia
          <br />
          <a href={searchUrl} target="_blank" rel="noopener noreferrer">
            Click to view all records for {collectionName}
          </a>
        </p>
      )}

      {biocachePct && numRecords && (
        <p>
          Approximately, records for {biocachePct}% of {noun ?? 'specimens'} are available for viewing in the Atlas of Living Australia.
        </p>
      )}

      {showDigitisation && (
        <div className="mb-3" id="progressBarItem">
          <div className="progress">
            <div
              className="progress-bar bg-success"
              role="progressbar"
              style={{ width: `${digitisedPct}%` }}
              aria-valuenow={digitisedPct}
              aria-valuemin={0}
              aria-valuemax={100}
            />
          </div>
        </div>
      )}
    </div>
  );
}
