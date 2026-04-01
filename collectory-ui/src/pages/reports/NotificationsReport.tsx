import { useQuery } from '@tanstack/react-query';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function NotificationsReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'notifications'],
    queryFn: reportsApi.notifications,
  });

  return (
    <ReportLayout title="Notifications" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr><th>Timestamp</th><th>Action</th><th>Entity</th><th>User</th></tr>
          </thead>
          <tbody>
            {data.notices.map(n => (
              <tr key={n.id}>
                <td>{n.timestamp}</td>
                <td>{n.action}</td>
                <td>{n.entityUid}</td>
                <td>{n.user}</td>
              </tr>
            ))}
            {data.notices.length === 0 && (
              <tr><td colSpan={4} className="text-center text-muted">No notifications found.</td></tr>
            )}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
