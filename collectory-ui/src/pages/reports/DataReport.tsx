import { useQuery } from '@tanstack/react-query';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function DataReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'data'],
    queryFn: reportsApi.data,
  });

  const pct = (with_: number, total: number) => {
    if (total === 0) return '0%';
    return `${Math.round(((total - with_) / total) * 100)}%`;
  };

  return (
    <ReportLayout title="Data Summary" isLoading={isLoading} error={error as Error}>
      {data && (
        <div className="dialog">
          <table className="table table-striped table-bordered">
            <colgroup><col width="40%" /><col width="10%" /><col width="50%" /></colgroup>
            <tbody>
              <tr className="table-secondary"><td colSpan={3}><strong>Totals</strong></td></tr>
              <tr><td>Collections</td><td>{data.totalCollections}</td><td /></tr>
              <tr><td>Institutions</td><td>{data.totalInstitutions}</td><td /></tr>
              <tr><td>Data providers</td><td>{data.totalDataProviders}</td><td /></tr>
              <tr><td>Data resources</td><td>{data.totalDataResources}</td><td /></tr>
              <tr><td>Data hubs</td><td>{data.totalDataHubs}</td><td /></tr>
              <tr><td>Contacts</td><td>{data.totalContacts}</td><td /></tr>
            </tbody>
          </table>

          <h3>Data Completeness</h3>
          <table className="table table-striped table-bordered">
            <tbody>
              {[
                { label: 'Collections with no collection type', with_: data.collectionsWithType, total: data.totalCollections },
                { label: 'Collections with no focus', with_: data.collectionsWithFocus, total: data.totalCollections },
                { label: 'Collections with no description', with_: data.collectionsWithDescriptions, total: data.totalCollections },
                { label: 'Collections with no keywords', with_: data.collectionsWithKeywords, total: data.totalCollections },
                { label: 'Collections with no provider codes', with_: data.collectionsWithProviderCodes, total: data.totalCollections },
                { label: 'Collections with no geo. description', with_: data.collectionsWithGeoDescription, total: data.totalCollections },
                { label: 'Collections with no size', with_: data.collectionsWithNumRecords, total: data.totalCollections },
                { label: 'Collections with no digitised size', with_: data.collectionsWithNumRecordsDigitised, total: data.totalCollections },
              ].map(row => (
                <tr key={row.label}>
                  <td>{row.label}</td>
                  <td>{row.total - row.with_}</td>
                  <td>{pct(row.with_, row.total)} complete</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h3>Contact Coverage</h3>
          <table className="table table-striped table-bordered">
            <tbody>
              <tr>
                <td>Collections with no contacts</td>
                <td>{data.collectionsWithoutContacts}</td>
                <td>{data.totalCollections > 0 ? `${Math.round((data.collectionsWithoutContacts / data.totalCollections) * 100)}%` : '0%'}</td>
              </tr>
              <tr>
                <td>Collections with no email contacts</td>
                <td>{data.collectionsWithoutEmailContacts}</td>
                <td>{data.totalCollections > 0 ? `${Math.round((data.collectionsWithoutEmailContacts / data.totalCollections) * 100)}%` : '0%'}</td>
              </tr>
              <tr>
                <td>Institutions with no contacts</td>
                <td>{data.institutionsWithoutContacts}</td>
                <td>{data.totalInstitutions > 0 ? `${Math.round((data.institutionsWithoutContacts / data.totalInstitutions) * 100)}%` : '0%'}</td>
              </tr>
              <tr>
                <td>Institutions with no email contacts</td>
                <td>{data.institutionsWithoutEmailContacts}</td>
                <td>{data.totalInstitutions > 0 ? `${Math.round((data.institutionsWithoutEmailContacts / data.totalInstitutions) * 100)}%` : '0%'}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </ReportLayout>
  );
}
