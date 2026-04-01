import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { DataResource, ProviderGroup } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface ProviderEntry {
  uid: string;
  name: string;
}

export default function ProvidersEditor() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);

  // Local list of selected providers (edited in-memory, saved on "Save")
  const [selectedProviders, setSelectedProviders] = useState<ProviderEntry[]>([]);

  // Fetch the parent entity (collection or institution)
  const { data: entity } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderGroup & { providers?: string; version?: number }>(`/${entityType}/${id}`);
      return data;
    },
    enabled: !!entityType && !!id,
  });

  // Fetch the providers sub-resource to get current provider list
  const { data: initialProviders = [], isLoading, error } = useQuery({
    queryKey: [entityType, id, 'providers'],
    queryFn: async () => {
      // Try the sub-resource endpoint first; fall back to parsing entity.providers
      try {
        const { data } = await apiClient.get<ProviderEntry[]>(`/${entityType}/${id}/providers`);
        return data;
      } catch {
        // If no dedicated endpoint, parse from the entity's providers field
        if (entity?.providers) {
          const uids = entity.providers.split(',').filter(Boolean);
          // Resolve names via lookup
          const entries: ProviderEntry[] = await Promise.all(
            uids.map(async (uid) => {
              try {
                const { data } = await apiClient.get<{ name: string }>(`/lookup/summary/${uid}`);
                return { uid, name: data.name ?? uid };
              } catch {
                return { uid, name: uid };
              }
            }),
          );
          return entries;
        }
        return [];
      }
    },
    enabled: !!entityType && !!id,
  });

  // Seed local state when server data arrives
  useEffect(() => {
    if (initialProviders.length > 0 || !isLoading) {
      setSelectedProviders(initialProviders);
      setDirty(false);
    }
  }, [initialProviders, isLoading]);

  // Search for data resources (records type) to add as providers
  const { data: searchResults = [] } = useQuery({
    queryKey: ['search', 'dataResource', searchQuery],
    queryFn: async () => {
      const { data } = await apiClient.get<DataResource[]>('/dataResource');
      const q = searchQuery.toLowerCase();
      return data
        .filter((r) => r.resourceType === 'records')
        .filter((r) => r.name.toLowerCase().includes(q))
        .slice(0, 20);
    },
    enabled: searchQuery.length >= 2,
  });

  // Filter out already-selected providers from search results
  const filteredResults = useMemo(() => {
    const selectedUids = new Set(selectedProviders.map((p) => p.uid));
    return searchResults.filter((r) => !selectedUids.has(r.uid));
  }, [searchResults, selectedProviders]);

  // Save: PUT all selected provider UIDs as a comma-separated string
  const saveMutation = useMutation({
    mutationFn: async () => {
      const providerUids = selectedProviders.map((p) => p.uid).join(',');
      await apiClient.put(`/${entityType}/${id}`, {
        providers: providerUids,
        version: entity?.version,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id, 'providers'] });
      queryClient.invalidateQueries({ queryKey: [entityType, id] });
      setDirty(false);
      setServerError(null);
      navigate(`/${entityType}/show/${id}`);
    },
    onError: (err: Error) => setServerError(err.message),
  });

  function addProvider(resource: DataResource) {
    setSelectedProviders((prev) => [
      ...prev,
      { uid: resource.uid, name: resource.name },
    ]);
    setSearchQuery('');
    setDirty(true);
  }

  function removeProvider(uid: string) {
    setSelectedProviders((prev) => prev.filter((p) => p.uid !== uid));
    setDirty(true);
  }

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
        <div className="alert alert-danger">Failed to load record providers.</div>
      </div>
    );
  }

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item">
              <Link to={`/${entityType}/list`}>{ENTITY_LABELS[entityType ?? ''] ?? entityType}</Link>
            </li>
            <li className="breadcrumb-item">
              <Link to={`/${entityType}/show/${id}`}>{id}</Link>
            </li>
            <li className="breadcrumb-item active">Providers</li>
          </ol>
        </nav>
        <h1>Record Providers - {entity?.name ?? id}</h1>
        <p className="text-muted">
          Manage which data resources (records) are providers for this {entityType}.
        </p>

        {serverError && <div className="alert alert-danger">{serverError}</div>}
        {dirty && (
          <div className="alert alert-warning">
            You have unsaved changes. Click <strong>Save</strong> to persist them.
          </div>
        )}

        {/* Current providers */}
        <h3>Current Providers</h3>
        {selectedProviders.length === 0 ? (
          <p className="text-muted">No record providers.</p>
        ) : (
          <ul className="list-group mb-3">
            {selectedProviders.map((p) => (
              <li key={p.uid} className="list-group-item d-flex justify-content-between align-items-center">
                <Link to={`/dataResource/show/${p.uid}`}>
                  {p.name} <code className="ms-1">{p.uid}</code>
                </Link>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-danger"
                  onClick={() => {
                    if (window.confirm(`Remove ${p.name}?`)) {
                      removeProvider(p.uid);
                    }
                  }}
                >
                  <i className="fa fa-times" />
                </button>
              </li>
            ))}
          </ul>
        )}

        {/* Search and add */}
        <h3>Add Provider</h3>
        <div className="card card-body mb-3">
          <div className="mb-2">
            <input
              type="text"
              className="form-control"
              placeholder="Search data resources (records)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          {filteredResults.length > 0 && (
            <ul className="list-group">
              {filteredResults.map((resource) => (
                <li
                  key={resource.uid}
                  className="list-group-item d-flex justify-content-between align-items-center"
                >
                  <span>
                    {resource.name} <code className="ms-1">{resource.uid}</code>
                  </span>
                  <button
                    type="button"
                    className="btn btn-sm btn-primary"
                    onClick={() => addProvider(resource)}
                  >
                    <i className="fa fa-plus me-1" /> Add
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="d-flex gap-2 mt-4">
          <button
            type="button"
            className="btn btn-primary"
            disabled={saveMutation.isPending || !dirty}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending && (
              <span className="spinner-border spinner-border-sm me-1" role="status" />
            )}
            Save
          </button>
          <button
            type="button"
            className="btn btn-outline-dark"
            onClick={() => navigate(`/${entityType}/show/${id}`)}
          >
            Cancel
          </button>
        </div>
      </div>
    </ProtectedRoute>
  );
}
