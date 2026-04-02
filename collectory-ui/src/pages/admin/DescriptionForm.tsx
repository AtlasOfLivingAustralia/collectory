import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useParams, useNavigate, Link } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { updateEntity } from '../../api/endpoints/mutations';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

const ENTITY_CONFIG: Record<string, { apiPath: string; singular: string; showPath: string }> = {
  collection: { apiPath: 'collection', singular: 'Collection', showPath: '/public/showCollection' },
  institution: { apiPath: 'institution', singular: 'Institution', showPath: '/public/showInstitution' },
  dataResource: { apiPath: 'dataResource', singular: 'Data Resource', showPath: '/public/showDataResource' },
  dataProvider: { apiPath: 'dataProvider', singular: 'Data Provider', showPath: '/public/showDataProvider' },
  dataHub: { apiPath: 'dataHub', singular: 'Data Hub', showPath: '/public/showDataHub' },
};

const COLLECTION_TYPES = [
  'preserved', 'living', 'tissue', 'DNA', 'fossils', 'cellcultures', 'seedbank', 'other',
];

const ACTIVE_OPTIONS = ['Active', 'Inactive', 'Partially active', 'Closed'];

const RESOURCE_TYPES = [
  'records', 'events', 'species-list', 'website', 'document', 'publications',
];

interface DescriptionFormValues {
  // Common
  pubShortDescription: string;
  pubDescription: string;
  techDescription: string;
  focus: string;
  // Collection-specific
  geographicDescription: string;
  states: string;
  kingdomCoverage: string;
  scientificNames: string;
  startDate: string;
  endDate: string;
  collectionType: string;
  active: string;
  numRecords: number | '';
  numRecordsDigitised: number | '';
  subCollections: string;
  // DataResource-specific
  resourceType: string;
  status: string;
  contentTypes: string;
  purpose: string;
  qualityControlDescription: string;
  methodStepDescription: string;
  dataCollectionProtocolName: string;
  dataCollectionProtocolDoc: string;
  suitableFor: string;
  northBoundingCoordinate: number | '';
  southBoundingCoordinate: number | '';
  eastBoundingCoordinate: number | '';
  westBoundingCoordinate: number | '';
  beginDate: string;
  drEndDate: string;
  lastChecked: string;
  dataCurrency: string;
  // DataHub-specific
  memberInstitutions: string;
  memberCollections: string;
  memberDataResources: string;
  // Optimistic locking
  version?: number;
}

export default function DescriptionForm() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;
  const [serverError, setServerError] = useState<string | null>(null);

  const { data: entity, isLoading } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      if (!config || !id) return null;
      const { data } = await apiClient.get<Record<string, unknown>>(`/${config.apiPath}/${id}`);
      return data;
    },
    enabled: !!config && !!id,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<DescriptionFormValues>();

  useEffect(() => {
    if (!entity) return;
    const e = entity as Record<string, unknown>;
    reset({
      pubShortDescription: (e.pubShortDescription as string) ?? '',
      pubDescription: (e.pubDescription as string) ?? '',
      techDescription: (e.techDescription as string) ?? '',
      focus: (e.focus as string) ?? '',
      geographicDescription: (e.geographicDescription as string) ?? '',
      states: (e.states as string) ?? '',
      kingdomCoverage: (e.kingdomCoverage as string) ?? '',
      scientificNames: (e.scientificNames as string) ?? '',
      startDate: (e.startDate as string) ?? '',
      endDate: (e.endDate as string) ?? '',
      collectionType: (e.collectionType as string) ?? '',
      active: (e.active as string) ?? '',
      numRecords: (e.numRecords as number) ?? '',
      numRecordsDigitised: (e.numRecordsDigitised as number) ?? '',
      subCollections: (e.subCollections as string) ?? '',
      resourceType: (e.resourceType as string) ?? '',
      status: (e.status as string) ?? '',
      contentTypes: (e.contentTypes as string) ?? '',
      purpose: (e.purpose as string) ?? '',
      qualityControlDescription: (e.qualityControlDescription as string) ?? '',
      methodStepDescription: (e.methodStepDescription as string) ?? '',
      dataCollectionProtocolName: (e.dataCollectionProtocolName as string) ?? '',
      dataCollectionProtocolDoc: (e.dataCollectionProtocolDoc as string) ?? '',
      suitableFor: (e.suitableFor as string) ?? '',
      northBoundingCoordinate: (e.northBoundingCoordinate as number) ?? '',
      southBoundingCoordinate: (e.southBoundingCoordinate as number) ?? '',
      eastBoundingCoordinate: (e.eastBoundingCoordinate as number) ?? '',
      westBoundingCoordinate: (e.westBoundingCoordinate as number) ?? '',
      beginDate: (e.beginDate as string) ?? '',
      drEndDate: (e.endDate as string) ?? '',
      lastChecked: (e.lastChecked as string) ?? '',
      dataCurrency: (e.dataCurrency as string) ?? '',
      memberInstitutions: (e.memberInstitutions as string) ?? '',
      memberCollections: (e.memberCollections as string) ?? '',
      memberDataResources: (e.memberDataResources as string) ?? '',
      version: e.version as number | undefined,
    });
  }, [entity, reset]);

  const mutation = useMutation({
    mutationFn: (data: Record<string, unknown>) => updateEntity(config!.apiPath, id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id] });
      navigate(`${config!.showPath}/${id}`);
    },
    onError: (err: Error) => {
      setServerError(err.message || 'Failed to save description.');
    },
  });

  function onSubmit(values: DescriptionFormValues) {
    setServerError(null);
    const payload: Record<string, unknown> = {
      pubShortDescription: values.pubShortDescription || undefined,
      pubDescription: values.pubDescription || undefined,
      techDescription: values.techDescription || undefined,
      focus: values.focus || undefined,
    };

    if (values.version !== undefined) payload.version = values.version;

    if (entityType === 'collection') {
      payload.geographicDescription = values.geographicDescription || undefined;
      payload.states = values.states || undefined;
      payload.kingdomCoverage = values.kingdomCoverage || undefined;
      payload.scientificNames = values.scientificNames || undefined;
      payload.startDate = values.startDate || undefined;
      payload.endDate = values.endDate || undefined;
      payload.collectionType = values.collectionType || undefined;
      payload.active = values.active || undefined;
      payload.numRecords = values.numRecords !== '' ? Number(values.numRecords) : undefined;
      payload.numRecordsDigitised = values.numRecordsDigitised !== '' ? Number(values.numRecordsDigitised) : undefined;
      payload.subCollections = values.subCollections || undefined;
    }

    if (entityType === 'dataResource') {
      payload.resourceType = values.resourceType || undefined;
      payload.status = values.status || undefined;
      payload.contentTypes = values.contentTypes || undefined;
      payload.geographicDescription = values.geographicDescription || undefined;
      payload.purpose = values.purpose || undefined;
      payload.qualityControlDescription = values.qualityControlDescription || undefined;
      payload.methodStepDescription = values.methodStepDescription || undefined;
      payload.dataCollectionProtocolName = values.dataCollectionProtocolName || undefined;
      payload.dataCollectionProtocolDoc = values.dataCollectionProtocolDoc || undefined;
      payload.suitableFor = values.suitableFor || undefined;
      payload.northBoundingCoordinate = values.northBoundingCoordinate !== '' ? Number(values.northBoundingCoordinate) : undefined;
      payload.southBoundingCoordinate = values.southBoundingCoordinate !== '' ? Number(values.southBoundingCoordinate) : undefined;
      payload.eastBoundingCoordinate = values.eastBoundingCoordinate !== '' ? Number(values.eastBoundingCoordinate) : undefined;
      payload.westBoundingCoordinate = values.westBoundingCoordinate !== '' ? Number(values.westBoundingCoordinate) : undefined;
      payload.beginDate = values.beginDate || undefined;
      payload.endDate = values.drEndDate || undefined;
      payload.lastChecked = values.lastChecked || undefined;
      payload.dataCurrency = values.dataCurrency || undefined;
    }

    if (entityType === 'dataHub') {
      payload.memberInstitutions = values.memberInstitutions || undefined;
      payload.memberCollections = values.memberCollections || undefined;
      payload.memberDataResources = values.memberDataResources || undefined;
    }

    mutation.mutate(payload);
  }

  if (!config) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Unknown entity type: {entityType}</div>
      </div>
    );
  }

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

  const entityName = (entity as Record<string, unknown> | null)?.name as string | undefined;

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[
            { url: `/${entityType}/list`, label: ENTITY_LABELS[entityType ?? ''] ?? entityType ?? '' },
            { url: `/${entityType}/show/${id}`, label: entityName || id || '' },
          ]}
          current="Description"
        />
        <h1>Description &amp; Details</h1>
        {entityName && (
          <p className="text-muted">{entityName} (<code>{id}</code>)</p>
        )}

        {serverError && <div className="alert alert-danger">{serverError}</div>}

        <form onSubmit={handleSubmit(onSubmit)}>
          {/* Common fields */}
          <fieldset className="mb-4">
            <legend>Description</legend>

            <div className="mb-3">
              <label htmlFor="pubShortDescription" className="form-label">Short Description (max 100 chars)</label>
              <input
                id="pubShortDescription"
                type="text"
                className="form-control"
                maxLength={100}
                {...register('pubShortDescription')}
              />
            </div>

            <div className="mb-3">
              <label htmlFor="pubDescription" className="form-label">Public Description</label>
              <textarea id="pubDescription" className="form-control" rows={6} {...register('pubDescription')} />
            </div>

            <div className="mb-3">
              <label htmlFor="techDescription" className="form-label">Technical Description</label>
              <textarea id="techDescription" className="form-control" rows={6} {...register('techDescription')} />
            </div>

            <div className="mb-3">
              <label htmlFor="focus" className="form-label">Focus</label>
              <textarea id="focus" className="form-control" rows={3} {...register('focus')} />
            </div>
          </fieldset>

          {/* Collection-specific */}
          {entityType === 'collection' && (
            <fieldset className="mb-4">
              <legend>Collection Details</legend>

              <div className="mb-3">
                <label htmlFor="geographicDescription" className="form-label">Geographic Description</label>
                <textarea id="geographicDescription" className="form-control" rows={3} {...register('geographicDescription')} />
              </div>

              <div className="mb-3">
                <label htmlFor="states" className="form-label">States</label>
                <input id="states" type="text" className="form-control" {...register('states')} />
              </div>

              <div className="mb-3">
                <label htmlFor="kingdomCoverage" className="form-label">Kingdom Coverage (JSON array or text)</label>
                <input id="kingdomCoverage" type="text" className="form-control" {...register('kingdomCoverage')} />
                <div className="form-text text-muted">e.g. ["Plantae","Animalia"]</div>
              </div>

              <div className="mb-3">
                <label htmlFor="scientificNames" className="form-label">Scientific Names (JSON array or text)</label>
                <input id="scientificNames" type="text" className="form-control" {...register('scientificNames')} />
              </div>

              <div className="row">
                <div className="col-md-6 mb-3">
                  <label htmlFor="startDate" className="form-label">Start Date (year)</label>
                  <input id="startDate" type="text" className="form-control" placeholder="e.g. 1900" {...register('startDate')} />
                </div>
                <div className="col-md-6 mb-3">
                  <label htmlFor="endDate" className="form-label">End Date (year)</label>
                  <input id="endDate" type="text" className="form-control" placeholder="e.g. 2020" {...register('endDate')} />
                </div>
              </div>

              <div className="row">
                <div className="col-md-6 mb-3">
                  <label htmlFor="collectionType" className="form-label">Collection Type</label>
                  <select id="collectionType" className="form-select" {...register('collectionType')}>
                    <option value="">-- Select --</option>
                    {COLLECTION_TYPES.map((ct) => (
                      <option key={ct} value={ct}>{ct}</option>
                    ))}
                  </select>
                </div>
                <div className="col-md-6 mb-3">
                  <label htmlFor="active" className="form-label">Status</label>
                  <select id="active" className="form-select" {...register('active')}>
                    <option value="">-- Select --</option>
                    {ACTIVE_OPTIONS.map((opt) => (
                      <option key={opt} value={opt}>{opt}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="row">
                <div className="col-md-6 mb-3">
                  <label htmlFor="numRecords" className="form-label">Number of Records</label>
                  <input id="numRecords" type="number" className="form-control" {...register('numRecords', { valueAsNumber: true })} />
                </div>
                <div className="col-md-6 mb-3">
                  <label htmlFor="numRecordsDigitised" className="form-label">Number of Records Digitised</label>
                  <input id="numRecordsDigitised" type="number" className="form-control" {...register('numRecordsDigitised', { valueAsNumber: true })} />
                </div>
              </div>

              <div className="mb-3">
                <label htmlFor="subCollections" className="form-label">Sub-collections (JSON)</label>
                <textarea
                  id="subCollections"
                  className="form-control font-monospace"
                  rows={4}
                  {...register('subCollections')}
                />
                <div className="form-text text-muted">
                  JSON array of objects, e.g. [&#123;"name":"Herbs","description":"Dried specimens"&#125;]
                </div>
              </div>
            </fieldset>
          )}

          {/* DataResource-specific */}
          {entityType === 'dataResource' && (
            <fieldset className="mb-4">
              <legend>Data Resource Details</legend>

              <div className="row">
                <div className="col-md-6 mb-3">
                  <label htmlFor="resourceType" className="form-label">Resource Type</label>
                  <select id="resourceType" className="form-select" {...register('resourceType')}>
                    <option value="">-- Select --</option>
                    {RESOURCE_TYPES.map((rt) => (
                      <option key={rt} value={rt}>{rt}</option>
                    ))}
                  </select>
                </div>
                <div className="col-md-6 mb-3">
                  <label htmlFor="status" className="form-label">Status</label>
                  <input id="status" type="text" className="form-control" {...register('status')} />
                </div>
              </div>

              <div className="mb-3">
                <label htmlFor="contentTypes" className="form-label">Content Types (JSON array)</label>
                <input id="contentTypes" type="text" className="form-control" {...register('contentTypes')} />
                <div className="form-text text-muted">e.g. ["point occurrence data","images"]</div>
              </div>

              <div className="mb-3">
                <label htmlFor="dr-geographicDescription" className="form-label">Geographic Description</label>
                <textarea id="dr-geographicDescription" className="form-control" rows={3} {...register('geographicDescription')} />
              </div>

              <div className="mb-3">
                <label htmlFor="purpose" className="form-label">Purpose</label>
                <textarea id="purpose" className="form-control" rows={3} {...register('purpose')} />
              </div>

              <div className="mb-3">
                <label htmlFor="qualityControlDescription" className="form-label">Quality Control Description</label>
                <textarea id="qualityControlDescription" className="form-control" rows={3} {...register('qualityControlDescription')} />
              </div>

              <div className="mb-3">
                <label htmlFor="methodStepDescription" className="form-label">Method Step Description</label>
                <textarea id="methodStepDescription" className="form-control" rows={3} {...register('methodStepDescription')} />
              </div>

              <div className="row">
                <div className="col-md-6 mb-3">
                  <label htmlFor="dataCollectionProtocolName" className="form-label">Data Collection Protocol Name</label>
                  <input id="dataCollectionProtocolName" type="text" className="form-control" {...register('dataCollectionProtocolName')} />
                </div>
                <div className="col-md-6 mb-3">
                  <label htmlFor="dataCollectionProtocolDoc" className="form-label">Protocol Document URL</label>
                  <input id="dataCollectionProtocolDoc" type="text" className="form-control" {...register('dataCollectionProtocolDoc')} />
                </div>
              </div>

              <div className="mb-3">
                <label htmlFor="suitableFor" className="form-label">Suitable For</label>
                <textarea id="suitableFor" className="form-control" rows={2} {...register('suitableFor')} />
              </div>

              <fieldset className="mb-3">
                <legend className="fs-6 fw-semibold">Bounding Box</legend>
                <div className="row">
                  <div className="col-md-6 offset-md-3 mb-3">
                    <label htmlFor="northBoundingCoordinate" className="form-label">North</label>
                    <input id="northBoundingCoordinate" type="number" step="any" className="form-control" {...register('northBoundingCoordinate', { valueAsNumber: true })} />
                  </div>
                </div>
                <div className="row">
                  <div className="col-md-6 mb-3">
                    <label htmlFor="westBoundingCoordinate" className="form-label">West</label>
                    <input id="westBoundingCoordinate" type="number" step="any" className="form-control" {...register('westBoundingCoordinate', { valueAsNumber: true })} />
                  </div>
                  <div className="col-md-6 mb-3">
                    <label htmlFor="eastBoundingCoordinate" className="form-label">East</label>
                    <input id="eastBoundingCoordinate" type="number" step="any" className="form-control" {...register('eastBoundingCoordinate', { valueAsNumber: true })} />
                  </div>
                </div>
                <div className="row">
                  <div className="col-md-6 offset-md-3 mb-3">
                    <label htmlFor="southBoundingCoordinate" className="form-label">South</label>
                    <input id="southBoundingCoordinate" type="number" step="any" className="form-control" {...register('southBoundingCoordinate', { valueAsNumber: true })} />
                  </div>
                </div>
              </fieldset>

              <div className="row">
                <div className="col-md-6 mb-3">
                  <label htmlFor="beginDate" className="form-label">Begin Date</label>
                  <input id="beginDate" type="text" className="form-control" {...register('beginDate')} />
                </div>
                <div className="col-md-6 mb-3">
                  <label htmlFor="drEndDate" className="form-label">End Date</label>
                  <input id="drEndDate" type="text" className="form-control" {...register('drEndDate')} />
                </div>
              </div>

              <div className="row">
                <div className="col-md-6 mb-3">
                  <label htmlFor="lastChecked" className="form-label">Last Checked</label>
                  <input id="lastChecked" type="text" className="form-control" {...register('lastChecked')} />
                </div>
                <div className="col-md-6 mb-3">
                  <label htmlFor="dataCurrency" className="form-label">Data Currency</label>
                  <input id="dataCurrency" type="text" className="form-control" {...register('dataCurrency')} />
                </div>
              </div>
            </fieldset>
          )}

          {/* DataHub-specific */}
          {entityType === 'dataHub' && (
            <fieldset className="mb-4">
              <legend>Data Hub Members</legend>

              <div className="mb-3">
                <label htmlFor="memberInstitutions" className="form-label">Member Institutions (JSON)</label>
                <textarea id="memberInstitutions" className="form-control font-monospace" rows={3} {...register('memberInstitutions')} />
              </div>

              <div className="mb-3">
                <label htmlFor="memberCollections" className="form-label">Member Collections (JSON)</label>
                <textarea id="memberCollections" className="form-control font-monospace" rows={3} {...register('memberCollections')} />
              </div>

              <div className="mb-3">
                <label htmlFor="memberDataResources" className="form-label">Member Data Resources (JSON)</label>
                <textarea id="memberDataResources" className="form-control font-monospace" rows={3} {...register('memberDataResources')} />
              </div>
            </fieldset>
          )}

          {/* Buttons */}
          <div className="d-flex gap-2 mt-4">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={isSubmitting || mutation.isPending}
            >
              {(isSubmitting || mutation.isPending) && (
                <span className="spinner-border spinner-border-sm me-1" role="status" />
              )}
              Save
            </button>
            <Link to={`${config.showPath}/${id}`} className="btn btn-outline-dark">Cancel</Link>
          </div>
        </form>
      </div>
    </ProtectedRoute>
  );
}
