import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function ResourcesReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'resources'],
    queryFn: reportsApi.resources,
  });

  return (
    <ReportLayout title="Data Resources" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr><th>Name</th><th>Status</th><th>Type</th></tr>
          </thead>
          <tbody>
            {data.map(dr => (
              <tr key={dr.uid}>
                <td><Link to={`/public/show/${dr.uid}`}>{dr.name}</Link></td>
                <td>{dr.status}</td>
                <td>{dr.resourceType}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
