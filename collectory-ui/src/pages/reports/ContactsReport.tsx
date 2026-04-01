import { useQuery } from '@tanstack/react-query';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function ContactsReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'contacts'],
    queryFn: reportsApi.contacts,
  });

  return (
    <ReportLayout title="All Contacts" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr><th>Name</th><th>Email</th><th>Phone</th></tr>
          </thead>
          <tbody>
            {data.map(c => (
              <tr key={c.id}>
                <td>{[c.title, c.firstName, c.lastName].filter(Boolean).join(' ')}</td>
                <td>{c.email}</td>
                <td>{c.phone}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
