import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function AttributionsReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'attributions'],
    queryFn: reportsApi.attributions,
  });

  return (
    <ReportLayout title="Attributions" isLoading={isLoading} error={error as Error}>
      {data && (
        <div className="dialog">
          <h3>Collections</h3>
          <table className="table table-striped table-bordered">
            <tbody>
              {data.collAttributions.map(a => (
                <tr key={a.entity.uid}>
                  <td><Link to={`/public/show/${a.entity.uid}`}>{a.entity.name}</Link></td>
                </tr>
              ))}
            </tbody>
          </table>

          <h3>Institutions</h3>
          <table className="table table-striped table-bordered">
            <tbody>
              {data.instAttributions.map(a => (
                <tr key={a.entity.uid}>
                  <td><Link to={`/public/show/${a.entity.uid}`}>{a.entity.name}</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ReportLayout>
  );
}
