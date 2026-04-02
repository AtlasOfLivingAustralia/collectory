import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import apiClient from '../../api/client';
import type { ProviderGroup } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

const ENTITY_CONFIG: Record<string, { apiPath: string; singular: string }> = {
  collection: { apiPath: 'collection', singular: 'Collection' },
  institution: { apiPath: 'institution', singular: 'Institution' },
  dataResource: { apiPath: 'dataResource', singular: 'Data Resource' },
  dataProvider: { apiPath: 'dataProvider', singular: 'Data Provider' },
  dataHub: { apiPath: 'dataHub', singular: 'Data Hub' },
};

interface FormValues {
  range: string;
}

function parseRange(json?: string): string {
  if (!json) return '';
  try {
    const obj = JSON.parse(json) as Record<string, unknown>;
    const range = obj.range;
    if (Array.isArray(range)) {
      return range.join(', ');
    }
    return '';
  } catch {
    return '';
  }
}

function buildTaxonomyHints(existingJson: string | undefined, rangeStr: string): string {
  let existing: Record<string, unknown> = {};
  if (existingJson) {
    try {
      existing = JSON.parse(existingJson) as Record<string, unknown>;
    } catch {
      // ignore parse errors
    }
  }
  const rangeArray = rangeStr
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
  return JSON.stringify({ ...existing, range: rangeArray });
}

export default function TaxonomicRangeEditor() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [saveError, setSaveError] = useState<string | null>(null);

  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;

  const { data: entity, isLoading, error } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderGroup & { version?: number }>(
        `/${entityType}/${id}`,
      );
      return data;
    },
    enabled: !!entityType && !!id,
  });

  const { register, handleSubmit } = useForm<FormValues>({
    values: { range: parseRange(entity?.taxonomyHints) },
  });

  const mutation = useMutation({
    mutationFn: async (data: FormValues) => {
      const taxonomyHints = buildTaxonomyHints(entity?.taxonomyHints, data.range);
      await apiClient.put(`/${entityType}/${id}`, {
        taxonomyHints,
        version: entity?.version,
      });
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

  if (error || !entity || !config) {
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
          current="Taxonomic Range"
        />
        <h1>Taxonomic Range - {entity.name}</h1>
        <p className="text-muted">
          Enter a comma-separated list of higher taxa that describe the taxonomic range of this{' '}
          {config.singular.toLowerCase()}.
        </p>

        {saveError && <div className="alert alert-danger">{saveError}</div>}

        <form onSubmit={handleSubmit((data) => mutation.mutate(data))}>
          <div className="mb-3">
            <label htmlFor="range" className="form-label">
              Taxonomic Range
            </label>
            <input
              type="text"
              id="range"
              className="form-control"
              placeholder="e.g. Mammals, Birds, Reptiles"
              {...register('range')}
            />
            <div className="form-text text-muted">
              Separate multiple taxa with commas.
            </div>
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
