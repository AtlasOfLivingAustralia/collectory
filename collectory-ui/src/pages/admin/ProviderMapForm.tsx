import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { getInstitutions } from '../../api/endpoints/institutions';
import { getCollections } from '../../api/endpoints/collections';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface ProviderMapFormData {
  institutionUid: string;
  collectionUid: string;
  institutionCodes: string;
  collectionCodes: string;
  warning: string;
  exact: boolean;
  matchAnyCollectionCode: boolean;
}

export default function ProviderMapForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isEdit = !!id;
  const mapId = Number(id);

  const { register, handleSubmit, reset, formState: { isSubmitting } } = useForm<ProviderMapFormData>({
    defaultValues: { exact: false, matchAnyCollectionCode: false },
  });

  const { data: institutions = [] } = useQuery({
    queryKey: ['institutions'],
    queryFn: getInstitutions,
  });

  const { data: collections = [] } = useQuery({
    queryKey: ['collections'],
    queryFn: getCollections,
  });

  const { data: providerMap } = useQuery({
    queryKey: ['providerMap', mapId],
    queryFn: async () => {
      const { data } = await apiClient.get(`/providerMap/${mapId}`);
      return data;
    },
    enabled: isEdit && !isNaN(mapId),
  });

  useEffect(() => {
    if (providerMap) {
      reset({
        institutionUid: providerMap.institution?.uid ?? '',
        collectionUid: providerMap.collection?.uid ?? '',
        institutionCodes: providerMap.institutionCodes ?? '',
        collectionCodes: providerMap.collectionCodes ?? '',
        warning: providerMap.warning ?? '',
        exact: providerMap.exact ?? false,
        matchAnyCollectionCode: providerMap.matchAnyCollectionCode ?? false,
      });
    }
  }, [providerMap, reset]);

  const saveMutation = useMutation({
    mutationFn: async (data: ProviderMapFormData) => {
      const payload = {
        institution: data.institutionUid ? { uid: data.institutionUid } : null,
        collection: data.collectionUid ? { uid: data.collectionUid } : null,
        institutionCodes: data.institutionCodes,
        collectionCodes: data.collectionCodes,
        warning: data.warning,
        exact: data.exact,
        matchAnyCollectionCode: data.matchAnyCollectionCode,
      };
      if (isEdit) {
        const res = await apiClient.put(`/providerMap/${mapId}`, payload);
        return res.data;
      } else {
        const res = await apiClient.post('/providerMap', payload);
        return res.data;
      }
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['providerMaps'] });
      queryClient.invalidateQueries({ queryKey: ['providerMap', mapId] });
      navigate(`/providerMap/show/${saved.id ?? id}`);
    },
  });

  const onSubmit = (data: ProviderMapFormData) => {
    saveMutation.mutate(data);
  };

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[{ url: '/providerMap/list', label: 'Provider Maps' }]}
          current={isEdit ? 'Edit Provider Map' : 'New Provider Map'}
        />
        <h1>{isEdit ? 'Edit Provider Map' : 'New Provider Map'}</h1>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="card mb-3">
            <div className="card-body">
              <div className="mb-3">
                <label className="form-label">Institution</label>
                <select
                  className="form-select"
                  {...register('institutionUid')}
                  disabled={isEdit}
                >
                  <option value="">-- None --</option>
                  {institutions.map((inst) => (
                    <option key={inst.uid} value={inst.uid}>{inst.name}</option>
                  ))}
                </select>
              </div>

              <div className="mb-3">
                <label className="form-label">Collection</label>
                <select
                  className="form-select"
                  {...register('collectionUid')}
                  disabled={isEdit}
                >
                  <option value="">-- None --</option>
                  {collections.map((coll) => (
                    <option key={coll.uid} value={coll.uid}>{coll.name}</option>
                  ))}
                </select>
              </div>

              <div className="mb-3">
                <label className="form-label">Institution Codes</label>
                <input
                  type="text"
                  className="form-control"
                  {...register('institutionCodes')}
                />
              </div>

              <div className="mb-3">
                <label className="form-label">Collection Codes</label>
                <input
                  type="text"
                  className="form-control"
                  {...register('collectionCodes')}
                />
              </div>

              <div className="mb-3">
                <label className="form-label">Warning</label>
                <input
                  type="text"
                  className="form-control"
                  {...register('warning')}
                />
              </div>

              <div className="form-check mb-3">
                <input
                  type="checkbox"
                  className="form-check-input"
                  id="exact"
                  {...register('exact')}
                />
                <label className="form-check-label" htmlFor="exact">
                  Exact
                </label>
              </div>

              <div className="form-check mb-3">
                <input
                  type="checkbox"
                  className="form-check-input"
                  id="matchAnyCollectionCode"
                  {...register('matchAnyCollectionCode')}
                />
                <label className="form-check-label" htmlFor="matchAnyCollectionCode">
                  Match Any Collection Code
                </label>
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
