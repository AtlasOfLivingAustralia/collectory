import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function CollectionsReport() {
  const [simple, setSimple] = useState(false);
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'collections', simple],
    queryFn: () => reportsApi.collections(simple ? 'true' : 'false'),
  });

  return (
    <ReportLayout title="Collections Report" isLoading={isLoading} error={error as Error}>
      {data && (
        <div className="dialog">
          {!simple && (
            <>
              <p><strong>View</strong> shows the public page. <strong>Edit</strong> shows the admin page.</p>
            </>
          )}
          <p>
            {data.length} collections.{' '}
            <button className="btn btn-link p-0" onClick={() => setSimple(!simple)}>
              {simple ? 'Show full view.' : 'Show simple view.'}
            </button>
          </p>
          <table className="table table-striped table-bordered">
            {!simple && (
              <colgroup><col width="60%" /><col width="20%" /><col width="10%" /><col width="10%" /></colgroup>
            )}
            <tbody>
              {data.map(c => (
                <tr key={c.uid}>
                  <td>{c.name}</td>
                  {!simple && (
                    <>
                      <td>{c.acronym}</td>
                      <td><Link to={`/public/show/${c.uid}`}>View</Link></td>
                      <td><Link to={`/collection/show/${c.uid}`}>Edit</Link></td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ReportLayout>
  );
}
