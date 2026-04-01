import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import apiClient from '../../api/client';
import type { DataResource, ProviderGroup } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

// ---------------------------------------------------------------------------
// Darwin Core "important" fields — mirrors DarwinCoreFields.groovy getImportant()
// Fields with a `values` list render as a <select>, others as a free-text input.
// ---------------------------------------------------------------------------
interface DwcFieldDef {
  name: string;
  values?: string[];
}

const DWC_IMPORTANT_FIELDS: DwcFieldDef[] = [
  {
    name: 'basisOfRecord',
    values: [
      '', 'FossilSpecimen', 'HumanObservation', 'LivingSpecimen',
      'MachineObservation', 'NomenclaturalChecklist', 'PreservedSpecimen',
      'MaterialSample', 'Event', 'Taxon', 'Occurrence', 'MaterialCitation',
    ],
  },
  { name: 'type', values: ['', 'Event', 'MovingImage', 'PhysicalObject', 'Sound', 'StillImage', 'Text'] },
  { name: 'recordedBy' },
  { name: 'occurrenceStatus', values: ['', 'present', 'absent'] },
  { name: 'samplingProtocol' },
  { name: 'country' },
  { name: 'geodeticDatum' },
  { name: 'coordinateUncertaintyInMeters' },
  { name: 'coordinatePrecision' },
  { name: 'georeferencedBy' },
  { name: 'georeferenceProtocol' },
  { name: 'georeferenceSources' },
  { name: 'georeferenceVerificationStatus', values: ['', 'verified', 'unverified'] },
  { name: 'identifiedBy' },
  { name: 'identificationReferences' },
  { name: 'identificationQualifier' },
  { name: 'identificationVerificationStatus', values: ['', 'verified', 'unverified'] },
  { name: 'kingdom' },
  {
    name: 'taxonRank',
    values: [
      '', 'kingdom', 'subkingdom', 'division', 'phylum', 'subdivision', 'subphylum',
      'class', 'subclass', 'order', 'suborder', 'family', 'subfamily', 'tribe',
      'subtribe', 'genus', 'subgenus', 'section', 'subsection', 'series', 'subseries',
      'species', 'subspecies', 'variety', 'subvariety', 'form', 'subform',
    ],
  },
  {
    name: 'datePrecision',
    values: [
      '', 'Day', 'Year', 'Month', 'Day Range', 'Month Range', 'Year Range',
      'Before Year', 'After Year',
    ],
  },
];

// ---------------------------------------------------------------------------
// Form types
// ---------------------------------------------------------------------------
interface ContributionFormValues {
  status: string;
  provenance: string;
  lastChecked: string;
  dataCurrency: string;
  harvestFrequency: number | '';
  mobilisationNotes: string;
  harvestingNotes: string;
  publicArchiveAvailable: boolean;
  connectionParameters: string;
  // DwC defaults stored as a flat map of fieldName → value
  dwcDefaults: Record<string, string>;
  version?: number;
}

interface AppConfigPartial {
  statusList?: string[];
  provenanceTypesList?: string[];
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------
// "Less important" DwC fields — mirrors DarwinCoreFields.groovy getLessImportant()
// ---------------------------------------------------------------------------
const DWC_LESS_IMPORTANT_FIELDS: string[] = [
  'institutionCode', 'collectionCode', 'catalogNumber', 'individualCount', 'sex',
  'lifeStage', 'reproductiveCondition', 'behavior', 'establishmentMeans', 'preparations',
  'disposition', 'associatedMedia', 'associatedReferences', 'associatedSequences',
  'associatedTaxa', 'eventDate', 'startDayOfYear', 'endDayOfYear', 'year', 'month',
  'day', 'verbatimEventDate', 'habitat', 'fieldNumber', 'fieldNotes', 'eventRemarks',
  'waterBody', 'islandGroup', 'island', 'countryCode', 'stateProvince', 'county',
  'municipality', 'locality', 'verbatimLocality', 'minimumElevationInMeters',
  'maximumElevationInMeters', 'minimumDepthInMeters', 'maximumDepthInMeters',
  'locationRemarks', 'decimalLatitude', 'decimalLongitude', 'verbatimCoordinates',
  'verbatimLatitude', 'verbatimLongitude', 'verbatimCoordinateSystem', 'verbatimSRS',
  'scientificName', 'vernacularName', 'scientificNameAuthorship', 'higherClassification',
  'phylum', 'class', 'order', 'family', 'genus', 'subgenus', 'specificEpithet',
  'infraspecificEpithet', 'nomenclaturalCode', 'taxonomicStatus', 'nomenclaturalStatus',
  'taxonRemarks',
];

const IMPORTANT_FIELD_NAMES = new Set(DWC_IMPORTANT_FIELDS.map((f) => f.name));

export default function ContributionForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [parseError, setParseError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [extraDwcFields, setExtraDwcFields] = useState<string[]>([]);
  const [addFieldSelection, setAddFieldSelection] = useState('');

  const { data: resource, isLoading, error } = useQuery({
    queryKey: ['dataResource', id],
    queryFn: async () => {
      const { data } = await apiClient.get<DataResource>(`/dataResource/${id}`);
      return data;
    },
    enabled: !!id,
  });

  const { data: appConfig } = useQuery({
    queryKey: ['config'],
    queryFn: async () => {
      const { data } = await apiClient.get<AppConfigPartial>('/config');
      return data;
    },
  });

  const statusList = appConfig?.statusList ?? ['identified', 'inProgress', 'dataAvailable', 'linksAvailable', 'declined'];
  const provenanceList = appConfig?.provenanceTypesList ?? ['Individual sightings', 'Published dataset', 'Draft'];

  // Show DwC section only for 'records' or 'events' resource types
  const showDwc = resource?.resourceType === 'records' || resource?.resourceType === 'events';

  const { register, handleSubmit, setValue, watch } = useForm<ContributionFormValues>({
    defaultValues: {
      status: '',
      provenance: '',
      lastChecked: '',
      dataCurrency: '',
      harvestFrequency: '',
      mobilisationNotes: '',
      harvestingNotes: '',
      publicArchiveAvailable: false,
      connectionParameters: '{}',
      dwcDefaults: {},
    },
  });

  useEffect(() => {
    if (!resource) return;
    setValue('status', resource.status ?? '');
    setValue('provenance', resource.provenance ?? '');
    setValue('lastChecked', resource.lastChecked ?? '');
    setValue('dataCurrency', resource.dataCurrency ?? '');
    setValue('harvestFrequency', resource.harvestFrequency ?? '');
    setValue('mobilisationNotes', resource.mobilisationNotes ?? '');
    setValue('harvestingNotes', resource.harvestingNotes ?? '');
    setValue('publicArchiveAvailable', resource.publicArchiveAvailable ?? false);

    // Connection parameters
    if (resource.connectionParameters) {
      try {
        const formatted = JSON.stringify(JSON.parse(resource.connectionParameters), null, 2);
        setValue('connectionParameters', formatted);
      } catch {
        setValue('connectionParameters', resource.connectionParameters);
      }
    } else {
      setValue('connectionParameters', '{}');
    }

    // DwC defaults
    if (resource.defaultDarwinCoreValues) {
      const parsed = resource.defaultDarwinCoreValues;
      const dwcMap: Record<string, string> = {};
      for (const field of DWC_IMPORTANT_FIELDS) {
        dwcMap[field.name] = parsed[field.name] ?? '';
      }
      // Include any existing less-important field values
      const existingLessImportant: string[] = [];
      for (const [key, value] of Object.entries(parsed)) {
        if (!IMPORTANT_FIELD_NAMES.has(key) && value !== '') {
          dwcMap[key] = value;
          existingLessImportant.push(key);
        }
      }
      if (existingLessImportant.length > 0) {
        setExtraDwcFields(existingLessImportant);
      }
      setValue('dwcDefaults', dwcMap);
    }

    // Optimistic locking
    setValue('version', (resource as ProviderGroup & { version?: number }).version);
  }, [resource, setValue]);

  const connectionParameters = watch('connectionParameters');

  function validateJson(text: string): boolean {
    try {
      JSON.parse(text);
      setParseError(null);
      return true;
    } catch (e) {
      setParseError(e instanceof Error ? e.message : 'Invalid JSON');
      return false;
    }
  }

  function handleFormat() {
    try {
      const formatted = JSON.stringify(JSON.parse(connectionParameters), null, 2);
      setValue('connectionParameters', formatted);
      setParseError(null);
    } catch (e) {
      setParseError(e instanceof Error ? e.message : 'Invalid JSON');
    }
  }

  const mutation = useMutation({
    mutationFn: async (data: ContributionFormValues) => {
      // Validate JSON before saving
      let parsedParams: string;
      try {
        parsedParams = JSON.stringify(JSON.parse(data.connectionParameters));
      } catch {
        throw new Error('Invalid JSON in connection parameters');
      }

      // Build DwC defaults — only include non-empty values
      let defaultDarwinCoreValues: Record<string, string> | undefined;
      if (showDwc) {
        const dwcEntries = Object.entries(data.dwcDefaults).filter(([, v]) => v !== '');
        if (dwcEntries.length > 0) {
          defaultDarwinCoreValues = Object.fromEntries(dwcEntries);
        } else {
          defaultDarwinCoreValues = undefined;
        }
      }

      const payload: Partial<DataResource> & { version?: number } = {
        status: data.status || undefined,
        provenance: data.provenance || undefined,
        lastChecked: data.lastChecked || undefined,
        dataCurrency: data.dataCurrency || undefined,
        harvestFrequency: data.harvestFrequency === '' ? undefined : Number(data.harvestFrequency),
        mobilisationNotes: data.mobilisationNotes || undefined,
        harvestingNotes: data.harvestingNotes || undefined,
        publicArchiveAvailable: data.publicArchiveAvailable,
        connectionParameters: parsedParams,
        ...(showDwc ? { defaultDarwinCoreValues } : {}),
      };
      if (data.version !== undefined) payload.version = data.version;
      await apiClient.put(`/dataResource/${id}`, payload);
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
            <li className="breadcrumb-item active">Contribution</li>
          </ol>
        </nav>
        <h1>Contribution - {resource.name}</h1>

        {saveError && <div className="alert alert-danger">{saveError}</div>}
        {parseError && (
          <div className="alert alert-warning">JSON parse error: {parseError}</div>
        )}

        <form onSubmit={handleSubmit((data) => mutation.mutate(data))}>
          {/* Status */}
          <div className="mb-3">
            <label htmlFor="status" className="form-label">Status</label>
            <select id="status" className="form-select" {...register('status')}>
              {statusList.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          {/* Provenance */}
          <div className="mb-3">
            <label htmlFor="provenance" className="form-label">Provenance</label>
            <select id="provenance" className="form-select" {...register('provenance')}>
              <option value="">none</option>
              {provenanceList.map((p) => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>

          {/* Last checked */}
          <div className="mb-3">
            <label htmlFor="lastChecked" className="form-label">Last Checked</label>
            <input
              type="text"
              id="lastChecked"
              className="form-control"
              placeholder="yyyy-MM-dd HH:mm:ss.S"
              {...register('lastChecked')}
            />
          </div>

          {/* Data currency */}
          <div className="mb-3">
            <label htmlFor="dataCurrency" className="form-label">Data Currency</label>
            <input
              type="text"
              id="dataCurrency"
              className="form-control"
              placeholder="yyyy-MM-dd HH:mm:ss.S"
              {...register('dataCurrency')}
            />
          </div>

          {/* Harvest frequency */}
          <div className="mb-3">
            <label htmlFor="harvestFrequency" className="form-label">Harvest Frequency</label>
            <input
              type="number"
              id="harvestFrequency"
              className="form-control"
              min={0}
              {...register('harvestFrequency')}
            />
          </div>

          {/* Mobilisation notes */}
          <div className="mb-3">
            <label htmlFor="mobilisationNotes" className="form-label">Mobilisation Notes</label>
            <textarea
              id="mobilisationNotes"
              className="form-control"
              rows={4}
              {...register('mobilisationNotes')}
            />
          </div>

          {/* Harvesting notes */}
          <div className="mb-3">
            <label htmlFor="harvestingNotes" className="form-label">Harvesting Notes</label>
            <textarea
              id="harvestingNotes"
              className="form-control"
              rows={4}
              {...register('harvestingNotes')}
            />
          </div>

          {/* Public archive available */}
          <div className="form-check mb-3">
            <input
              type="checkbox"
              id="publicArchiveAvailable"
              className="form-check-input"
              {...register('publicArchiveAvailable')}
            />
            <label htmlFor="publicArchiveAvailable" className="form-check-label">
              Public archive available
            </label>
          </div>

          {/* Connection Parameters */}
          <div className="card card-body mb-3">
            <h5 className="mb-3">Harvest Parameters</h5>
            <div className="mb-2">
              <label htmlFor="connectionParams" className="form-label">
                Connection Parameters (JSON)
              </label>
              <textarea
                id="connectionParams"
                className={`form-control font-monospace ${parseError ? 'is-invalid' : ''}`}
                rows={15}
                spellCheck={false}
                {...register('connectionParameters', {
                  onBlur: () => validateJson(connectionParameters),
                })}
              />
            </div>
            <button
              type="button"
              className="btn btn-outline-dark btn-sm"
              onClick={handleFormat}
            >
              Format JSON
            </button>
          </div>

          {/* Darwin Core Default Values — only for 'records' and 'events' resource types */}
          {showDwc && (
            <div className="card card-body mb-3">
              <h5 className="mb-1">Darwin Core Default Values</h5>
              <p className="text-muted small mb-3">
                These values will be applied as defaults to all records in this resource during
                processing. Leave blank to use values from the data.
              </p>
              {DWC_IMPORTANT_FIELDS.map((field) => (
                <div className="mb-3" key={field.name}>
                  <label htmlFor={`dwc-${field.name}`} className="form-label">
                    {field.name}
                  </label>
                  {field.values ? (
                    <select
                      id={`dwc-${field.name}`}
                      className="form-select"
                      {...register(`dwcDefaults.${field.name}`)}
                    >
                      {field.values.map((v) => (
                        <option key={v} value={v}>{v || '-- none --'}</option>
                      ))}
                    </select>
                  ) : (
                    <input
                      type="text"
                      id={`dwc-${field.name}`}
                      className="form-control"
                      {...register(`dwcDefaults.${field.name}`)}
                    />
                  )}
                </div>
              ))}

              {/* Less important DwC fields (existing values + manually added) */}
              {extraDwcFields.length > 0 && (
                <>
                  <hr />
                  <h6 className="mb-3">Additional DwC Fields</h6>
                  {extraDwcFields.map((fieldName) => (
                    <div className="mb-3" key={fieldName}>
                      <label htmlFor={`dwc-${fieldName}`} className="form-label">
                        {fieldName}
                      </label>
                      <input
                        type="text"
                        id={`dwc-${fieldName}`}
                        className="form-control"
                        {...register(`dwcDefaults.${fieldName}`)}
                      />
                    </div>
                  ))}
                </>
              )}

              {/* Add another DwC field */}
              <hr />
              <div className="d-flex gap-2 align-items-end">
                <div className="flex-grow-1">
                  <label htmlFor="addDwcField" className="form-label">Add another DwC term</label>
                  <select
                    id="addDwcField"
                    className="form-select"
                    value={addFieldSelection}
                    onChange={(e) => setAddFieldSelection(e.target.value)}
                  >
                    <option value="">-- select a term --</option>
                    {DWC_LESS_IMPORTANT_FIELDS
                      .filter((f) => !extraDwcFields.includes(f))
                      .map((f) => (
                        <option key={f} value={f}>{f}</option>
                      ))}
                  </select>
                </div>
                <button
                  type="button"
                  className="btn btn-outline-dark btn-sm"
                  disabled={!addFieldSelection}
                  onClick={() => {
                    if (addFieldSelection && !extraDwcFields.includes(addFieldSelection)) {
                      setExtraDwcFields((prev) => [...prev, addFieldSelection]);
                      setValue(`dwcDefaults.${addFieldSelection}`, '');
                      setAddFieldSelection('');
                    }
                  }}
                >
                  Add term
                </button>
              </div>
            </div>
          )}

          <div className="d-flex gap-2">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={mutation.isPending || !!parseError}
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
