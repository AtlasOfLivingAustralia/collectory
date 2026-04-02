import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface LicenceFormData {
  name: string;
  acronym: string;
  licenceVersion: string;
  url: string;
  imageUrl: string;
  version?: number;
}

export default function LicenceForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isEdit = !!id;
  const licenceId = Number(id);

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<LicenceFormData>();

  const { data: licence } = useQuery({
    queryKey: ['licence', licenceId],
    queryFn: async () => {
      const { data } = await apiClient.get(`/licence/${licenceId}`);
      return data;
    },
    enabled: isEdit && !isNaN(licenceId),
  });

  useEffect(() => {
    if (licence) {
      reset({
        name: licence.name ?? '',
        acronym: licence.acronym ?? '',
        licenceVersion: licence.licenceVersion ?? '',
        url: licence.url ?? '',
        imageUrl: licence.imageUrl ?? '',
        version: licence.version,
      });
    }
  }, [licence, reset]);

  const saveMutation = useMutation({
    mutationFn: async (data: LicenceFormData) => {
      if (isEdit) {
        const res = await apiClient.put(`/licence/${licenceId}`, data);
        return res.data;
      } else {
        const res = await apiClient.post('/licence', data);
        return res.data;
      }
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['licences'] });
      queryClient.invalidateQueries({ queryKey: ['licence', licenceId] });
      navigate(`/licence/show/${saved.id ?? id}`);
    },
  });

  const onSubmit = (data: LicenceFormData) => {
    saveMutation.mutate(data);
  };

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[{ url: '/licence/list', label: 'Licences' }]}
          current={isEdit ? 'Edit Licence' : 'New Licence'}
        />
        <h1>{isEdit ? 'Edit Licence' : 'New Licence'}</h1>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="card mb-3">
            <div className="card-body">
              <div className="mb-3">
                <label className="form-label">Name *</label>
                <input
                  type="text"
                  className={`form-control ${errors.name ? 'is-invalid' : ''}`}
                  {...register('name', { required: 'Name is required' })}
                />
                {errors.name && <div className="invalid-feedback">{errors.name.message}</div>}
              </div>

              <div className="mb-3">
                <label className="form-label">Acronym *</label>
                <input
                  type="text"
                  className={`form-control ${errors.acronym ? 'is-invalid' : ''}`}
                  {...register('acronym', { required: 'Acronym is required' })}
                />
                {errors.acronym && <div className="invalid-feedback">{errors.acronym.message}</div>}
              </div>

              <div className="mb-3">
                <label className="form-label">Version *</label>
                <input
                  type="text"
                  className={`form-control ${errors.licenceVersion ? 'is-invalid' : ''}`}
                  {...register('licenceVersion', { required: 'Version is required' })}
                />
                {errors.licenceVersion && <div className="invalid-feedback">{errors.licenceVersion.message}</div>}
              </div>

              <div className="mb-3">
                <label className="form-label">URL *</label>
                <input
                  type="url"
                  className={`form-control ${errors.url ? 'is-invalid' : ''}`}
                  {...register('url', { required: 'URL is required' })}
                />
                {errors.url && <div className="invalid-feedback">{errors.url.message}</div>}
              </div>

              <div className="mb-3">
                <label className="form-label">Image URL *</label>
                <input
                  type="url"
                  className={`form-control ${errors.imageUrl ? 'is-invalid' : ''}`}
                  {...register('imageUrl', { required: 'Image URL is required' })}
                />
                {errors.imageUrl && <div className="invalid-feedback">{errors.imageUrl.message}</div>}
              </div>
            </div>
          </div>

          {saveMutation.isError && (
            <div className="alert alert-danger">An error occurred while saving.</div>
          )}

          <div className="d-flex gap-2">
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              <i className="fa fa-save me-1" /> Save
            </button>
            <button type="button" className="btn btn-outline-secondary" onClick={() => navigate(-1)}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </ProtectedRoute>
  );
}
