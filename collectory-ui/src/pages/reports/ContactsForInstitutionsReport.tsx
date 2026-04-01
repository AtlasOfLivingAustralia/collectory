import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function ContactsForInstitutionsReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'contactsForInstitutions'],
    queryFn: reportsApi.contactsForInstitutions,
  });

  return (
    <ReportLayout title="Contacts for Institutions" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr><th>Institution</th><th>Contacts</th></tr>
          </thead>
          <tbody>
            {data.map(entity => (
              <tr key={entity.uid}>
                <td><Link to={`/public/show/${entity.uid}`}>{entity.name}</Link></td>
                <td>
                  {entity.contacts.length === 0 ? (
                    <span className="text-muted">None</span>
                  ) : (
                    entity.contacts.map((c, i) => (
                      <div key={i}>
                        {c.name}{c.primaryContact && ' *'}
                        {c.email && <> — {c.email}</>}
                        {c.role && <> ({c.role})</>}
                      </div>
                    ))
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
