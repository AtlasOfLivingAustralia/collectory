import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function InstitutionsReport() {
  const [simple, setSimple] = useState(false);
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'institutions', simple],
    queryFn: () => reportsApi.institutions(simple ? 'true' : 'false'),
  });

  return (
    <ReportLayout title="Institutions Report" isLoading={isLoading} error={error as Error}>
      {data && (
        <div className="dialog">
          <p>
            {data.length} institutions.{' '}
            <button className="btn btn-link p-0" onClick={() => setSimple(!simple)}>
              {simple ? 'Show full view.' : 'Show simple view.'}
            </button>
          </p>
          <table className="table table-striped table-bordered">
            <tbody>
              {data.map(i => (
                <tr key={i.uid}>
                  <td>{i.name}</td>
                  {!simple && (
                    <>
                      <td>{i.acronym}</td>
                      <td><Link to={`/public/show/${i.uid}`}>View</Link></td>
                      <td><Link to={`/institution/show/${i.uid}`}>Edit</Link></td>
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
