import { useQuery } from '@tanstack/react-query';
import { reportsApi, type CouncilContactItem } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

function CouncilSection({ title, items }: { title: string; items: CouncilContactItem[] }) {
  return (
    <div className="mb-4">
      <h3>{title} ({items.length})</h3>
      {items.length === 0 ? (
        <p className="text-muted">None</p>
      ) : (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr><th>Collection</th><th>Primary Contact</th><th>Email</th></tr>
          </thead>
          <tbody>
            {items.map(item => (
              <tr key={item.id}>
                <td>{item.name}</td>
                <td>{item.contact}</td>
                <td>{item.email}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default function ContactsForCouncilMembersReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'contactsForCouncilMembers'],
    queryFn: reportsApi.contactsForCouncilMembers,
  });

  return (
    <ReportLayout title="Contacts for Council Members" isLoading={isLoading} error={error as Error}>
      {data && (
        <div className="dialog">
          <CouncilSection title="CHAFC" items={data.chafc} />
          <CouncilSection title="CHAEC" items={data.chaec} />
          <CouncilSection title="CHACM" items={data.chacm} />
        </div>
      )}
    </ReportLayout>
  );
}
