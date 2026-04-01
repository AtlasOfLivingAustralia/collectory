import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

interface UsageStatsProps {
  entityUid: string;
  /** Logger event type: 1002 for collections/institutions/providers, 2000 for website-type data resources */
  eventId?: string;
  loggerUrl?: string;
  disableLoggerLinks?: boolean;
}

interface ReasonDetail {
  events: number;
  records: number;
}

interface PeriodStats {
  events: number;
  records: number;
  reasonBreakdown: Record<string, ReasonDetail>;
}

interface UsageData {
  thisMonth?: PeriodStats;
  last3Months?: PeriodStats;
  lastYear?: PeriodStats;
  all?: PeriodStats;
}

const PERIOD_LABELS: Record<string, string> = {
  thisMonth: 'This month',
  last3Months: 'Last 3 months',
  lastYear: 'Last 12 months',
  all: 'All downloads',
};

const PERIODS: (keyof UsageData)[] = ['thisMonth', 'last3Months', 'lastYear', 'all'];

function addCommas(n: number): string {
  return n.toLocaleString();
}

function capitalise(s: string): string {
  if (!s) return s;
  if (s.length === 1) return s.toUpperCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Sort reason breakdown by records descending (matching Grails sortKV) */
function sortedReasons(breakdown: Record<string, ReasonDetail>): { key: string; value: ReasonDetail }[] {
  return Object.entries(breakdown)
    .map(([key, value]) => ({ key, value: value as ReasonDetail }))
    .sort((a, b) => b.value.records - a.value.records);
}

export default function UsageStats({ entityUid, eventId = '1002', loggerUrl, disableLoggerLinks }: UsageStatsProps) {
  const { t } = useTranslation();
  const [data, setData] = useState<UsageData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!loggerUrl || disableLoggerLinks) {
      setLoading(false);
      return;
    }

    // Use the backend proxy to avoid CORS.
    apiClient.get<UsageData>(`/proxy/logger/reasonBreakdown.json?eventId=${eventId}&entityUid=${entityUid}`)
      .then((res) => {
        setData(res.data);
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message);
        setLoading(false);
      });
  }, [entityUid, eventId, loggerUrl, disableLoggerLinks]);

  if (loading) {
    return (
      <div className="d-flex justify-content-center my-4">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="alert alert-warning">
        {t('usageStats.noData', 'No usage statistics available.')}
      </div>
    );
  }

  if (!data) {
    return (
      <div className="alert alert-info">
        {t('usageStats.noData', 'No usage statistics available.')}
      </div>
    );
  }

  return (
    <div id="usage">
      {PERIODS.map((period) => {
        const stats = data[period];
        if (!stats) return null;

        const breakdown = stats.reasonBreakdown ?? {};
        const testingDetail = breakdown['testing'];
        const nonTestingRecords = testingDetail ? stats.records - testingDetail.records : stats.records;
        const nonTestingEvents = testingDetail ? stats.events - testingDetail.events : stats.events;
        const reasons = sortedReasons(breakdown);

        return (
          <div key={period} className="usageDiv card card-body mb-3">
            <h4>
              <span>{PERIOD_LABELS[period] ?? period}</span>
              <span className="float-end">
                {addCommas(nonTestingRecords)} {t('usageStats.recordsDownloaded', 'records downloaded from')}{' '}
                {addCommas(nonTestingEvents)} {t('usageStats.downloads', 'downloads')}.
              </span>
            </h4>
            {reasons.length > 0 && (
              <table className="table">
                <tbody>
                  {reasons.map(({ key, value }) => {
                    const isTesting = key.toLowerCase().includes('test');
                    return (
                      <tr key={key} style={isTesting ? { color: '#999' } : undefined}>
                        <td>
                          {capitalise(key)}
                          {isTesting && (
                            <><br /><span style={{ fontSize: 12 }}> *{t('usageStats.testingNote', 'Testing downloads are not included in the totals above')}</span></>
                          )}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          {addCommas(value.events)} {t('usageStats.events', 'events')}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          {addCommas(value.records)} {t('usageStats.records', 'records')}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        );
      })}
    </div>
  );
}
