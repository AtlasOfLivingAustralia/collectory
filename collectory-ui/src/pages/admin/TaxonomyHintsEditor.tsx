import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, useFieldArray } from 'react-hook-form';
import apiClient from '../../api/client';
import type { ProviderGroup } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface HintRow {
  key: string;
  value: string;
}

interface FormValues {
  hints: HintRow[];
}

function parseHints(json?: string): HintRow[] {
  if (!json) return [];
  try {
    const obj = JSON.parse(json) as Record<string, string>;
    return Object.entries(obj).map(([key, value]) => ({ key, value }));
  } catch {
    return [];
  }
}

function serializeHints(hints: HintRow[]): string {
  const obj: Record<string, string> = {};
  for (const h of hints) {
    const k = h.key.trim();
    if (k) obj[k] = h.value;
  }
  return JSON.stringify(obj);
}

export default function TaxonomyHintsEditor() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [saveError, setSaveError] = useState<string | null>(null);

  const { data: entity, isLoading, error } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderGroup>(`/${entityType}/${id}`);
      return data;
    },
    enabled: !!entityType && !!id,
  });

  const { control, register, handleSubmit } = useForm<FormValues>({
    values: { hints: parseHints(entity?.taxonomyHints) },
  });

  const { fields, append, remove } = useFieldArray({ control, name: 'hints' });

  const mutation = useMutation({
    mutationFn: async (data: FormValues) => {
      const taxonomyHints = serializeHints(data.hints);
      await apiClient.put(`/${entityType}/${id}`, { taxonomyHints });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id] });
      navigate(`/${entityType}/show/${id}`);
    },
    onError: (err: Error) => setSaveError(err.message),
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

  if (error || !entity) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load entity.</div>
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
            { url: `/${entityType}/show/${id}`, label: entity.name || id || '' },
          ]}
          current="Taxonomy Hints"
        />
        <h1>Taxonomy Hints - {entity.name}</h1>
        <p className="text-muted">
          Taxonomy hints are used to guide the name matching process. Each hint is a key-value pair
          where the key is a higher taxon group and the value is a more specific taxon.
        </p>

        {saveError && <div className="alert alert-danger">{saveError}</div>}

        <form onSubmit={handleSubmit((data) => mutation.mutate(data))}>
          <table className="table">
            <thead>
              <tr>
                <th>Group (key)</th>
                <th>Taxon (value)</th>
                <th style={{ width: '80px' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {fields.map((field, index) => (
                <tr key={field.id}>
                  <td>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="e.g. Mammals"
                      {...register(`hints.${index}.key`)}
                    />
                  </td>
                  <td>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="e.g. Marsupials"
                      {...register(`hints.${index}.value`)}
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-danger"
                      onClick={() => remove(index)}
                      title="Remove"
                    >
                      <i className="fa fa-trash" />
                    </button>
                  </td>
                </tr>
              ))}
              {fields.length === 0 && (
                <tr>
                  <td colSpan={3} className="text-center text-muted py-3">
                    No taxonomy hints defined.
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          <div className="mb-3">
            <button
              type="button"
              className="btn btn-outline-dark"
              onClick={() => append({ key: '', value: '' })}
            >
              <i className="fa fa-plus me-1" /> Add Hint
            </button>
          </div>

          <div className="d-flex gap-2">
            <button type="submit" className="btn btn-primary" disabled={mutation.isPending}>
              {mutation.isPending ? 'Saving...' : 'Save'}
            </button>
            <button
              type="button"
              className="btn btn-outline-dark"
              onClick={() => navigate(`/${entityType}/show/${id}`)}
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </ProtectedRoute>
  );
}
