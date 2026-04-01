import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function ProviderRecordsDataReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'providerRecordsData'],
    queryFn: reportsApi.providerRecordsData,
  });

  return (
    <ReportLayout title="Provider Records Data" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr>
              <th>Provider</th>
              <th>Acronym</th>
              <th>Biocache Records</th>
            </tr>
          </thead>
          <tbody>
            {data.statistics.map(s => (
              <tr key={s.uid}>
                <td><Link to={`/public/show/${s.uid}`}>{s.name}</Link></td>
                <td>{s.acronym}</td>
                <td>{s.numBiocacheRecords != null && s.numBiocacheRecords !== -1 ? s.numBiocacheRecords.toLocaleString() : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
