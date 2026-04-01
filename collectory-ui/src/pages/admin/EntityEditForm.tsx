import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { createEntity, updateEntity } from '../../api/endpoints/mutations';
import { getInstitutions } from '../../api/endpoints/institutions';
import { getDataProviders } from '../../api/endpoints/dataProviders';
import type { ProviderGroup, Institution, DataProvider, DataResource, Collection } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

/** Maps URL entityType to API path and labels */
const ENTITY_CONFIG: Record<string, { apiPath: string; singular: string; plural: string }> = {
  collection: { apiPath: 'collection', singular: 'Collection', plural: 'Collections' },
  institution: { apiPath: 'institution', singular: 'Institution', plural: 'Institutions' },
  dataResource: { apiPath: 'dataResource', singular: 'Data Resource', plural: 'Data Resources' },
  dataProvider: { apiPath: 'dataProvider', singular: 'Data Provider', plural: 'Data Providers' },
  dataHub: { apiPath: 'dataHub', singular: 'Data Hub', plural: 'Data Hubs' },
};

const NETWORK_MEMBERSHIPS = [
  { key: 'CHAH', label: 'CHAH' },
  { key: 'CHAEC', label: 'CHAEC' },
  { key: 'CHAFC', label: 'CHAFC' },
  { key: 'CHACM', label: 'CHACM' },
];

const RESOURCE_TYPES = [
  'records',
  'events',
  'species-list',
  'website',
  'document',
  'publications',
];

interface FormValues {
  name: string;
  acronym: string;
  guid: string;
  websiteUrl: string;
  isALAPartner: boolean;
  networkMembership: string[];
  notes: string;
  keywords: string;
  gbifRegistryKey: string;
  version?: number;
  // DataResource-specific fields
  isPrivate?: boolean;
  gbifDoi?: string;
  resourceType?: string;
  dataProviderId?: string;
  institutionId?: string;
  // DataProvider / Institution specific
  gbifCountryToAttribute?: string;
}

function parseNetworkMembership(value?: string): string[] {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed as string[];
  } catch {
    // not JSON
  }
  return [];
}

export default function EntityEditForm() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;
  const isCreateMode = !id;

  const [serverError, setServerError] = useState<string | null>(null);

  const { data: entity, isLoading: entityLoading } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      if (!config || !id) return null;
      const { data } = await apiClient.get<ProviderGroup & { version?: number }>(`/${config.apiPath}/${id}`);
      return data;
    },
    enabled: !isCreateMode && !!config && !!id,
  });

  // Fetch institutions list for dataResource and collection entity types
  const needsInstitutions = entityType === 'dataResource' || entityType === 'collection';
  const { data: institutionsList } = useQuery({
    queryKey: ['institutions', 'list'],
    queryFn: getInstitutions,
    enabled: needsInstitutions,
  });

  // Fetch data providers list for dataResource entity type
  const needsDataProviders = entityType === 'dataResource';
  const { data: dataProvidersList } = useQuery({
    queryKey: ['dataProviders', 'list'],
    queryFn: getDataProviders,
    enabled: needsDataProviders,
  });

  // Sort institutions and data providers by name
  const sortedInstitutions = institutionsList
    ? [...institutionsList].sort((a, b) => a.name.localeCompare(b.name))
    : [];
  const sortedDataProviders = dataProvidersList
    ? [...dataProvidersList].sort((a, b) => a.name.localeCompare(b.name))
    : [];

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: {
      name: '',
      acronym: '',
      guid: '',
      websiteUrl: '',
      isALAPartner: false,
      networkMembership: [],
      notes: '',
      keywords: '',
      gbifRegistryKey: '',
      isPrivate: false,
      gbifDoi: '',
      resourceType: '',
      dataProviderId: '',
      institutionId: '',
      gbifCountryToAttribute: '',
    },
  });

  // Populate form when entity loads
  useEffect(() => {
    if (entity) {
      const typedEntity = entity as ProviderGroup & Record<string, unknown>;
      reset({
        name: entity.name ?? '',
        acronym: entity.acronym ?? '',
        guid: entity.guid ?? '',
        websiteUrl: entity.websiteUrl ?? '',
        isALAPartner: entity.isALAPartner ?? false,
        networkMembership: parseNetworkMembership(
          Array.isArray(entity.networkMembership) ? JSON.stringify(entity.networkMembership) : entity.networkMembership
        ),
        notes: entity.notes ?? '',
        keywords: entity.keywords ?? '',
        gbifRegistryKey: entity.gbifRegistryKey ?? '',
        version: (entity as ProviderGroup & { version?: number }).version,
        // DataResource-specific
        isPrivate: (typedEntity as unknown as DataResource & { isPrivate?: boolean }).isPrivate ?? false,
        gbifDoi: (typedEntity as unknown as DataResource).gbifDoi ?? '',
        resourceType: (typedEntity as unknown as DataResource).resourceType ?? '',
        dataProviderId: (typedEntity as unknown as DataResource).dataProvider?.uid ?? '',
        institutionId: entityType === 'collection'
          ? (typedEntity as unknown as Collection).institution?.uid ?? ''
          : (typedEntity as unknown as DataResource).institution?.uid ?? '',
        // DataProvider / Institution
        gbifCountryToAttribute: (typedEntity as unknown as DataProvider | Institution).gbifCountryToAttribute ?? '',
      });
    }
  }, [entity, reset, entityType]);

  const createMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) => createEntity(config!.apiPath, data),
    onSuccess: (result: ProviderGroup) => {
      queryClient.invalidateQueries({ queryKey: [entityType] });
      navigate(`/${entityType}/show/${result.uid}`);
    },
    onError: (err: Error) => {
      setServerError(err.message || 'Failed to create entity.');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) => updateEntity(config!.apiPath, id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType] });
      queryClient.invalidateQueries({ queryKey: [entityType, id] });
      navigate(`/${entityType}/show/${id}`);
    },
    onError: (err: Error) => {
      setServerError(err.message || 'Failed to update entity.');
    },
  });

  function onSubmit(values: FormValues) {
    setServerError(null);
    const payload: Record<string, unknown> = {
      name: values.name,
      acronym: values.acronym || undefined,
      websiteUrl: values.websiteUrl || undefined,
      isALAPartner: values.isALAPartner,
      networkMembership: values.networkMembership.length > 0
        ? JSON.stringify(values.networkMembership)
        : undefined,
      notes: values.notes || undefined,
      keywords: values.keywords || undefined,
    };

    // DataResource-specific fields
    if (entityType === 'dataResource') {
      payload.isPrivate = values.isPrivate ?? false;
      payload.gbifDoi = values.gbifDoi || undefined;
      payload.resourceType = values.resourceType || undefined;
      if (values.dataProviderId) {
        payload.dataProvider = { id: values.dataProviderId };
      }
      if (values.institutionId) {
        payload.institution = { id: values.institutionId };
      }
    }

    // Collection-specific fields
    if (entityType === 'collection') {
      if (values.institutionId) {
        payload.institution = { id: values.institutionId };
      }
    }

    // DataProvider / Institution specific
    if (entityType === 'dataProvider' || entityType === 'institution') {
      payload.gbifCountryToAttribute = values.gbifCountryToAttribute || undefined;
    }

    if (isCreateMode) {
      if (values.guid) payload.guid = values.guid;
      createMutation.mutate(payload);
    } else {
      if (values.version !== undefined) payload.version = values.version;
      updateMutation.mutate(payload);
    }
  }

  const networkMembershipValues = watch('networkMembership');

  function handleNetworkToggle(key: string) {
    const current = networkMembershipValues ?? [];
    if (current.includes(key)) {
      setValue('networkMembership', current.filter((k) => k !== key), { shouldDirty: true });
    } else {
      setValue('networkMembership', [...current, key], { shouldDirty: true });
    }
  }

  if (!config) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Unknown entity type: {entityType}</div>
      </div>
    );
  }

  if (!isCreateMode && entityLoading) {
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

  const cancelPath = isCreateMode
    ? `/${entityType}/list`
    : `/${entityType}/show/${id}`;

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item">
              <Link to={`/${entityType}/list`}>{config.plural}</Link>
            </li>
            {!isCreateMode && entity && (
              <li className="breadcrumb-item">
                <Link to={`/${entityType}/show/${id}`}>{entity.uid || id}</Link>
              </li>
            )}
            <li className="breadcrumb-item active">{isCreateMode ? `New ${config.singular}` : 'Edit'}</li>
          </ol>
        </nav>
        <h1>{isCreateMode ? `New ${config.singular}` : `Edit ${config.singular}`}</h1>
        {!isCreateMode && entity && (
          <p className="text-muted">
            <code>{entity.uid}</code>
          </p>
        )}

        {serverError && (
          <div className="alert alert-danger">{serverError}</div>
        )}

        <form onSubmit={handleSubmit(onSubmit)}>
          {/* Name */}
          <div className="mb-3">
            <label htmlFor="name" className="form-label">Name *</label>
            <input
              id="name"
              type="text"
              className={`form-control ${errors.name ? 'is-invalid' : ''}`}
              {...register('name', { required: 'Name is required' })}
            />
            {errors.name && <div className="invalid-feedback">{errors.name.message}</div>}
          </div>

          {/* Acronym */}
          <div className="mb-3">
            <label htmlFor="acronym" className="form-label">Acronym</label>
            <input
              id="acronym"
              type="text"
              className="form-control"
              {...register('acronym')}
            />
          </div>

          {/* GUID */}
          <div className="mb-3">
            <label htmlFor="guid" className="form-label">GUID</label>
            <input
              id="guid"
              type="text"
              className="form-control"
              readOnly={!isCreateMode}
              {...register('guid')}
            />
            {!isCreateMode && (
              <div className="form-text text-muted">GUID cannot be changed after creation.</div>
            )}
          </div>

          {/* Website URL */}
          <div className="mb-3">
            <label htmlFor="websiteUrl" className="form-label">Website URL</label>
            <input
              id="websiteUrl"
              type="url"
              className="form-control"
              placeholder="https://"
              {...register('websiteUrl')}
            />
          </div>

          {/* ALA Partner — TODO: should be admin-only (ROLE_ADMIN), but leaving visible until client-side role check is implemented */}
          <div className="mb-3 form-check">
            <input
              id="isALAPartner"
              type="checkbox"
              className="form-check-input"
              {...register('isALAPartner')}
            />
            <label htmlFor="isALAPartner" className="form-check-label">ALA Partner</label>
          </div>

          {/* --- DataResource-specific fields --- */}
          {entityType === 'dataResource' && (
            <>
              {/* isPrivate */}
              <div className="mb-3 form-check">
                <input
                  id="isPrivate"
                  type="checkbox"
                  className="form-check-input"
                  {...register('isPrivate')}
                />
                <label htmlFor="isPrivate" className="form-check-label">Private</label>
              </div>

              {/* GBIF DOI */}
              <div className="mb-3">
                <label htmlFor="gbifDoi" className="form-label">GBIF DOI</label>
                <input
                  id="gbifDoi"
                  type="text"
                  className="form-control"
                  maxLength={45}
                  {...register('gbifDoi')}
                />
              </div>

              {/* Resource Type */}
              <div className="mb-3">
                <label htmlFor="resourceType" className="form-label">Resource Type</label>
                <select
                  id="resourceType"
                  className="form-select"
                  {...register('resourceType')}
                >
                  <option value="">-- Select a resource type --</option>
                  {RESOURCE_TYPES.map((rt) => (
                    <option key={rt} value={rt}>{rt}</option>
                  ))}
                </select>
              </div>

              {/* Data Provider */}
              <div className="mb-3">
                <label htmlFor="dataProviderId" className="form-label">Data Provider</label>
                <select
                  id="dataProviderId"
                  className="form-select"
                  {...register('dataProviderId')}
                >
                  <option value="">-- Select a data provider --</option>
                  {sortedDataProviders.map((dp) => (
                    <option key={dp.uid} value={dp.uid}>{dp.name}</option>
                  ))}
                </select>
              </div>

              {/* Institution (for DataResource) */}
              <div className="mb-3">
                <label htmlFor="institutionId" className="form-label">Institution</label>
                <select
                  id="institutionId"
                  className="form-select"
                  {...register('institutionId')}
                >
                  <option value="">-- Select an institution --</option>
                  {sortedInstitutions.map((inst) => (
                    <option key={inst.uid} value={inst.uid}>{inst.name}</option>
                  ))}
                </select>
              </div>
            </>
          )}

          {/* --- Collection-specific fields --- */}
          {entityType === 'collection' && (
            <>
              {/* Institution (for Collection) */}
              <div className="mb-3">
                <label htmlFor="institutionId" className="form-label">Institution</label>
                <select
                  id="institutionId"
                  className="form-select"
                  {...register('institutionId')}
                >
                  <option value="">-- Select an institution --</option>
                  {sortedInstitutions.map((inst) => (
                    <option key={inst.uid} value={inst.uid}>{inst.name}</option>
                  ))}
                </select>
              </div>
            </>
          )}

          {/* --- DataProvider / Institution specific fields --- */}
          {(entityType === 'dataProvider' || entityType === 'institution') && (
            <>
              {/* GBIF Country to Attribute */}
              <div className="mb-3">
                <label htmlFor="gbifCountryToAttribute" className="form-label">
                  GBIF Country to Attribute
                </label>
                <input
                  id="gbifCountryToAttribute"
                  type="text"
                  className="form-control"
                  placeholder="e.g. AU, NZ, ZZZ for international organisations"
                  {...register('gbifCountryToAttribute')}
                />
                <div className="form-text text-muted">
                  ISO 3166-1 alpha-2 country code for GBIF attribution. Use ZZZ for international organisations.
                </div>
              </div>
            </>
          )}

          {/* Network Membership */}
          <div className="mb-3">
            <label className="form-label">Network Membership</label>
            <div>
              {NETWORK_MEMBERSHIPS.map(({ key, label }) => (
                <div key={key} className="form-check form-check-inline">
                  <input
                    id={`network-${key}`}
                    type="checkbox"
                    className="form-check-input"
                    checked={networkMembershipValues?.includes(key) ?? false}
                    onChange={() => handleNetworkToggle(key)}
                  />
                  <label htmlFor={`network-${key}`} className="form-check-label">{label}</label>
                </div>
              ))}
            </div>
          </div>

          {/* Notes */}
          <div className="mb-3">
            <label htmlFor="notes" className="form-label">Notes</label>
            <textarea
              id="notes"
              className="form-control"
              rows={4}
              {...register('notes')}
            />
          </div>

          {/* Keywords */}
          <div className="mb-3">
            <label htmlFor="keywords" className="form-label">Keywords</label>
            <textarea
              id="keywords"
              className="form-control"
              rows={2}
              {...register('keywords')}
            />
            <div className="form-text text-muted">Comma-separated list of keywords.</div>
          </div>

          {/* GBIF Registry Key (readonly) */}
          {!isCreateMode && entity?.gbifRegistryKey && (
            <div className="mb-3">
              <label htmlFor="gbifRegistryKey" className="form-label">GBIF Registry Key</label>
              <input
                id="gbifRegistryKey"
                type="text"
                className="form-control"
                readOnly
                {...register('gbifRegistryKey')}
              />
            </div>
          )}

          {/* Buttons */}
          <div className="d-flex gap-2 mt-4">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={isSubmitting || createMutation.isPending || updateMutation.isPending}
            >
              {(isSubmitting || createMutation.isPending || updateMutation.isPending) && (
                <span className="spinner-border spinner-border-sm me-1" role="status" />
              )}
              Save
            </button>
            <Link to={cancelPath} className="btn btn-outline-dark">Cancel</Link>
          </div>
        </form>
      </div>
    </ProtectedRoute>
  );
}
