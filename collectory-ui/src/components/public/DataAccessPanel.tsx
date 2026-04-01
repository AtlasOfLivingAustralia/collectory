import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

interface DataAccessPanelProps {
  entityUid: string;
  facetField: string;
  biocacheUiUrl?: string;
  biocacheServicesUrl?: string;
  loggerUrl?: string;
  alertsUrl?: string;
  recordCount?: number;
  disableLoggerLinks?: boolean;
  disableAlertLinks?: boolean;
}

export default function DataAccessPanel({
  entityUid,
  facetField,
  biocacheUiUrl,
  biocacheServicesUrl,
  loggerUrl,
  alertsUrl,
  disableLoggerLinks,
  disableAlertLinks,
}: DataAccessPanelProps) {
  const { t } = useTranslation();
  const [liveCount, setLiveCount] = useState<number | null>(null);

  // Fetch live record count from biocache — mirrors GSP's loadRecordCount() JS
  useEffect(() => {
    if (!biocacheServicesUrl || !entityUid) return;
    apiClient.get<{ totalRecords?: number }>(`/proxy/biocache/occurrences/search`, {
      params: { q: `${facetField}:${entityUid}`, pageSize: 0, facet: false },
    })
      .then((res) => {
        if (res.data?.totalRecords != null) setLiveCount(res.data.totalRecords);
      })
      .catch(() => {/* silently ignore */});
  }, [entityUid, facetField, biocacheServicesUrl]);

  if (!biocacheUiUrl) return null;

  const searchUrl = `${biocacheUiUrl}/occurrences/search?q=${facetField}:${entityUid}`;
  const displayCount = liveCount;

  return (
    <div className="public-metadata">
      {/* Large record count — matches GSP #totalRecordCountLinkHdr */}
      {displayCount != null && displayCount > 0 && (
        <h3 id="totalRecordCountLinkHdr">
          <a id="totalRecordCountLink" href={searchUrl} target="_blank" rel="noopener noreferrer">
            {displayCount.toLocaleString()} {t('public.show.rt.des03', 'records')}
          </a>
        </h3>
      )}

      <h4>{t('dataAccess.title', 'Data access')}</h4>
      <div className="dataAccess btn-group-vertical">
        <a href={searchUrl} className="btn btn-outline-dark" target="_blank" rel="noopener noreferrer">
          <i className="fa fa-list" /> {t('dataAccess.viewRecords', 'View records')}
        </a>
        {!disableLoggerLinks && loggerUrl && (
          <a
            href={`${loggerUrl}/reasonBreakdownCSV?eventId=1002&entityUid=${entityUid}`}
            className="btn btn-outline-dark"
            target="_blank"
            rel="noopener noreferrer"
          >
            <i className="fa fa-download" /> {t('dataAccess.downloadStats', 'Download usage stats')}
          </a>
        )}
        {!disableAlertLinks && alertsUrl && (
          <>
            <a
              href={`${alertsUrl}/webservice/createBiocacheNewRecordsAlert?webserviceQuery=${encodeURIComponent(`/occurrences/search?q=${facetField}:${entityUid}`)}&uiQuery=${encodeURIComponent(`/occurrences/search?q=${facetField}:${entityUid}`)}`}
              className="btn btn-outline-dark"
              target="_blank"
              rel="noopener noreferrer"
            >
              <i className="fa fa-bell" /> {t('dataAccess.newRecordsAlert', 'Alert me about new records')}
            </a>
            <a
              href={`${alertsUrl}/webservice/createBiocacheNewAnnotationsAlert?webserviceQuery=${encodeURIComponent(`/occurrences/search?q=${facetField}:${entityUid}`)}&uiQuery=${encodeURIComponent(`/occurrences/search?q=${facetField}:${entityUid}`)}`}
              className="btn btn-outline-dark"
              target="_blank"
              rel="noopener noreferrer"
            >
              <i className="fa fa-bell" /> {t('dataAccess.newAnnotationsAlert', 'Alert me about annotations')}
            </a>
          </>
        )}
      </div>
    </div>
  );
}
