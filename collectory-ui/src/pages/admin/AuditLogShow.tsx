import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface AuditLogEvent {
  id: number;
  actor: string;
  uri: string;
  className: string;
  persistedObjectId: string;
  eventName: string;
  propertyName: string;
  oldValue: string;
  newValue: string;
  dateCreated: string;
  lastUpdated: string;
}

export default function AuditLogShow() {
  const { id } = useParams<{ id: string }>();
  const eventId = Number(id);

  const { data: event, isLoading, error } = useQuery({
    queryKey: ['auditLogEvent', eventId],
    queryFn: async () => {
      const { data } = await apiClient.get<AuditLogEvent>(`/auditLogEvent/${eventId}`);
      return data;
    },
    enabled: !isNaN(eventId),
  });

  if (isLoading) {
    return (
      <div className="container mt-4 d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (error || !event) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load audit log event. ID: {id}</div>
      </div>
    );
  }

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item">
              <Link to="/auditLogEvent/list">Audit Log Events</Link>
            </li>
            <li className="breadcrumb-item active">Event #{event.id}</li>
          </ol>
        </nav>

        <h1>Audit Log Event #{event.id}</h1>

        <div className="card mb-3">
          <div className="card-header">
            <strong>Event Details</strong>
          </div>
          <div className="card-body">
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">ID</div>
              <div className="col-sm-8">{event.id}</div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">Actor</div>
              <div className="col-sm-8">{event.actor || '-'}</div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">URI</div>
              <div className="col-sm-8">{event.uri || '-'}</div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">Class Name</div>
              <div className="col-sm-8">{event.className || '-'}</div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">Persisted Object ID</div>
              <div className="col-sm-8">{event.persistedObjectId || '-'}</div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">Event Name</div>
              <div className="col-sm-8">{event.eventName || '-'}</div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">Property Name</div>
              <div className="col-sm-8">{event.propertyName || '-'}</div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">Old Value</div>
              <div className="col-sm-8">
                <pre className="mb-0" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                  {event.oldValue || '-'}
                </pre>
              </div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">New Value</div>
              <div className="col-sm-8">
                <pre className="mb-0" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                  {event.newValue || '-'}
                </pre>
              </div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">Date Created</div>
              <div className="col-sm-8">{event.dateCreated || '-'}</div>
            </div>
            <div className="row mb-1">
              <div className="col-sm-4 text-muted">Last Updated</div>
              <div className="col-sm-8">{event.lastUpdated || '-'}</div>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
