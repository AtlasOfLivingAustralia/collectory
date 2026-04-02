import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { ExternalIdentifier, ProviderGroup } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface NewIdentifier {
  source: string;
  identifier: string;
  uri: string;
}

const EMPTY_ID: NewIdentifier = { source: '', identifier: '', uri: '' };

export default function ExternalIdEditor() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [newId, setNewId] = useState<NewIdentifier>(EMPTY_ID);
  const [opError, setOpError] = useState<string | null>(null);

  const { data: entity } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderGroup>(`/${entityType}/${id}`);
      return data;
    },
    enabled: !!entityType && !!id,
  });

  const { data: identifiers = [], isLoading, error } = useQuery({
    queryKey: [entityType, id, 'externalIdentifiers'],
    queryFn: async () => {
      const { data } = await apiClient.get<ExternalIdentifier[]>(
        `/${entityType}/${id}/externalIdentifiers`,
      );
      return data;
    },
    enabled: !!entityType && !!id,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: [entityType, id, 'externalIdentifiers'] });
  };

  const addMutation = useMutation({
    mutationFn: async (payload: NewIdentifier) => {
      await apiClient.post(`/${entityType}/${id}/externalIdentifiers`, payload);
    },
    onSuccess: () => {
      invalidate();
      setAdding(false);
      setNewId(EMPTY_ID);
      setOpError(null);
    },
    onError: (err: Error) => setOpError(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: async (extId: number) => {
      await apiClient.delete(`/${entityType}/${id}/externalIdentifiers/${extId}`);
    },
    onSuccess: () => {
      invalidate();
      setOpError(null);
    },
    onError: (err: Error) => setOpError(err.message),
  });

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load external identifiers.</div>
      </div>
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
          current="External Identifiers"
        />
        <h1>External Identifiers - {entity?.name ?? id}</h1>

        {opError && <div className="alert alert-danger">{opError}</div>}

        <table className="table table-striped">
          <thead>
            <tr>
              <th>Source</th>
              <th>Identifier</th>
              <th>URI</th>
              <th style={{ width: '80px' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {identifiers.map((ext) => (
              <tr key={ext.id}>
                <td>{ext.source}</td>
                <td>{ext.identifier}</td>
                <td>
                  {ext.uri ? (
                    <a href={ext.uri} target="_blank" rel="noopener noreferrer">
                      {ext.uri}
                    </a>
                  ) : (
                    <span className="text-muted">-</span>
                  )}
                </td>
                <td>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-danger"
                    disabled={deleteMutation.isPending}
                    onClick={() => {
                      if (window.confirm('Remove this external identifier?')) {
                        deleteMutation.mutate(ext.id);
                      }
                    }}
                    title="Delete"
                  >
                    <i className="fa fa-trash" />
                  </button>
                </td>
              </tr>
            ))}

            {adding && (
              <tr>
                <td>
                  <input
                    type="text"
                    className="form-control form-control-sm"
                    placeholder="Source"
                    value={newId.source}
                    onChange={(e) => setNewId({ ...newId, source: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    type="text"
                    className="form-control form-control-sm"
                    placeholder="Identifier"
                    value={newId.identifier}
                    onChange={(e) => setNewId({ ...newId, identifier: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    type="text"
                    className="form-control form-control-sm"
                    placeholder="URI (optional)"
                    value={newId.uri}
                    onChange={(e) => setNewId({ ...newId, uri: e.target.value })}
                  />
                </td>
                <td>
                  <div className="d-flex gap-1">
                    <button
                      type="button"
                      className="btn btn-sm btn-primary"
                      disabled={!newId.source.trim() || !newId.identifier.trim() || addMutation.isPending}
                      onClick={() => addMutation.mutate(newId)}
                      title="Save"
                    >
                      <i className="fa fa-check" />
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-dark"
                      onClick={() => {
                        setAdding(false);
                        setNewId(EMPTY_ID);
                      }}
                      title="Cancel"
                    >
                      <i className="fa fa-times" />
                    </button>
                  </div>
                </td>
              </tr>
            )}

            {identifiers.length === 0 && !adding && (
              <tr>
                <td colSpan={4} className="text-center text-muted py-3">
                  No external identifiers defined.
                </td>
              </tr>
            )}
          </tbody>
        </table>

        <div className="d-flex gap-2">
          {!adding && (
            <button
              type="button"
              className="btn btn-outline-dark"
              onClick={() => setAdding(true)}
            >
              <i className="fa fa-plus me-1" /> Add Identifier
            </button>
          )}
          <button
            type="button"
            className="btn btn-outline-dark"
            onClick={() => navigate(`/${entityType}/show/${id}`)}
          >
            Back
          </button>
        </div>
      </div>
    </ProtectedRoute>
  );
}
