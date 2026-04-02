import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { DataResource, ProviderGroup } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface ConsumerEntry {
  uid: string;
  name: string;
  entityType: 'institution' | 'collection';
}

export default function ConsumersEditor() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState('');
  const [searchType, setSearchType] = useState<'institution' | 'collection'>('institution');
  const [serverError, setServerError] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);

  // Local list of selected consumers (edited in-memory, saved on "Save")
  const [selectedConsumers, setSelectedConsumers] = useState<ConsumerEntry[]>([]);

  const { data: resource } = useQuery({
    queryKey: ['dataResource', id],
    queryFn: async () => {
      const { data } = await apiClient.get<DataResource>(`/dataResource/${id}`);
      return data;
    },
    enabled: !!id,
  });

  // Fetch current consumers from the API (read-only seed for local state)
  const { data: initialConsumers = [], isLoading, error } = useQuery({
    queryKey: ['dataResource', id, 'consumers'],
    queryFn: async () => {
      const { data } = await apiClient.get<ConsumerEntry[]>(`/dataResource/${id}/consumers`);
      return data;
    },
    enabled: !!id,
  });

  // Seed local state when server data arrives
  useEffect(() => {
    if (initialConsumers.length > 0 || !isLoading) {
      setSelectedConsumers(initialConsumers);
      setDirty(false);
    }
  }, [initialConsumers, isLoading]);

  // Search for entities to add as consumers
  const { data: searchResults = [] } = useQuery({
    queryKey: ['search', searchType, searchQuery],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderGroup[]>(`/${searchType}`);
      const q = searchQuery.toLowerCase();
      return data
        .filter((e) => e.name.toLowerCase().includes(q))
        .slice(0, 20);
    },
    enabled: searchQuery.length >= 2,
  });

  // Filter out already-selected consumers from search results
  const filteredResults = useMemo(() => {
    const selectedUids = new Set(selectedConsumers.map((c) => c.uid));
    return searchResults.filter((e) => !selectedUids.has(e.uid));
  }, [searchResults, selectedConsumers]);

  // Save: PUT all selected consumer UIDs as a comma-separated string
  const saveMutation = useMutation({
    mutationFn: async () => {
      const consumerUids = selectedConsumers.map((c) => c.uid).join(',');
      await apiClient.put(`/dataResource/${id}`, { consumers: consumerUids });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataResource', id, 'consumers'] });
      queryClient.invalidateQueries({ queryKey: ['dataResource', id] });
      setDirty(false);
      setServerError(null);
      navigate(`/dataResource/show/${id}`);
    },
    onError: (err: Error) => setServerError(err.message),
  });

  function addConsumer(entity: ProviderGroup) {
    setSelectedConsumers((prev) => [
      ...prev,
      { uid: entity.uid, name: entity.name, entityType: searchType },
    ]);
    setSearchQuery('');
    setDirty(true);
  }

  function removeConsumer(uid: string) {
    setSelectedConsumers((prev) => prev.filter((c) => c.uid !== uid));
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
        <div className="alert alert-danger">Failed to load consumers.</div>
      </div>
    );
  }

  const institutions = selectedConsumers.filter((c) => c.entityType === 'institution');
  const collections = selectedConsumers.filter((c) => c.entityType === 'collection');

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[
            { url: '/dataResource/list', label: 'Data Resources' },
            { url: `/dataResource/show/${id}`, label: resource?.name || id || '' },
          ]}
          current="Consumers"
        />
        <h1>Data Consumers - {resource?.name ?? id}</h1>
        <p className="text-muted">
          Manage which institutions and collections consume this data resource.
        </p>

        {serverError && <div className="alert alert-danger">{serverError}</div>}
        {dirty && (
          <div className="alert alert-warning">
            You have unsaved changes. Click <strong>Save</strong> to persist them.
          </div>
        )}

        {/* Institutions */}
        <h3>Institutions</h3>
        {institutions.length === 0 ? (
          <p className="text-muted">No consuming institutions.</p>
        ) : (
          <ul className="list-group mb-3">
            {institutions.map((c) => (
              <li key={c.uid} className="list-group-item d-flex justify-content-between align-items-center">
                <Link to={`/institution/show/${c.uid}`}>{c.name}</Link>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-danger"
                  onClick={() => {
                    if (window.confirm(`Remove ${c.name}?`)) {
                      removeConsumer(c.uid);
                    }
                  }}
                >
                  <i className="fa fa-times" />
                </button>
              </li>
            ))}
          </ul>
        )}

        {/* Collections */}
        <h3>Collections</h3>
        {collections.length === 0 ? (
          <p className="text-muted">No consuming collections.</p>
        ) : (
          <ul className="list-group mb-3">
            {collections.map((c) => (
              <li key={c.uid} className="list-group-item d-flex justify-content-between align-items-center">
                <Link to={`/collection/show/${c.uid}`}>{c.name}</Link>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-danger"
                  onClick={() => {
                    if (window.confirm(`Remove ${c.name}?`)) {
                      removeConsumer(c.uid);
                    }
                  }}
                >
                  <i className="fa fa-times" />
                </button>
              </li>
            ))}
          </ul>
        )}

        {/* Add consumer */}
        <h3>Add Consumer</h3>
        <div className="card card-body mb-3">
          <div className="row mb-2">
            <div className="col-auto">
              <select
                className="form-select"
                value={searchType}
                onChange={(e) => {
                  setSearchType(e.target.value as 'institution' | 'collection');
                  setSearchQuery('');
                }}
              >
                <option value="institution">Institution</option>
                <option value="collection">Collection</option>
              </select>
            </div>
            <div className="col">
              <input
                type="text"
                className="form-control"
                placeholder={`Search ${searchType}s...`}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          {filteredResults.length > 0 && (
            <ul className="list-group">
              {filteredResults.map((entity) => (
                <li
                  key={entity.uid}
                  className="list-group-item d-flex justify-content-between align-items-center"
                >
                  <span>
                    {entity.name} <code className="ms-1">{entity.uid}</code>
                  </span>
                  <button
                    type="button"
                    className="btn btn-sm btn-primary"
                    onClick={() => addConsumer(entity)}
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
            onClick={() => navigate(`/dataResource/show/${id}`)}
          >
            Cancel
          </button>
        </div>
      </div>
    </ProtectedRoute>
  );
}
