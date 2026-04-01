import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import apiClient from '../../api/client';
import type { DataResource } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface FormValues {
  gbifRegistryKey: string;
  gbifCountryToAttribute: string;
}

export default function GbifEditForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [saveError, setSaveError] = useState<string | null>(null);

  const { data: resource, isLoading, error } = useQuery({
    queryKey: ['dataResource', id],
    queryFn: async () => {
      const { data } = await apiClient.get<DataResource & { version?: number; gbifCountryToAttribute?: string }>(
        `/dataResource/${id}`,
      );
      return data;
    },
    enabled: !!id,
  });

  const { register, handleSubmit } = useForm<FormValues>({
    values: {
      gbifRegistryKey: resource?.gbifRegistryKey ?? '',
      gbifCountryToAttribute: resource?.gbifCountryToAttribute ?? '',
    },
  });

  const mutation = useMutation({
    mutationFn: async (data: FormValues) => {
      await apiClient.put(`/dataResource/${id}`, {
        gbifRegistryKey: data.gbifRegistryKey || null,
        gbifCountryToAttribute: data.gbifCountryToAttribute || null,
        version: resource?.version,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataResource', id] });
      navigate(`/dataResource/show/${id}`);
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

  if (error || !resource) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load data resource.</div>
      </div>
    );
  }

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><Link to="/dataResource/list">Data Resources</Link></li>
            <li className="breadcrumb-item"><Link to={`/dataResource/show/${id}`}>{id}</Link></li>
            <li className="breadcrumb-item active">GBIF Settings</li>
          </ol>
        </nav>
        <h1>GBIF Registration - {resource.name}</h1>
        <p className="text-muted">
          Edit the GBIF registration details for this data resource.
        </p>

        {saveError && <div className="alert alert-danger">{saveError}</div>}

        <form onSubmit={handleSubmit((data) => mutation.mutate(data))}>
          <div className="row">
            <div className="col-md-6">
              <div className="mb-3">
                <label htmlFor="gbifRegistryKey" className="form-label">
                  GBIF Registry Key
                </label>
                <input
                  type="text"
                  id="gbifRegistryKey"
                  className="form-control"
                  placeholder="e.g. 8a863029-f435-446a-821e-275f4f641165"
                  {...register('gbifRegistryKey')}
                />
                <div className="form-text text-muted">
                  The UUID assigned by GBIF when this dataset was registered.
                </div>
              </div>

              <div className="mb-3">
                <label htmlFor="gbifCountryToAttribute" className="form-label">
                  Country to Attribute
                </label>
                <input
                  type="text"
                  id="gbifCountryToAttribute"
                  className="form-control"
                  placeholder="e.g. AU (or ZZZ for international)"
                  {...register('gbifCountryToAttribute')}
                />
                <div className="form-text text-muted">
                  ISO 3166-1 alpha-2 country code (e.g. AU, US) or ZZZ for international datasets.
                </div>
              </div>

              <div className="d-flex gap-2">
                <button type="submit" className="btn btn-primary" disabled={mutation.isPending}>
                  {mutation.isPending ? 'Saving...' : 'Save'}
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
          </div>
        </form>
      </div>
    </ProtectedRoute>
  );
}
