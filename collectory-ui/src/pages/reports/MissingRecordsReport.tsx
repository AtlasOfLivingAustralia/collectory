import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function MissingRecordsReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'missingRecords'],
    queryFn: reportsApi.missingRecords,
  });

  return (
    <ReportLayout title="Missing Records" isLoading={isLoading} error={error as Error}>
      {data && (
        <>
          <p>Collections where biocache records are less than 70% of claimed digitised records.</p>
          <table className="table table-striped table-bordered">
            <thead className="table-light">
              <tr><th>Collection</th><th>Biocache Count</th><th>Claimed Digitised</th></tr>
            </thead>
            <tbody>
              {data.mrs.map(m => (
                <tr key={m.collection.uid}>
                  <td><Link to={`/public/show/${m.collection.uid}`}>{m.collection.name}</Link></td>
                  <td>{m.biocacheCount.toLocaleString()}</td>
                  <td>{m.claimed.toLocaleString()}</td>
                </tr>
              ))}
              {data.mrs.length === 0 && (
                <tr><td colSpan={3} className="text-center text-muted">No missing records found.</td></tr>
              )}
            </tbody>
          </table>
        </>
      )}
    </ReportLayout>
  );
}
