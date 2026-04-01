import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function CollectionTypesReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'collectionTypes'],
    queryFn: reportsApi.collectionTypes,
  });

  return (
    <ReportLayout title="Collection Types" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr><th>Collection</th><th>Type</th></tr>
          </thead>
          <tbody>
            {data.map(c => (
              <tr key={c.uid}>
                <td><Link to={`/public/show/${c.uid}`}>{c.name}</Link></td>
                <td>{c.collectionType}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
