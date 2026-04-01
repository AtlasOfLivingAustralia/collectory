import { useQuery } from '@tanstack/react-query';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function ActivityReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'activity'],
    queryFn: reportsApi.activity,
  });

  return (
    <ReportLayout title="Activity Report" isLoading={isLoading} error={error as Error}>
      {data && (
        <div className="dialog">
          <h3>Usage statistics</h3>
          <table className="table table-striped table-bordered">
            <colgroup><col width="30%" /><col width="70%" /></colgroup>
            <tbody>
              <tr><td>Curator views</td><td>{data.curatorViews}</td></tr>
              <tr><td>Curator previews</td><td>{data.curatorPreviews}</td></tr>
              <tr><td>Curator edits</td><td>{data.curatorEdits}</td></tr>
              <tr><td>Admin views</td><td>{data.adminViews}</td></tr>
              <tr><td>Admin previews</td><td>{data.adminPreviews}</td></tr>
              <tr><td>Admin edits</td><td>{data.adminEdits}</td></tr>
              <tr><td colSpan={2}><strong>Latest activity</strong></td></tr>
              {data.latestActivity.map(entry => (
                <tr key={entry.id}>
                  <td colSpan={2}>
                    {entry.timestamp} — {entry.user} — {entry.action}
                    {entry.entityUid && <> — {entry.entityUid}</>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ReportLayout>
  );
}
