import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import Pagination from '../../components/common/Pagination';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

const PAGE_SIZE = 100;

interface AuditLogEvent {
  id: number;
  actor: string;
  uri: string;
  className: string;
  persistedObjectId: string;
  lastUpdated: string;
}

export default function AuditLogList() {
  const [currentPage, setCurrentPage] = useState(1);

  const offset = (currentPage - 1) * PAGE_SIZE;

  const { data, isLoading, error } = useQuery({
    queryKey: ['auditLogEvents', offset],
    queryFn: async () => {
      const { data } = await apiClient.get<{ changes: AuditLogEvent[]; totalElements: number }>('/reports/changes', { params: { offset } });
      return data;
    },
  });

  const events = data?.changes ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item active">Audit Log Events</li>
          </ol>
        </nav>
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h1>Audit Log Events</h1>
        </div>

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
          </div>
        )}

        {error && <div className="alert alert-danger">Failed to load audit log events.</div>}

        {!isLoading && !error && (
          <>
            <p className="text-muted">
              Showing {events.length} of {totalElements} events
            </p>

            <table className="table table-striped table-hover">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Actor</th>
                  <th>URI</th>
                  <th>Class</th>
                  <th>Object ID</th>
                  <th>Last Updated</th>
                </tr>
              </thead>
              <tbody>
                {events.map((e) => (
                  <tr key={e.id}>
                    <td>
                      <Link to={`/auditLogEvent/show/${e.id}`}>{e.id}</Link>
                    </td>
                    <td>{e.actor || '-'}</td>
                    <td>{e.uri || '-'}</td>
                    <td>{e.className || '-'}</td>
                    <td>{e.persistedObjectId || '-'}</td>
                    <td>{e.lastUpdated || '-'}</td>
                  </tr>
                ))}
                {events.length === 0 && (
                  <tr>
                    <td colSpan={6} className="text-center text-muted py-4">
                      No audit log events found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>

            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              onPageChange={setCurrentPage}
            />
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
