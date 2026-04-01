import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function HarvestersReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'harvesters'],
    queryFn: reportsApi.harvesters,
  });

  return (
    <ReportLayout title="Harvester Configuration" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr>
              <th>Resource</th>
              <th>Status</th>
              <th>Frequency</th>
              <th>Last Checked</th>
              <th>Data Currency</th>
            </tr>
          </thead>
          <tbody>
            {data.map(dr => (
              <tr key={dr.uid}>
                <td><Link to={`/public/show/${dr.uid}`}>{dr.name}</Link></td>
                <td>{dr.status}</td>
                <td>{dr.harvestingFrequency}</td>
                <td>{dr.lastChecked}</td>
                <td>{dr.dataCurrency}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
