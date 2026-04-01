import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function ProvidersReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'providers'],
    queryFn: reportsApi.providers,
  });

  return (
    <ReportLayout title="Data Providers" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <tbody>
            {data.map(dp => (
              <tr key={dp.uid}>
                <td><Link to={`/public/show/${dp.uid}`}>{dp.name}</Link></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
