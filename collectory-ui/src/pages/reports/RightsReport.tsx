import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function RightsReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'rights'],
    queryFn: reportsApi.rights,
  });

  return (
    <ReportLayout title="Rights &amp; Licensing" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr>
              <th>Resource</th>
              <th>License Type</th>
              <th>Version</th>
              <th>Rights</th>
              <th>Filed</th>
            </tr>
          </thead>
          <tbody>
            {data.map(dr => (
              <tr key={dr.uid}>
                <td><Link to={`/public/show/${dr.uid}`}>{dr.name}</Link></td>
                <td>{dr.licenseType}</td>
                <td>{dr.licenseVersion}</td>
                <td>{dr.rights}</td>
                <td>{dr.filed ? 'Yes' : 'No'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
