import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface ProviderCodeFormData {
  code: string;
  version?: number;
}

export default function ProviderCodeForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isEdit = !!id;
  const codeId = Number(id);

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<ProviderCodeFormData>();

  const { data: providerCode } = useQuery({
    queryKey: ['providerCode', codeId],
    queryFn: async () => {
      const { data } = await apiClient.get(`/providerCode/${codeId}`);
      return data;
    },
    enabled: isEdit && !isNaN(codeId),
  });

  useEffect(() => {
    if (providerCode) {
      reset({ code: providerCode.code ?? '', version: providerCode.version });
    }
  }, [providerCode, reset]);

  const saveMutation = useMutation({
    mutationFn: async (data: ProviderCodeFormData) => {
      if (isEdit) {
        const res = await apiClient.put(`/providerCode/${codeId}`, data);
        return res.data;
      } else {
        const res = await apiClient.post('/providerCode', data);
        return res.data;
      }
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['providerCodes'] });
      queryClient.invalidateQueries({ queryKey: ['providerCode', codeId] });
      navigate(`/providerCode/show/${saved.id ?? id}`);
    },
  });

  const onSubmit = (data: ProviderCodeFormData) => {
    saveMutation.mutate(data);
  };

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[{ url: '/providerCode/list', label: 'Provider Codes' }]}
          current={isEdit ? 'Edit Provider Code' : 'New Provider Code'}
        />
        <h1>{isEdit ? 'Edit Provider Code' : 'New Provider Code'}</h1>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="card mb-3">
            <div className="card-body">
              <div className="mb-3">
                <label className="form-label">Code *</label>
                <input
                  type="text"
                  className={`form-control ${errors.code ? 'is-invalid' : ''}`}
                  {...register('code', { required: 'Code is required' })}
                />
                {errors.code && <div className="invalid-feedback">{errors.code.message}</div>}
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
