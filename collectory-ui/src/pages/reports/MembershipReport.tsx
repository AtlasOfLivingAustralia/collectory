import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi, type NetworkMember } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

function MemberList({ title, members }: { title: string; members: NetworkMember[] | { uid: string; name: string }[] }) {
  return (
    <div className="mb-4">
      <h3>{title} ({members.length})</h3>
      {members.length === 0 ? (
        <p className="text-muted">None</p>
      ) : (
        <table className="table table-striped table-bordered">
          <tbody>
            {members.map(m => (
              <tr key={m.uid}>
                <td>
                  <Link to={`/public/show/${m.uid}`}>{m.name}</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default function MembershipReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'membership'],
    queryFn: reportsApi.membership,
  });

  return (
    <ReportLayout title="Network Membership" isLoading={isLoading} error={error as Error}>
      {data && (
        <div className="dialog">
          <MemberList title="ALA Partners" members={data.partners} />
          <MemberList title="CHAH Members" members={data.chahMembers} />
          <MemberList title="CHAEC Members" members={data.chaecMembers} />
          <MemberList title="CHAFC Members" members={data.chafcMembers} />
          <MemberList title="AMRRN Members" members={data.amrrnMembers} />
          <MemberList title="CAMD Partners" members={data.camdMembers} />
        </div>
      )}
    </ReportLayout>
  );
}
