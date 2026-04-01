import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function DataLinksReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'dataLinks'],
    queryFn: reportsApi.dataLinks,
  });

  return (
    <ReportLayout title="Data Links" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr><th>Provider</th><th>Consumer</th></tr>
          </thead>
          <tbody>
            {data.links.map((link, i) => (
              <tr key={i}>
                <td><Link to={`/public/show/${link.provider}`}>{link.provider}</Link></td>
                <td><Link to={`/public/show/${link.consumer}`}>{link.consumer}</Link></td>
              </tr>
            ))}
            {data.links.length === 0 && (
              <tr><td colSpan={2} className="text-center text-muted">No data links found.</td></tr>
            )}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
