import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function ClassificationReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'classification'],
    queryFn: reportsApi.classification,
  });

  return (
    <ReportLayout title="Classification" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr><th>Collection</th><th>Keywords</th></tr>
          </thead>
          <tbody>
            {data.collections.map(c => (
              <tr key={c.uid}>
                <td><Link to={`/public/show/${c.uid}`}>{c.name}</Link></td>
                <td>{c.keywords}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
