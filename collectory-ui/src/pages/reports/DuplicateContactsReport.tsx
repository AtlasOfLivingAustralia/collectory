import { useQuery } from '@tanstack/react-query';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function DuplicateContactsReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'duplicateContacts'],
    queryFn: reportsApi.duplicateContacts,
  });

  return (
    <ReportLayout title="Duplicate Contacts" isLoading={isLoading} error={error as Error}>
      {data && (
        <div className="dialog">
          <h3>Duplicate Emails ({data.dupEmails.length})</h3>
          {data.dupEmails.length === 0 ? (
            <p className="text-muted">No duplicate emails found.</p>
          ) : (
            <table className="table table-striped table-bordered">
              <thead className="table-light">
                <tr><th>Email</th><th>Contacts</th></tr>
              </thead>
              <tbody>
                {data.dupEmails.map(dup => (
                  <tr key={dup.email}>
                    <td>{dup.email}</td>
                    <td>
                      {dup.contacts.map((c, i) => (
                        <span key={i}>
                          {i > 0 && ', '}
                          {[c.firstName, c.lastName].filter(Boolean).join(' ')} (id: {c.id})
                        </span>
                      ))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <h3>Duplicate Names ({data.dupNames.length})</h3>
          {data.dupNames.length === 0 ? (
            <p className="text-muted">No duplicate names found.</p>
          ) : (
            <table className="table table-striped table-bordered">
              <thead className="table-light">
                <tr><th>Name</th><th>Contacts</th></tr>
              </thead>
              <tbody>
                {data.dupNames.map(dup => (
                  <tr key={`${dup.firstName}-${dup.lastName}`}>
                    <td>{dup.firstName} {dup.lastName}</td>
                    <td>
                      {dup.contacts.map((c, i) => (
                        <span key={i}>
                          {i > 0 && ', '}
                          {c.email || 'no email'} (id: {c.id})
                        </span>
                      ))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </ReportLayout>
  );
}
