import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { Attribution, ProviderGroup } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

const ENTITY_CONFIG: Record<string, { apiPath: string; showPath: string }> = {
  collection: { apiPath: 'collection', showPath: '/public/showCollection' },
  institution: { apiPath: 'institution', showPath: '/public/showInstitution' },
  dataResource: { apiPath: 'dataResource', showPath: '/public/showDataResource' },
  dataProvider: { apiPath: 'dataProvider', showPath: '/public/showDataProvider' },
  dataHub: { apiPath: 'dataHub', showPath: '/public/showDataHub' },
};

/**
 * Parse the entity's attributions field.
 * It may be a JSON array of attribution objects/names, or undefined.
 */
function parseEntityAttributions(entity: ProviderGroup): string[] {
  if (!entity.attributions) return [];
  try {
    const parsed = JSON.parse(entity.attributions);
    if (Array.isArray(parsed)) {
      // Could be array of strings (names/uids) or array of objects
      return parsed.map((item: string | Attribution) =>
        typeof item === 'string' ? item : (item.uid ?? item.name ?? String(item.id)),
      );
    }
    return [];
  } catch {
    return [];
  }
}

export default function AttributionsEditor() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [saveError, setSaveError] = useState<string | null>(null);

  // Fetch entity data
  const { data: entity, isLoading: entityLoading } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      if (!config) return null;
      const { data } = await apiClient.get<ProviderGroup>(`/${config.apiPath}/${id}`);
      return data;
    },
    enabled: !!config && !!id,
  });

  // Fetch all available attributions
  const { data: allAttributions, isLoading: attributionsLoading, error: attributionsError } = useQuery({
    queryKey: ['attributions'],
    queryFn: async () => {
      const { data } = await apiClient.get<Attribution[]>('/attribution');
      return data;
    },
  });

  // Seed selected IDs from entity data
  useEffect(() => {
    if (entity) {
      setSelectedIds(new Set(parseEntityAttributions(entity)));
    }
  }, [entity]);

  const mutation = useMutation({
    mutationFn: async (selected: string[]) => {
      if (!config || !id) throw new Error('Missing config or id');
      // POST attribution flags as at1, at2, at3, etc. keyed by index
      // Each flag corresponds to whether the attribution at that index is selected
      const payload: Record<string, string | boolean> = {};

      if (allAttributions) {
        allAttributions.forEach((attr, i) => {
          const key = `at${i + 1}`;
          payload[key] = selected.includes(attr.uid) || selected.includes(attr.name) || selected.includes(String(attr.id));
        });
      }

      await apiClient.post(`/${config.apiPath}/${id}/attributions`, payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id] });
      if (config) {
        navigate(`${config.showPath}/${id}`);
      }
    },
    onError: (err: Error) => setSaveError(err.message),
  });

  function toggleAttribution(identifier: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(identifier)) {
        next.delete(identifier);
      } else {
        next.add(identifier);
      }
      return next;
    });
  }

  if (!config) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Unknown entity type: {entityType}</div>
      </div>
    );
  }

  const isLoading = entityLoading || attributionsLoading;

  if (isLoading) {
    return (
      <div className="container mt-4">
        <div className="d-flex justify-content-center p-5">
          <div className="spinner-border" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
        </div>
      </div>
    );
  }

  if (attributionsError) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load available attributions.</div>
      </div>
    );
  }

  const attributionList = allAttributions ?? [];

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[
            { url: `/${entityType}/list`, label: ENTITY_LABELS[entityType ?? ''] ?? entityType ?? '' },
            { url: `/${entityType}/show/${id}`, label: (entity?.name as string) || id || '' },
          ]}
          current="Attributions"
        />
        <h1>Attributions</h1>
        {entity?.name && (
          <p className="text-muted">{entity.name} (<code>{id}</code>)</p>
        )}
        <p className="text-muted">
          Select the attributions that apply to this entity.
        </p>

        {saveError && <div className="alert alert-danger">{saveError}</div>}

        <div className="card card-body mb-3">
          {attributionList.length === 0 && (
            <p className="text-muted mb-0">No attributions available.</p>
          )}
          {attributionList.map((attr) => {
            const identifier = attr.uid ?? attr.name;
            const isChecked = selectedIds.has(attr.uid) || selectedIds.has(attr.name) || selectedIds.has(String(attr.id));
            return (
              <div key={attr.id} className="form-check mb-2">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id={`attr-${attr.id}`}
                  checked={isChecked}
                  onChange={() => toggleAttribution(identifier)}
                />
                <label className="form-check-label" htmlFor={`attr-${attr.id}`}>
                  {attr.name}
                  {attr.url && (
                    <a href={attr.url} target="_blank" rel="noopener noreferrer" className="ms-2 small">
                      <i className="fa fa-external-link" />
                    </a>
                  )}
                </label>
              </div>
            );
          })}
        </div>

        <div className="d-flex gap-2">
          <button
            type="button"
            className="btn btn-primary"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate(Array.from(selectedIds))}
          >
            {mutation.isPending && (
              <span className="spinner-border spinner-border-sm me-1" role="status" />
            )}
            {mutation.isPending ? 'Saving...' : 'Save'}
          </button>
          <button
            type="button"
            className="btn btn-outline-dark"
            onClick={() => navigate(`${config.showPath}/${id}`)}
          >
            Cancel
          </button>
        </div>
      </div>
    </ProtectedRoute>
  );
}
