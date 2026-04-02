import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, useWatch } from 'react-hook-form';
import apiClient from '../../api/client';
import type { DataResource, Licence } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

interface RightsFormValues {
  citation: string;
  rights: string;
  licenceID: string;        // stores "<licenseType>|<licenseVersion>"
  permissionsDocument: string;
  permissionsDocumentType: string;
  riskAssessment: boolean;
  filed: boolean;
  downloadLimit: number | '';
}

interface AppConfigPartial {
  permissionsDocumentTypes?: string[];
}

/** Encode a Licence as a single select value */
function encodeKey(lic: Licence) {
  return `${lic.acronym}|${lic.licenceVersion}`;
}

export default function RightsForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [saveError, setSaveError] = useState<string | null>(null);

  const { data: resource, isLoading: resourceLoading } = useQuery({
    queryKey: ['dataResource', id],
    queryFn: async () => {
      const { data } = await apiClient.get<DataResource>(`/dataResource/${id}`);
      return data;
    },
    enabled: !!id,
  });

  const { data: licences = [] } = useQuery({
    queryKey: ['licences'],
    queryFn: async () => {
      const { data } = await apiClient.get<Licence[]>('/licence');
      return data;
    },
  });

  const { data: appConfig } = useQuery({
    queryKey: ['config'],
    queryFn: async () => {
      const { data } = await apiClient.get<AppConfigPartial>('/config');
      return data;
    },
  });

  const permissionsDocumentTypes = appConfig?.permissionsDocumentTypes ?? [
    '',
    'Email',
    'Data Provider Agreement',
    'Web Page',
    'Other',
  ];

  // Find the matching licence for the current resource
  const currentLicenceKey = resource && licences.length > 0
    ? (licences.find(
        (l) => l.acronym === resource.licenseType && l.licenceVersion === resource.licenseVersion,
      )
        ? `${resource.licenseType}|${resource.licenseVersion}`
        : '')
    : '';

  const { register, handleSubmit, control } = useForm<RightsFormValues>({
    values: {
      citation: resource?.citation ?? '',
      rights: resource?.rights ?? '',
      licenceID: currentLicenceKey,
      permissionsDocument: resource?.permissionsDocument ?? '',
      permissionsDocumentType: resource?.permissionsDocumentType ?? '',
      riskAssessment: resource?.riskAssessment ?? false,
      filed: resource?.filed ?? false,
      downloadLimit: resource?.downloadLimit ?? '',
    },
  });

  // Watch permissionsDocumentType to conditionally show filed/riskAssessment
  const permDocType = useWatch({ control, name: 'permissionsDocumentType' });
  const isDPA = permDocType === 'Data Provider Agreement';

  const mutation = useMutation({
    mutationFn: async (data: RightsFormValues) => {
      // Decode licenceID back to licenseType + licenseVersion
      let licenseType: string | undefined;
      let licenseVersion: string | undefined;
      if (data.licenceID) {
        const lic = licences.find((l) => encodeKey(l) === data.licenceID);
        if (lic) {
          licenseType = lic.acronym;
          licenseVersion = lic.licenceVersion;
        }
      }
      const payload: Partial<DataResource> = {
        citation: data.citation || undefined,
        rights: data.rights || undefined,
        licenseType,
        licenseVersion,
        permissionsDocument: data.permissionsDocument || undefined,
        permissionsDocumentType: data.permissionsDocumentType || undefined,
        // Always include riskAssessment and filed — cleared to false when not DPA
        riskAssessment: data.permissionsDocumentType === 'Data Provider Agreement' ? data.riskAssessment : false,
        filed: data.permissionsDocumentType === 'Data Provider Agreement' ? data.filed : false,
        downloadLimit: data.downloadLimit === '' ? undefined : Number(data.downloadLimit),
      };
      await apiClient.put(`/dataResource/${id}`, payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataResource', id] });
      navigate(`/dataResource/show/${id}`);
    },
    onError: (err: Error) => setSaveError(err.message),
  });

  if (resourceLoading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (!resource) {
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
          current="Rights"
        />
        <h1>Rights &amp; Licensing - {resource.name}</h1>

        {saveError && <div className="alert alert-danger">{saveError}</div>}

        <form onSubmit={handleSubmit((data) => mutation.mutate(data))}>
          {/* Citation */}
          <div className="mb-3">
            <label htmlFor="citation" className="form-label">Citation</label>
            <textarea
              id="citation"
              className="form-control"
              rows={3}
              {...register('citation')}
            />
          </div>

          {/* Rights */}
          <div className="mb-3">
            <label htmlFor="rights" className="form-label">Rights</label>
            <textarea
              id="rights"
              className="form-control"
              rows={3}
              {...register('rights')}
            />
          </div>

          {/* Licence */}
          <div className="mb-3">
            <label htmlFor="licenceID" className="form-label">License Type</label>
            <select id="licenceID" className="form-select" {...register('licenceID')}>
              <option value="">--- Choose licence ---</option>
              {licences.map((lic) => (
                <option key={lic.id} value={encodeKey(lic)}>
                  {lic.name} ({lic.acronym} {lic.licenceVersion})
                </option>
              ))}
            </select>
          </div>

          {/* Permissions document */}
          <div className="mb-3">
            <label htmlFor="permissionsDocument" className="form-label">Permissions Document</label>
            <input
              type="text"
              id="permissionsDocument"
              className="form-control"
              {...register('permissionsDocument')}
            />
          </div>

          {/* Permissions document type */}
          <div className="mb-3">
            <label htmlFor="permissionsDocumentType" className="form-label">
              Permissions Document Type
            </label>
            <select
              id="permissionsDocumentType"
              className="form-select"
              {...register('permissionsDocumentType')}
            >
              {permissionsDocumentTypes.map((t) => (
                <option key={t} value={t}>{t || '-- none --'}</option>
              ))}
            </select>
          </div>

          {/* Risk assessment and Filed — only shown for Data Provider Agreement */}
          {isDPA && (
            <>
              <div className="form-check mb-3">
                <input
                  type="checkbox"
                  id="riskAssessment"
                  className="form-check-input"
                  {...register('riskAssessment')}
                />
                <label htmlFor="riskAssessment" className="form-check-label">
                  Risk assessment completed
                </label>
              </div>

              <div className="form-check mb-3">
                <input
                  type="checkbox"
                  id="filed"
                  className="form-check-input"
                  {...register('filed')}
                />
                <label htmlFor="filed" className="form-check-label">
                  Agreement filed
                </label>
              </div>
            </>
          )}

          {/* Download limit */}
          <div className="mb-3">
            <label htmlFor="downloadLimit" className="form-label">Download Limit</label>
            <input
              type="number"
              id="downloadLimit"
              className="form-control"
              min={0}
              {...register('downloadLimit')}
            />
            <div className="form-text text-muted">
              Maximum number of records that can be downloaded. Leave empty for no limit.
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
        </form>
      </div>
    </ProtectedRoute>
  );
}
