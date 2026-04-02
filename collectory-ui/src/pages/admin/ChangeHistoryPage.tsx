import { useParams, Link } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { ProviderGroup } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface AuditEvent {
  id: number;
  lastUpdated: string;
  actor: string;
  eventName: 'UPDATE' | 'INSERT' | 'DELETE';
  propertyName?: string;
  oldValue?: string;
  newValue?: string;
  className?: string;
  persistedObjectId?: number;
  uri?: string;
}

const ENTITY_CONFIG: Record<string, { apiPath: string; singular: string }> = {
  collection: { apiPath: 'collection', singular: 'Collection' },
  institution: { apiPath: 'institution', singular: 'Institution' },
  dataResource: { apiPath: 'dataResource', singular: 'Data Resource' },
  dataProvider: { apiPath: 'dataProvider', singular: 'Data Provider' },
  dataHub: { apiPath: 'dataHub', singular: 'Data Hub' },
};

function formatDate(dateStr: string): string {
  try {
    return new Date(dateStr).toLocaleString();
  } catch {
    return dateStr;
  }
}

function isContactFor(className?: string): boolean {
  return !!className && className.endsWith('ContactFor');
}

function renderEvent(event: AuditEvent, entityType: string) {
  const date = formatDate(event.lastUpdated);
  const actor = event.actor || 'Unknown';

  if (event.eventName === 'UPDATE' && event.propertyName) {
    return (
      <div className="card mb-3" key={event.id}>
        <div className="card-body">
          <p>
            On <strong>{date}</strong>, <em>{actor}</em> changed{' '}
            <code>{event.propertyName}</code>
          </p>
          <table className="table table-sm">
            <thead>
              <tr>
                <th>New Value</th>
                <th>Old Value</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="text-break">{event.newValue ?? <span className="text-muted">(empty)</span>}</td>
                <td className="text-break">{event.oldValue ?? <span className="text-muted">(empty)</span>}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  if (event.eventName === 'INSERT' && isContactFor(event.className)) {
    return (
      <div className="card mb-3" key={event.id}>
        <div className="card-body">
          <p>
            On <strong>{date}</strong>, <em>{actor}</em> added a contact
          </p>
        </div>
      </div>
    );
  }

  if (event.eventName === 'DELETE' && isContactFor(event.className)) {
    return (
      <div className="card mb-3" key={event.id}>
        <div className="card-body">
          <p>
            On <strong>{date}</strong>, <em>{actor}</em> removed a contact
          </p>
        </div>
      </div>
    );
  }

  if (event.eventName === 'INSERT') {
    const config = ENTITY_CONFIG[entityType];
    const label = config ? config.singular.toLowerCase() : entityType;
    return (
      <div className="card mb-3" key={event.id}>
        <div className="card-body">
          <p>
            On <strong>{date}</strong>, <em>{actor}</em> created this {label}
          </p>
        </div>
      </div>
    );
  }

  // Fallback for any other event
  return (
    <div className="card mb-3" key={event.id}>
      <div className="card-body">
        <p>
          On <strong>{date}</strong>, <em>{actor}</em> performed{' '}
          <code>{event.eventName}</code>
          {event.propertyName && (
            <> on <code>{event.propertyName}</code></>
          )}
        </p>
      </div>
    </div>
  );
}

export default function ChangeHistoryPage() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();

  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;

  const { data: entity, isLoading: entityLoading } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderGroup>(`/${entityType}/${id}`);
      return data;
    },
    enabled: !!entityType && !!id,
  });

  const {
    data: changes,
    isLoading: changesLoading,
    error: changesError,
  } = useQuery({
    queryKey: [entityType, id, 'changes'],
    queryFn: async () => {
      const { data } = await apiClient.get<AuditEvent[]>(`/${entityType}/${id}/changes`);
      return data;
    },
    enabled: !!entityType && !!id,
  });

  const isLoading = entityLoading || changesLoading;

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (changesError) {
    const is404 =
      changesError instanceof Error &&
      changesError.message.includes('404');
    return (
      <ProtectedRoute>
        <div className="container mt-4">
          {is404 ? (
            <div className="alert alert-info">
              Change history is not available for this entity type.
            </div>
          ) : (
            <div className="alert alert-danger">
              Failed to load change history: {changesError.message}
            </div>
          )}
          <Link
            to={`/${entityType}/show/${id}`}
            className="btn btn-outline-dark"
          >
            <i className="fa fa-arrow-left me-1" />
            Return to {entity?.name ?? config?.singular ?? 'entity'}
          </Link>
        </div>
      </ProtectedRoute>
    );
  }

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[
            { url: `/${entityType}/list`, label: ENTITY_LABELS[entityType ?? ''] ?? entityType ?? '' },
            { url: `/${entityType}/show/${id}`, label: (entity?.name as string) || id || '' },
          ]}
          current="Change History"
        />
        <h1>Change History - {entity?.name ?? id}</h1>

        {changes && changes.length > 0 ? (
          changes.map((event) => renderEvent(event, entityType!))
        ) : (
          <div className="alert alert-info">No changes recorded for this entity.</div>
        )}

        <Link
          to={`/${entityType}/show/${id}`}
          className="btn btn-outline-dark mt-3"
        >
          <i className="fa fa-arrow-left me-1" />
          Return to {entity?.name ?? config?.singular ?? 'entity'}
        </Link>
      </div>
    </ProtectedRoute>
  );
}
