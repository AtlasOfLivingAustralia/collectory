import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { MapContainer, TileLayer, Marker } from 'react-leaflet';
import apiClient from '../../api/client';
import type {
  ProviderGroup,
  Collection,
  Institution,
  DataResource,
  DataProvider,
  DataHub,
  ContactFor,
  ExternalIdentifier,
} from '../../api/types';
import { deleteEntity } from '../../api/endpoints/mutations';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import { useConfig } from '../../hooks/useConfig';

/* ---------- mini-map for location preview ---------- */

function LocationMiniMap({ lat, lng, tileUrl }: { lat: number; lng: number; tileUrl: string }) {
  return (
    <MapContainer
      center={[lat, lng]}
      zoom={12}
      style={{ height: '100%', width: '100%' }}
      scrollWheelZoom={false}
      dragging={false}
      zoomControl={false}
    >
      <TileLayer url={tileUrl} />
      <Marker position={[lat, lng]} />
    </MapContainer>
  );
}

/* ---------- entity type config ---------- */

const ENTITY_CONFIG: Record<string, { apiPath: string; plural: string; singular: string }> = {
  collection: { apiPath: '/collection', plural: 'Collections', singular: 'Collection' },
  institution: { apiPath: '/institution', plural: 'Institutions', singular: 'Institution' },
  dataResource: { apiPath: '/dataResource', plural: 'Data Resources', singular: 'Data Resource' },
  dataProvider: { apiPath: '/dataProvider', plural: 'Data Providers', singular: 'Data Provider' },
  dataHub: { apiPath: '/dataHub', plural: 'Data Hubs', singular: 'Data Hub' },
};

/* ---------- public show path mapping ---------- */

const PUBLIC_SHOW_PATH: Record<string, string> = {
  collection: '/public/showCollection/',
  institution: '/public/showInstitution/',
  dataResource: '/public/showDataResource/',
  dataProvider: '/public/showDataProvider/',
  dataHub: '/public/showDataHub/',
};

/* ---------- helpers ---------- */

function fmtDate(d?: string) {
  if (!d) return '-';
  return new Date(d).toLocaleString();
}

function tryParseJson(val?: string): unknown {
  if (!val) return null;
  try {
    return JSON.parse(val);
  } catch {
    return val;
  }
}

function parseKeywords(val?: string): string[] {
  if (!val) return [];
  const parsed = tryParseJson(val);
  if (Array.isArray(parsed)) return parsed as string[];
  if (typeof parsed === 'string') return parsed.split(',').map((s) => s.trim()).filter(Boolean);
  return [];
}

function boolIcon(val?: boolean) {
  return val ? (
    <i className="fa fa-check text-success" />
  ) : (
    <i className="fa fa-times text-muted" />
  );
}

/* ---------- sub-components ---------- */

interface CardProps {
  title: string;
  editLink?: string;
  children: React.ReactNode;
}

function SectionCard({ title, editLink, children }: CardProps) {
  return (
    <div className="card mb-3">
      <div className="card-header d-flex justify-content-between align-items-center">
        <strong>{title}</strong>
        {editLink && (
          <Link to={editLink} className="btn btn-sm btn-outline-dark">
            <i className="fa fa-edit me-1" /> Edit
          </Link>
        )}
      </div>
      <div className="card-body">{children}</div>
    </div>
  );
}

function FieldRow({ label, value }: { label: string; value?: React.ReactNode }) {
  if (value === undefined || value === null || value === '') return null;
  return (
    <div className="row mb-1">
      <div className="col-sm-4 text-muted">{label}</div>
      <div className="col-sm-8">{value}</div>
    </div>
  );
}

/* ---------- audit event type ---------- */

interface AuditEvent {
  date: string;
  userId?: string;
  action?: string;
  property?: string;
  oldValue?: string;
  newValue?: string;
}

/* ---------- main component ---------- */

export default function EntityAdminShow() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;

  const { data: appConfig } = useConfig();
  const gbifWebsite = appConfig?.gbifWebsite ?? 'https://www.gbif.org';
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Fetch entity
  const {
    data: entity,
    isLoading,
    error,
  } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      if (!config || !id) throw new Error('missing params');
      const { data } = await apiClient.get<ProviderGroup>(`${config.apiPath}/${id}`);
      return data;
    },
    enabled: !!config && !!id,
  });

  // Fetch contacts
  const { data: contacts = [] } = useQuery({
    queryKey: [entityType, id, 'contacts'],
    queryFn: async () => {
      if (!config || !id) return [];
      const { data } = await apiClient.get<ContactFor[]>(`${config.apiPath}/${id}/contacts`);
      return data;
    },
    enabled: !!config && !!id,
  });

  // Fetch external identifiers
  const { data: externalIds = [] } = useQuery({
    queryKey: [entityType, id, 'externalIdentifiers'],
    queryFn: async () => {
      if (!id) return [];
      const { data } = await apiClient.get<ExternalIdentifier[]>(`/lookup/externalIdentifiers/${id}`);
      return data;
    },
    enabled: !!id,
  });

  // Fetch audit / change history
  // TODO: Replace with actual audit endpoint when available (e.g. GET /${entityType}/${id}/changes or /audit/${id})
  const { data: auditEvents } = useQuery({
    queryKey: [entityType, id, 'audit'],
    queryFn: async () => {
      if (!config || !id) return null;
      try {
        const { data } = await apiClient.get<AuditEvent[]>(`${config.apiPath}/${id}/changes`);
        return data;
      } catch {
        // Endpoint may not exist yet — return null to fall back to simple display
        return null;
      }
    },
    enabled: !!config && !!id,
    retry: false,
  });

  /* ---------- mutations ---------- */

  // DataResource verification toggle
  const verifyMutation = useMutation({
    mutationFn: async (markVerified: boolean) => {
      const action = markVerified ? 'markAsVerified' : 'markAsUnverified';
      await apiClient.post(`/dataResource/${id}/${action}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id] });
    },
  });

  // GBIF registration actions
  const gbifMutation = useMutation({
    mutationFn: async (action: 'registerGBIF' | 'updateGBIF' | 'deleteGBIF') => {
      await apiClient.post(`/dataResource/${id}/${action}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id] });
    },
  });

  async function handleDelete() {
    if (!entityType || !id) return;
    setDeleting(true);
    try {
      await deleteEntity(entityType, id);
      navigate(`/${entityType}/list`);
    } catch (err) {
      console.error('Delete failed:', err);
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
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
      <div className="container mt-4 d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (error || !entity) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">
          Failed to load {config.singular}. {id ? `UID: ${id}` : ''}
        </div>
      </div>
    );
  }

  // Type narrowing helpers
  const asCollection = entityType === 'collection' ? (entity as Collection) : null;
  const asInstitution = entityType === 'institution' ? (entity as Institution) : null;
  const asDataResource = entityType === 'dataResource' ? (entity as DataResource) : null;
  const asDataProvider = entityType === 'dataProvider' ? (entity as DataProvider) : null;
  const asDataHub = entityType === 'dataHub' ? (entity as DataHub) : null;

  const taxonomyHints = tryParseJson(entity.taxonomyHints);
  const keywords = parseKeywords(entity.keywords);
  const networkMembership = Array.isArray(entity.networkMembership)
    ? entity.networkMembership
    : tryParseJson(entity.networkMembership);
  const attributions = tryParseJson(entity.attributions);

  // Connection parameters (DataResource)
  const connectionParams = asDataResource?.connectionParameters
    ? tryParseJson(asDataResource.connectionParameters)
    : null;

  // DwC defaults (DataResource)
  const dwcDefaults = asDataResource?.defaultDarwinCoreValues;
  const showDwcDefaults =
    dwcDefaults &&
    Object.keys(dwcDefaults).length > 0 &&
    (asDataResource?.resourceType === 'records' || asDataResource?.resourceType === 'events');

  // Public show path
  const publicShowPath = entityType ? PUBLIC_SHOW_PATH[entityType] : null;
  const showViewPublic =
    publicShowPath &&
    !(entityType === 'dataResource' && asDataResource?.isPrivate);

  // API base URL for JSON link
  const apiBaseUrl = '/ws';

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        {/* Breadcrumb */}
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item">
              <Link to={`/${entityType}/list`}>{config.plural}</Link>
            </li>
            <li className="breadcrumb-item active">{entity.name}</li>
          </ol>
        </nav>

        {/* ===== 1. Toolbar links ===== */}
        <div className="d-flex gap-2 mb-3">
          {showViewPublic && (
            <Link
              to={`${publicShowPath}${id}`}
              className="btn btn-sm btn-outline-primary"
            >
              <i className="fa fa-eye me-1" /> View Public
            </Link>
          )}
          <a
            href={`${apiBaseUrl}/${entityType}/${id}`}
            target="_blank"
            rel="noreferrer"
            className="btn btn-sm btn-outline-secondary"
          >
            <i className="fa fa-code me-1" /> JSON
          </a>
        </div>

        <div className="d-flex justify-content-between align-items-start mb-3">
          <h1>{entity.name}</h1>
          <Link to={`/${entityType}/edit/${id}`} className="btn btn-primary">
            <i className="fa fa-edit me-1" /> Edit
          </Link>
        </div>

        <div className="row">
          {/* ===== Main column ===== */}
          <div className="col-md-8">
            {/* 1. Summary */}
            <SectionCard title="Summary">
              <FieldRow label="Name" value={entity.name} />
              <FieldRow label="UID" value={<code>{entity.uid}</code>} />
              <FieldRow label="GUID" value={entity.guid} />
              <FieldRow label="Acronym" value={entity.acronym} />
              <FieldRow
                label="ALA Partner"
                value={
                  entity.isALAPartner ? (
                    <span className="badge bg-success">Yes</span>
                  ) : (
                    <span className="badge bg-secondary">No</span>
                  )
                }
              />
              <FieldRow label="Date Created" value={fmtDate(entity.dateCreated)} />
              <FieldRow label="Last Updated" value={fmtDate(entity.lastUpdated)} />
              <FieldRow label="User Last Modified" value={entity.userLastModified} />

              {/* 2. DataResource verification status */}
              {asDataResource && (
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Verification Status</div>
                  <div className="col-sm-8 d-flex align-items-center gap-2">
                    {asDataResource.verified ? (
                      <span className="badge bg-success">
                        <i className="fa fa-check me-1" />Verified
                      </span>
                    ) : (
                      <span className="badge bg-secondary">Unverified</span>
                    )}
                    <button
                      className="btn btn-sm btn-outline-dark"
                      disabled={verifyMutation.isPending}
                      onClick={() => verifyMutation.mutate(!asDataResource.verified)}
                    >
                      {verifyMutation.isPending
                        ? 'Updating...'
                        : asDataResource.verified
                          ? 'Mark as Unverified'
                          : 'Mark as Verified'}
                    </button>
                  </div>
                </div>
              )}
            </SectionCard>

            {/* 2. Description */}
            <SectionCard
              title="Description"
              editLink={`/${entityType}/${id}/description`}
            >
              <FieldRow label="Short Description" value={entity.pubShortDescription} />
              <FieldRow
                label="Public Description"
                value={
                  entity.pubDescription ? (
                    <div dangerouslySetInnerHTML={{ __html: entity.pubDescription }} />
                  ) : undefined
                }
              />
              <FieldRow label="Technical Description" value={entity.techDescription} />
              <FieldRow label="Focus" value={entity.focus} />
            </SectionCard>

            {/* ===== 3. DataResource mobilisation/contribution section ===== */}
            {asDataResource && (
              <SectionCard
                title="Mobilisation / Contribution"
                editLink={`/dataResource/${id}/contribution`}
              >
                <FieldRow label="Status" value={asDataResource.status} />
                <FieldRow label="Provenance" value={asDataResource.provenance} />
                <FieldRow label="Last Checked" value={fmtDate(asDataResource.lastChecked)} />
                <FieldRow label="Data Currency" value={asDataResource.dataCurrency} />
                <FieldRow
                  label="Harvest Frequency"
                  value={
                    asDataResource.harvestFrequency != null
                      ? `${asDataResource.harvestFrequency} day(s)`
                      : undefined
                  }
                />
                <FieldRow label="Mobilisation Notes" value={asDataResource.mobilisationNotes} />
                <FieldRow label="Harvesting Notes" value={asDataResource.harvestingNotes} />
                <FieldRow
                  label="Public Archive Available"
                  value={boolIcon(asDataResource.publicArchiveAvailable)}
                />

                {/* Connection parameters */}
                {connectionParams != null && (
                  <div className="mt-3">
                    <h6>Connection Parameters</h6>
                    <pre
                      className="bg-light p-2 rounded"
                      style={{ fontSize: '0.8rem', maxHeight: 300, overflow: 'auto' }}
                    >
                      {JSON.stringify(connectionParams, null, 2)}
                    </pre>
                  </div>
                )}

                {/* DwC defaults */}
                {showDwcDefaults && dwcDefaults && (
                  <div className="mt-3">
                    <h6>DwC Defaults</h6>
                    <table className="table table-sm mb-0">
                      <thead>
                        <tr>
                          <th>Term</th>
                          <th>Default Value</th>
                        </tr>
                      </thead>
                      <tbody>
                        {Object.entries(dwcDefaults).map(([k, v]) => (
                          <tr key={k}>
                            <td><code>{k}</code></td>
                            <td>{v}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </SectionCard>
            )}

            {/* ===== 4. DataResource upload button ===== */}
            {asDataResource && (
              <div className="card mb-3">
                <div className="card-body d-flex justify-content-between align-items-center">
                  <span className="text-muted">Upload occurrence or metadata files</span>
                  <Link
                    to={`/dataResource/${id}/upload`}
                    className="btn btn-outline-primary"
                  >
                    <i className="fa fa-upload me-1" /> Upload File
                  </Link>
                </div>
              </div>
            )}

            {/* ===== 5. DataResource rights section ===== */}
            {asDataResource && (
              <SectionCard
                title="Rights"
                editLink={`/dataResource/${id}/rights`}
              >
                <FieldRow label="Citation" value={asDataResource.citation} />
                <FieldRow label="Rights" value={asDataResource.rights} />
                <FieldRow label="License Type" value={asDataResource.licenseType} />
                <FieldRow label="License Version" value={asDataResource.licenseVersion} />
                <FieldRow
                  label="Permissions Document"
                  value={
                    asDataResource.permissionsDocument ? (
                      asDataResource.permissionsDocument.startsWith('http') ? (
                        <a
                          href={asDataResource.permissionsDocument}
                          target="_blank"
                          rel="noreferrer"
                        >
                          {asDataResource.permissionsDocument}
                        </a>
                      ) : (
                        asDataResource.permissionsDocument
                      )
                    ) : undefined
                  }
                />
                <FieldRow
                  label="Permissions Document Type"
                  value={asDataResource.permissionsDocumentType}
                />
                {/* Risk assessment and filed only shown for DPA-type resources */}
                {asDataResource.permissionsDocumentType === 'Data Provider Agreement' && (
                  <>
                    <FieldRow
                      label="Risk Assessment"
                      value={boolIcon(asDataResource.riskAssessment)}
                    />
                    <FieldRow label="Filed" value={boolIcon(asDataResource.filed)} />
                  </>
                )}
                <FieldRow
                  label="Download Limit"
                  value={
                    asDataResource.downloadLimit != null
                      ? asDataResource.downloadLimit.toLocaleString()
                      : undefined
                  }
                />
              </SectionCard>
            )}

            {/* ===== 6. DataResource GBIF sync section ===== */}
            {asDataResource && (
              <SectionCard title="GBIF Registration">
                <FieldRow label="GBIF Registry Key" value={asDataResource.gbifRegistryKey} />
                <FieldRow
                  label="Repatriation Country"
                  value={asDataResource.repatriationCountry}
                />
                <FieldRow
                  label="GBIF Dataset"
                  value={
                    asDataResource.gbifDataset ? (
                      <span className="badge bg-success">Yes</span>
                    ) : (
                      <span className="badge bg-secondary">No</span>
                    )
                  }
                />
                <FieldRow
                  label="Shareable with GBIF"
                  value={
                    asDataResource.isShareableWithGBIF ? (
                      <span className="badge bg-success">Yes</span>
                    ) : (
                      <span className="badge bg-secondary">No</span>
                    )
                  }
                />
                <FieldRow label="GBIF DOI" value={asDataResource.gbifDoi} />

                <div className="mt-3 d-flex gap-2 flex-wrap">
                  {asDataResource.gbifRegistryKey ? (
                    <>
                      <a
                        href={`${gbifWebsite}/dataset/${asDataResource.gbifRegistryKey}`}
                        target="_blank"
                        rel="noreferrer"
                        className="btn btn-sm btn-outline-dark"
                      >
                        <i className="fa fa-external-link me-1" /> View on GBIF
                      </a>
                      <button
                        className="btn btn-sm btn-outline-primary"
                        disabled={gbifMutation.isPending}
                        onClick={() => gbifMutation.mutate('updateGBIF')}
                      >
                        <i className="fa fa-refresh me-1" /> Update GBIF
                      </button>
                      <button
                        className="btn btn-sm btn-outline-danger"
                        disabled={gbifMutation.isPending}
                        onClick={() => gbifMutation.mutate('deleteGBIF')}
                      >
                        <i className="fa fa-trash me-1" /> Delete from GBIF
                      </button>
                    </>
                  ) : asDataResource.isShareableWithGBIF ? (
                    <button
                      className="btn btn-sm btn-outline-success"
                      disabled={gbifMutation.isPending}
                      onClick={() => gbifMutation.mutate('registerGBIF')}
                    >
                      <i className="fa fa-plus me-1" /> Register with GBIF
                    </button>
                  ) : (
                    <p className="text-muted mb-0">
                      Not registered with GBIF (not marked as shareable).
                    </p>
                  )}
                  {gbifMutation.isPending && (
                    <span className="text-muted ms-2">Processing...</span>
                  )}
                  {gbifMutation.isError && (
                    <span className="text-danger ms-2">
                      GBIF action failed. Please try again.
                    </span>
                  )}
                </div>
              </SectionCard>
            )}

            {/* ===== 7. DataResource user download reports ===== */}
            {/* TODO: loggerURL should come from app config endpoint; using placeholder paths for now */}
            {asDataResource && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Download Reports</strong>
                </div>
                <div className="card-body d-flex gap-2">
                  <a
                    href={`/logger/reasonBreakdown?entityUid=${id}&eventType=downloads`}
                    target="_blank"
                    rel="noreferrer"
                    className="btn btn-sm btn-outline-dark"
                  >
                    <i className="fa fa-download me-1" /> Reason Breakdown
                  </a>
                  <a
                    href={`/logger/emailBreakdown?entityUid=${id}&eventType=downloads`}
                    target="_blank"
                    rel="noreferrer"
                    className="btn btn-sm btn-outline-dark"
                  >
                    <i className="fa fa-envelope me-1" /> Email Breakdown
                  </a>
                </div>
              </div>
            )}

            {/* ===== 9. DataProvider IPT section ===== */}
            {asDataProvider && (
              <SectionCard title="IPT Integration">
                <p className="text-muted mb-0">
                  IPT scanning and management is available in the Manage section.
                </p>
              </SectionCard>
            )}

            {/* 3. Location */}
            <SectionCard
              title="Location"
              editLink={`/${entityType}/${id}/location`}
            >
              {entity.address && (
                <>
                  <FieldRow label="Street" value={entity.address.street} />
                  <FieldRow label="Post Box" value={entity.address.postBox} />
                  <FieldRow label="City" value={entity.address.city} />
                  <FieldRow label="State" value={entity.address.state} />
                  <FieldRow label="Postcode" value={entity.address.postcode} />
                  <FieldRow label="Country" value={entity.address.country} />
                </>
              )}
              <FieldRow label="Latitude" value={entity.latitude?.toString()} />
              <FieldRow label="Longitude" value={entity.longitude?.toString()} />
              <FieldRow label="State (coverage)" value={entity.state} />
              <FieldRow label="Phone" value={entity.phone} />
              <FieldRow label="Email" value={entity.email} />
              {entity.latitude != null && entity.longitude != null && (
                <div className="mt-2" style={{ height: '200px', width: '100%' }}>
                  <LocationMiniMap
                    lat={entity.latitude}
                    lng={entity.longitude}
                    tileUrl={appConfig?.cartodbPattern || 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'}
                  />
                </div>
              )}
            </SectionCard>

            {/* 4. Images */}
            <SectionCard
              title="Images"
              editLink={`/${entityType}/${id}/images`}
            >
              <div className="row">
                {entity.logoRef?.file && (
                  <div className="col-sm-6 mb-2">
                    <h6>Logo</h6>
                    <img
                      src={entity.logoRef.file}
                      alt="Logo"
                      className="img-thumbnail"
                      style={{ maxHeight: 120 }}
                    />
                    <FieldRow label="Caption" value={entity.logoRef.caption} />
                    <FieldRow label="Attribution" value={entity.logoRef.attribution} />
                    <FieldRow label="Copyright" value={entity.logoRef.copyright} />
                  </div>
                )}
                {entity.imageRef?.file && (
                  <div className="col-sm-6 mb-2">
                    <h6>Image</h6>
                    <img
                      src={entity.imageRef.file}
                      alt="Entity image"
                      className="img-thumbnail"
                      style={{ maxHeight: 120 }}
                    />
                    <FieldRow label="Caption" value={entity.imageRef.caption} />
                    <FieldRow label="Attribution" value={entity.imageRef.attribution} />
                    <FieldRow label="Copyright" value={entity.imageRef.copyright} />
                  </div>
                )}
                {!entity.logoRef?.file && !entity.imageRef?.file && (
                  <p className="text-muted">No images uploaded.</p>
                )}
              </div>
            </SectionCard>

            {/* 5. Record consumers/providers (conditional) */}
            {asCollection && asCollection.institution && (
              <SectionCard title="Institution">
                <Link to={`/institution/show/${asCollection.institution.uid}`}>
                  {asCollection.institution.name}
                </Link>
              </SectionCard>
            )}

            {asInstitution && asInstitution.collections && asInstitution.collections.length > 0 && (
              <SectionCard title="Collections">
                <ul className="list-unstyled mb-0">
                  {asInstitution.collections.map((c) => (
                    <li key={c.uid}>
                      <Link to={`/collection/show/${c.uid}`}>{c.name}</Link>
                      {c.acronym && <span className="text-muted ms-1">({c.acronym})</span>}
                    </li>
                  ))}
                </ul>
              </SectionCard>
            )}

            {asDataProvider && asDataProvider.resources && asDataProvider.resources.length > 0 && (
              <SectionCard title="Data Resources">
                <ul className="list-unstyled mb-0">
                  {asDataProvider.resources.map((r) => (
                    <li key={r.uid}>
                      <Link to={`/dataResource/show/${r.uid}`}>{r.name}</Link>
                    </li>
                  ))}
                </ul>
              </SectionCard>
            )}

            {asDataResource && asDataResource.dataProvider && (
              <SectionCard title="Data Provider">
                <Link to={`/dataProvider/show/${asDataResource.dataProvider.uid}`}>
                  {asDataResource.dataProvider.name}
                </Link>
              </SectionCard>
            )}

            {/* 6. Contacts */}
            <SectionCard
              title="Contacts"
              editLink={`/${entityType}/${id}/contacts`}
            >
              {contacts.length === 0 ? (
                <p className="text-muted mb-0">No contacts.</p>
              ) : (
                <table className="table table-sm mb-0">
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Role</th>
                      <th>Primary</th>
                      <th>Admin</th>
                    </tr>
                  </thead>
                  <tbody>
                    {contacts.map((cf) => (
                      <tr key={cf.id}>
                        <td>
                          <Link to={`/contact/show/${cf.contact.id}`}>
                            {[cf.contact.firstName, cf.contact.lastName].filter(Boolean).join(' ') ||
                              cf.contact.email ||
                              `Contact #${cf.contact.id}`}
                          </Link>
                        </td>
                        <td>{cf.role || '-'}</td>
                        <td className="text-center">
                          {cf.primaryContact && <i className="fa fa-check text-success" />}
                        </td>
                        <td className="text-center">
                          {cf.administrator && <i className="fa fa-check text-success" />}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </SectionCard>

            {/* 7. Taxonomy Hints */}
            <SectionCard
              title="Taxonomy Hints"
              editLink={`/${entityType}/${id}/taxonomyHints`}
            >
              {taxonomyHints && typeof taxonomyHints === 'object' ? (
                <pre className="mb-0" style={{ fontSize: '0.85rem' }}>
                  {JSON.stringify(taxonomyHints, null, 2)}
                </pre>
              ) : taxonomyHints ? (
                <p className="mb-0">{String(taxonomyHints)}</p>
              ) : (
                <p className="text-muted mb-0">No taxonomy hints.</p>
              )}
            </SectionCard>

            {/* 8. Attributions */}
            <SectionCard
              title="Attributions"
              editLink={`/${entityType}/${id}/attributions`}
            >
              {attributions && Array.isArray(attributions) && attributions.length > 0 ? (
                <ul className="list-unstyled mb-0">
                  {(attributions as Array<{ name?: string; uid?: string }>).map((a, i) => (
                    <li key={a.uid ?? i}>
                      {a.name ?? a.uid ?? JSON.stringify(a)}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-muted mb-0">No attributions.</p>
              )}
            </SectionCard>

            {/* 9. External Identifiers */}
            <SectionCard
              title="External Identifiers"
              editLink={`/${entityType}/${id}/externalIdentifiers`}
            >
              {externalIds.length === 0 ? (
                <p className="text-muted mb-0">No external identifiers.</p>
              ) : (
                <table className="table table-sm mb-0">
                  <thead>
                    <tr>
                      <th>Source</th>
                      <th>Identifier</th>
                      <th>URI</th>
                    </tr>
                  </thead>
                  <tbody>
                    {externalIds.map((eid) => (
                      <tr key={eid.id}>
                        <td>{eid.source}</td>
                        <td>{eid.identifier}</td>
                        <td>
                          {eid.uri ? (
                            <a href={eid.uri} target="_blank" rel="noreferrer">
                              {eid.uri}
                            </a>
                          ) : (
                            '-'
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </SectionCard>

            {/* ===== 8. Audit / Change History ===== */}
            <SectionCard title="Changes">
              {auditEvents && auditEvents.length > 0 ? (
                <div style={{ maxHeight: 400, overflow: 'auto' }}>
                  <table className="table table-sm mb-0">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>User</th>
                        <th>Action</th>
                        <th>Property</th>
                        <th>Old Value</th>
                        <th>New Value</th>
                      </tr>
                    </thead>
                    <tbody>
                      {auditEvents.map((evt, i) => (
                        <tr key={i}>
                          <td>{fmtDate(evt.date)}</td>
                          <td>{evt.userId || '-'}</td>
                          <td>{evt.action || '-'}</td>
                          <td>{evt.property || '-'}</td>
                          <td style={{ maxWidth: 150, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {evt.oldValue || '-'}
                          </td>
                          <td style={{ maxWidth: 150, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {evt.newValue || '-'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                /* TODO: Full audit log table will display here once the audit endpoint is implemented */
                <p className="text-muted mb-0">
                  Last modified by <strong>{entity.userLastModified}</strong> on{' '}
                  {fmtDate(entity.lastUpdated)}.
                </p>
              )}
            </SectionCard>
          </div>

          {/* ===== Sidebar ===== */}
          <div className="col-md-4">
            {/* Entity-specific info */}
            {asCollection && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Collection Details</strong>
                </div>
                <div className="card-body">
                  <FieldRow label="Collection Type" value={asCollection.collectionType} />
                  <FieldRow label="Active" value={asCollection.active} />
                  <FieldRow label="Records" value={asCollection.numRecords.toLocaleString()} />
                  <FieldRow
                    label="Digitised"
                    value={asCollection.numRecordsDigitised.toLocaleString()}
                  />
                  <FieldRow label="States" value={asCollection.states} />
                  <FieldRow label="Geographic Description" value={asCollection.geographicDescription} />
                  {asCollection.subCollections && (
                    <FieldRow
                      label="Sub-collections"
                      value={
                        <pre className="mb-0" style={{ fontSize: '0.8rem' }}>
                          {JSON.stringify(tryParseJson(asCollection.subCollections), null, 2)}
                        </pre>
                      }
                    />
                  )}
                </div>
              </div>
            )}

            {asInstitution && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Institution Details</strong>
                </div>
                <div className="card-body">
                  <FieldRow label="Institution Type" value={asInstitution.institutionType} />
                  <FieldRow label="Child Institutions" value={asInstitution.childInstitutions} />
                </div>
              </div>
            )}

            {asDataResource && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Data Resource Details</strong>
                </div>
                <div className="card-body">
                  <FieldRow label="Resource Type" value={asDataResource.resourceType} />
                  <FieldRow label="Status" value={asDataResource.status} />
                  <FieldRow label="License Type" value={asDataResource.licenseType} />
                  <FieldRow label="License Version" value={asDataResource.licenseVersion} />
                  <FieldRow label="Rights" value={asDataResource.rights} />
                  <FieldRow label="Citation" value={asDataResource.citation} />
                  <FieldRow
                    label="GBIF Dataset"
                    value={
                      asDataResource.gbifDataset ? (
                        <span className="badge bg-success">Yes</span>
                      ) : (
                        <span className="badge bg-secondary">No</span>
                      )
                    }
                  />
                  <FieldRow label="GBIF DOI" value={asDataResource.gbifDoi} />
                  <FieldRow label="Last Checked" value={fmtDate(asDataResource.lastChecked)} />
                  <FieldRow label="Data Currency" value={asDataResource.dataCurrency} />
                </div>
              </div>
            )}

            {asDataProvider && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Data Provider Details</strong>
                </div>
                <div className="card-body">
                  <FieldRow
                    label="Resources"
                    value={`${asDataProvider.resources?.length ?? 0} data resource(s)`}
                  />
                </div>
              </div>
            )}

            {asDataHub && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Data Hub Details</strong>
                </div>
                <div className="card-body">
                  <FieldRow label="Members" value={asDataHub.members} />
                  <FieldRow
                    label="Member Institutions"
                    value={asDataHub.memberInstitutions}
                  />
                  <FieldRow
                    label="Member Collections"
                    value={asDataHub.memberCollections}
                  />
                  <FieldRow
                    label="Member Data Resources"
                    value={asDataHub.memberDataResources}
                  />
                </div>
              </div>
            )}

            {/* GBIF Registration (sidebar — kept for non-DataResource entities) */}
            {!asDataResource && entity.gbifRegistryKey && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>GBIF Registration</strong>
                </div>
                <div className="card-body">
                  <FieldRow label="Registry Key" value={entity.gbifRegistryKey} />
                  <a
                    href={`${gbifWebsite}/dataset/${entity.gbifRegistryKey}`}
                    target="_blank"
                    rel="noreferrer"
                    className="btn btn-sm btn-outline-dark mt-2"
                  >
                    <i className="fa fa-external-link me-1" /> View on GBIF
                  </a>
                </div>
              </div>
            )}

            {/* Network Membership */}
            {!!networkMembership && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Network Membership</strong>
                </div>
                <div className="card-body">
                  <pre className="mb-0" style={{ fontSize: '0.85rem' }}>
                    {JSON.stringify(networkMembership, null, 2)}
                  </pre>
                </div>
              </div>
            )}

            {/* Keywords */}
            {keywords.length > 0 && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Keywords</strong>
                </div>
                <div className="card-body">
                  {keywords.map((kw) => (
                    <span key={kw} className="badge bg-secondary me-1 mb-1">
                      {kw}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Notes */}
            {entity.notes && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Notes</strong>
                </div>
                <div className="card-body">
                  <p className="mb-0">{entity.notes}</p>
                </div>
              </div>
            )}

            {/* Connection Parameters (DataResource) — sidebar duplicate removed, now in main column mobilisation section */}

            {/* Website */}
            {entity.websiteUrl && (
              <div className="card mb-3">
                <div className="card-header">
                  <strong>Website</strong>
                </div>
                <div className="card-body">
                  <a href={entity.websiteUrl} target="_blank" rel="noreferrer">
                    {entity.websiteUrl}
                  </a>
                </div>
              </div>
            )}

            {/* Delete */}
            <div className="card mb-3 border-danger">
              <div className="card-header bg-danger text-white">
                <strong>Danger Zone</strong>
              </div>
              <div className="card-body">
                {!showDeleteConfirm ? (
                  <button
                    className="btn btn-outline-danger w-100"
                    onClick={() => setShowDeleteConfirm(true)}
                  >
                    <i className="fa fa-trash me-1" /> Delete {config.singular}
                  </button>
                ) : (
                  <div>
                    <p className="text-danger mb-2">
                      Are you sure you want to delete <strong>{entity.name}</strong>? This action
                      cannot be undone.
                    </p>
                    <div className="d-flex gap-2">
                      <button
                        className="btn btn-danger flex-fill"
                        onClick={handleDelete}
                        disabled={deleting}
                      >
                        {deleting ? 'Deleting...' : 'Yes, Delete'}
                      </button>
                      <button
                        className="btn btn-outline-secondary flex-fill"
                        onClick={() => setShowDeleteConfirm(false)}
                        disabled={deleting}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
