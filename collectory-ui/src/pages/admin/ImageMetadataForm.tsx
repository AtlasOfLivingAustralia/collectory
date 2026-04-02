import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import apiClient from '../../api/client';
import type { DataResource } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface ImageMetadataValues {
  title: string;
  creator: string;
  rights: string;
  rightsHolder: string;
  licence: string;
  version?: number;
}

export default function ImageMetadataForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: resource, isLoading, error } = useQuery({
    queryKey: ['dataResource', id],
    queryFn: async () => {
      const { data } = await apiClient.get<DataResource & { version?: number; imageMetadata?: string }>(
        `/dataResource/${id}`,
      );
      return data;
    },
    enabled: !!id,
  });

  const { register, handleSubmit, setValue } = useForm<ImageMetadataValues>({
    defaultValues: {
      title: '',
      creator: '',
      rights: '',
      rightsHolder: '',
      licence: '',
    },
  });

  useEffect(() => {
    if (!resource) return;

    // Parse imageMetadata JSON if present
    if (resource.imageMetadata) {
      try {
        const meta = typeof resource.imageMetadata === 'string'
          ? JSON.parse(resource.imageMetadata)
          : resource.imageMetadata;
        setValue('title', meta.title ?? '');
        setValue('creator', meta.creator ?? '');
        setValue('rights', meta.rights ?? '');
        setValue('rightsHolder', meta.rightsHolder ?? '');
        setValue('licence', meta.licence ?? '');
      } catch {
        // If parsing fails, leave defaults
      }
    }

    setValue('version', resource.version);
  }, [resource, setValue]);

  const mutation = useMutation({
    mutationFn: async (data: ImageMetadataValues) => {
      const imageMetadata = JSON.stringify({
        title: data.title,
        creator: data.creator,
        rights: data.rights,
        rightsHolder: data.rightsHolder,
        licence: data.licence,
      });

      const payload: Record<string, unknown> = { imageMetadata };
      if (data.version !== undefined) payload.version = data.version;

      await apiClient.put(`/dataResource/${id}`, payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataResource', id] });
      navigate(`/dataResource/show/${id}`);
    },
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
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[
            { url: '/dataResource/list', label: 'Data Resources' },
            { url: `/dataResource/show/${id}`, label: resource.name || id || '' },
          ]}
          current="Image Metadata"
        />
        <h1>Image Metadata - {resource.name}</h1>
        <p className="text-muted">
          Default metadata values applied to images associated with this data resource.
        </p>

        {mutation.isError && (
          <div className="alert alert-danger">
            {mutation.error instanceof Error ? mutation.error.message : 'Failed to save.'}
          </div>
        )}

        <form onSubmit={handleSubmit((data) => mutation.mutate(data))}>
          <div className="mb-3">
            <label htmlFor="im-title" className="form-label">Title</label>
            <input
              type="text"
              id="im-title"
              className="form-control"
              {...register('title')}
            />
          </div>

          <div className="mb-3">
            <label htmlFor="im-creator" className="form-label">Creator</label>
            <input
              type="text"
              id="im-creator"
              className="form-control"
              {...register('creator')}
            />
          </div>

          <div className="mb-3">
            <label htmlFor="im-rights" className="form-label">Rights</label>
            <input
              type="text"
              id="im-rights"
              className="form-control"
              {...register('rights')}
            />
          </div>

          <div className="mb-3">
            <label htmlFor="im-rightsHolder" className="form-label">Rights Holder</label>
            <input
              type="text"
              id="im-rightsHolder"
              className="form-control"
              {...register('rightsHolder')}
            />
          </div>

          <div className="mb-3">
            <label htmlFor="im-licence" className="form-label">Licence</label>
            <input
              type="text"
              id="im-licence"
              className="form-control"
              {...register('licence')}
            />
          </div>

          <div className="d-flex gap-2">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={mutation.isPending}
            >
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
        </form>
      </div>
    </ProtectedRoute>
  );
}
