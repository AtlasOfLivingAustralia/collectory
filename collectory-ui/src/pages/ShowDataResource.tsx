import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../api/client';
import { useDataResource } from '../hooks/useDataResources';
import { useConfig } from '../hooks/useConfig';
import EntityImage from '../components/public/EntityImage';
import ContactsPanel from '../components/public/ContactsPanel';
import DataAccessPanel from '../components/public/DataAccessPanel';
import ExternalIds from '../components/public/ExternalIds';
import LocationPanel from '../components/public/LocationPanel';
import BiocacheCharts from '../components/public/BiocacheCharts';
import EditButton from '../components/public/EditButton';
import UsageStats from '../components/public/UsageStats';
import NetworkMembership from '../components/public/NetworkMembership';
import AttributionList from '../components/public/AttributionList';
import Breadcrumb from '../components/public/Breadcrumb';
import FormattedText from '../components/common/FormattedText';

export default function ShowDataResource() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const { data: resource, isLoading, error } = useDataResource(id ?? '');
  const { data: config } = useConfig();
  const [activeTab, setActiveTab] = useState('metadata');

  const isRecords = resource?.resourceType === 'records' || resource?.resourceType === 'events';
  const isPublications = resource?.resourceType === 'publications';

  // Facet for biocache: publications use annotationsUid; records/events use data_resource_uid
  const biocacheFacetField = isPublications ? 'annotationsUid' : 'data_resource_uid';

  // Fetch real biocache record count for DataAccessPanel
  const { data: biocacheCount } = useQuery({
    queryKey: ['biocacheCount', resource?.uid, biocacheFacetField],
    queryFn: async () => {
      try {
        const { data } = await apiClient.get<{ totalRecords: number }>(
          `/proxy/biocache/occurrences/search`,
          { params: { q: `${biocacheFacetField}:${resource!.uid}`, pageSize: 0 } },
        );
        return data.totalRecords ?? 0;
      } catch {
        return 0;
      }
    },
    enabled: !!(resource?.uid && (isRecords || isPublications)),
    staleTime: 60_000,
  });

  if (isLoading) {
    return (
      <div className="container mt-4">
        <div className="d-flex justify-content-center">
          <div className="spinner-border" role="status">
            <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
          </div>
        </div>
      </div>
    );
  }

  if (error || !resource) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">
          {t('error.notFound', 'Entity not found or an error occurred.')}
        </div>
      </div>
    );
  }

  const showUsageStats =
    (resource.resourceType === 'records' || resource.resourceType === 'events' || resource.resourceType === 'website') &&
    !config?.disableLoggerLinks;
  const contentTypesList: string[] = resource.contentTypes
    ? (() => { try { return JSON.parse(resource.contentTypes) as string[]; } catch { return []; } })()
    : [];

  const orgNameLong = config?.orgNameLong ?? 'Atlas of Living Australia';
  const resourceTypeListLabel = t(`resourceType.${resource.resourceType}.list`, resource.resourceType ?? 'Data Resources');

  return (
    <>
    <Breadcrumb
      parent={{ url: '/public/datasets', label: t('breadcrumb.datasets', 'Data Resources') }}
      extra={[{ url: `/public/datasets#filters=resourceType:${resource.resourceType}`, label: resourceTypeListLabel }]}
      current={resource.name}
    />
    <div className="row">
      <div className="col-md-9">
        <div id="titleSection">
          <h1>{resource.name}</h1>
          <EditButton entityType="dataResource" uid={resource.uid} />
          {/* Data provider and institution links below the title (matching GSP) */}
          {resource.dataProvider && (
            <h2 className="dataResourceProviderLink">
              <Link to={`/public/show/${resource.dataProvider.uid}`}>{resource.dataProvider.name}</Link>
            </h2>
          )}
          {resource.institution && (
            <h2 className="dataResourceInstitutionLink">
              <Link to={`/public/show/${resource.institution.uid}`}>{resource.institution.name}</Link>
            </h2>
          )}
        </div>

        <div className="tabbable">
          <ul className="nav nav-tabs" id="home-tabs">
            <li className="nav-item">
              <a
                className={`nav-link ${activeTab === 'metadata' ? 'active' : ''}`}
                href="#basicMetadata"
                data-bs-toggle="tab"
                onClick={(e) => { e.preventDefault(); setActiveTab('metadata'); }}
              >
                {t('show.tab.metadata', 'Metadata')}
              </a>
            </li>
            {isRecords && (
              <>
                {showUsageStats && (
                  <li className="nav-item">
                    <a
                      className={`nav-link ${activeTab === 'usage' ? 'active' : ''}`}
                      href="#usage-stats"
                      data-bs-toggle="tab"
                      onClick={(e) => { e.preventDefault(); setActiveTab('usage'); }}
                    >
                      {t('show.tab.usage.stats', 'Usage Stats')}
                    </a>
                  </li>
                )}
                <li className="nav-item">
                  <a
                    className={`nav-link ${activeTab === 'metrics' ? 'active' : ''}`}
                    href="#metrics"
                    data-bs-toggle="tab"
                    onClick={(e) => { e.preventDefault(); setActiveTab('metrics'); }}
                  >
                    {t('show.tab.metrics', 'Metrics')}
                  </a>
                </li>
              </>
            )}
          </ul>
        </div>

        <div className="tab-content">
          <div id="basicMetadata" className={`tab-pane ${activeTab === 'metadata' ? 'show active' : ''}`}>
            <h3 className="dataResourceProviderLink">Dataset type</h3>
            <p>{t(`resourceType.${resource.resourceType}.description`, resource.resourceType ?? '')}</p>

            {(resource.pubDescription || resource.techDescription || resource.focus) && (
              <h3>{t('public.des', 'Description')}</h3>
            )}
            {resource.pubDescription && <FormattedText text={resource.pubDescription} />}
            {resource.techDescription && <FormattedText text={resource.techDescription} />}
            {resource.focus && <FormattedText text={resource.focus} />}

            {/* dataResourceContribution: mirrors cl:dataResourceContribution tag */}
            {resource.resourceType === 'website' && resource.status === 'dataAvailable' && (
              <p>This website provides content for Atlas species pages.</p>
            )}
            {resource.resourceType === 'website' && resource.status === 'linksAvailable' && (
              <p>Links to this website appear on appropriate Atlas species pages.</p>
            )}
            {resource.resourceType === 'document' && resource.status === 'dataAvailable' && (
              <p>Documents from this source provide content for Atlas species pages.</p>
            )}
            {resource.resourceType === 'document' && resource.status === 'linksAvailable' && (
              <p>Links to this document appear on appropriate Atlas species pages.</p>
            )}

            {resource.geographicDescription && (
              <>
                <h3>{t('public.geographicDescription', 'Geographic description')}</h3>
                <FormattedText text={resource.geographicDescription} />
              </>
            )}

            {resource.purpose && (
              <>
                <h3>{t('public.purpose', 'Purpose')}</h3>
                <FormattedText text={resource.purpose} />
              </>
            )}

            {resource.qualityControlDescription && (
              <>
                <h3>{t('public.qualityControlDescription', 'Quality control description')}</h3>
                <FormattedText text={resource.qualityControlDescription} />
              </>
            )}

            {resource.methodStepDescription && (
              <>
                <h3>{t('public.methodStepDescription', 'Method step description')}</h3>
                <FormattedText text={resource.methodStepDescription} />
              </>
            )}

            {contentTypesList.length > 0 && (
              <>
                <h3>{t('public.sdr.content.label02', 'Content types')}</h3>
                <p>{contentTypesList.join(', ')}</p>
              </>
            )}

            {resource.dataCollectionProtocolName && (
              <>
                <h3>{t('public.sdr.content.label.datacollectionprotocolname', 'Data collection protocol')}</h3>
                <p>{resource.dataCollectionProtocolName}</p>
              </>
            )}

            {resource.dataCollectionProtocolDoc && (
              <>
                <h3>{t('public.sdr.content.label.datacollectionprotocoldoc', 'Data collection protocol document')}</h3>
                <p>{resource.dataCollectionProtocolDoc}</p>
              </>
            )}

            {resource.suitableFor && (
              <>
                <h3>{t('public.sdr.content.label.suitablefor', 'Suitable for')}</h3>
                <p>{resource.suitableFor !== 'other' ? resource.suitableFor : (resource.suitableForOtherDetail ?? 'Other')}</p>
              </>
            )}

            <h2>{t('public.sdr.content.label03', 'Citation')}</h2>
            {resource.citation ? (
              <FormattedText text={resource.citation} />
            ) : (
              <p>No citation is provided for this resource.</p>
            )}

            {resource.rights && (
              <>
                <h3>{t('public.sdr.content.label04', 'Rights')}</h3>
                <FormattedText text={resource.rights} />
              </>
            )}

            {resource.dataGeneralizations && (
              <>
                <h3>{t('public.sdr.content.label05', 'Data generalizations')}</h3>
                <FormattedText text={resource.dataGeneralizations} />
              </>
            )}

            {resource.informationWithheld && (
              <>
                <h3>{t('public.sdr.content.label06', 'Information withheld')}</h3>
                <FormattedText text={resource.informationWithheld} />
              </>
            )}

            {resource.downloadLimit != null && resource.downloadLimit > 0 && (
              <>
                <h3>{t('public.sdr.content.label07', 'Download limit')}</h3>
                <p>The download limit for this resource is {resource.downloadLimit.toLocaleString()} records.</p>
              </>
            )}

            {resource.resourceType === 'website' && (resource.lastChecked || resource.dataCurrency) && (
              <>
                <h3>{t('public.sdr.content.label08', 'Data currency')}</h3>
                <p>
                  {resource.lastChecked && <>Last checked: {new Date(resource.lastChecked).toLocaleDateString()} </>}
                  {resource.dataCurrency && <>Data currency: {new Date(resource.dataCurrency).toLocaleDateString()}</>}
                </p>
              </>
            )}

            {isRecords && (
              <>
                <h2>{t('public.sdr.content.label09', 'Records')}</h2>
                <div>
                  <p>
                    <span id="numBiocacheRecords">{(biocacheCount ?? 0).toLocaleString()}</span>{' '}
                    records are available from {orgNameLong}.
                    {resource.lastChecked && <> Last checked: {new Date(resource.lastChecked).toLocaleDateString()}.</>}
                    {resource.dataCurrency && <> Data currency: {new Date(resource.dataCurrency).toLocaleDateString()}.</>}
                  </p>
                  {resource.publicArchiveAvailable && (
                    <a
                      href={`/ws/dataResource/${resource.uid}/download`}
                      className="btn btn-outline-dark btn-sm"
                    >
                      <i className="fa fa-download me-1" />
                      Download public archive
                    </a>
                  )}
                </div>
              </>
            )}

            <p className="text-muted small">
              {t('common.lastUpdated', 'Last updated')}: {new Date(resource.lastUpdated).toLocaleDateString()}
              {resource.userLastModified && <span> by {resource.userLastModified}</span>}
            </p>
          </div>

          {showUsageStats && (
            <div id="usage-stats" className={`tab-pane ${activeTab === 'usage' ? 'show active' : ''}`}>
              <UsageStats
                entityUid={resource.uid}
                eventId={resource.resourceType === 'website' ? '2000' : '1002'}
                loggerUrl={config?.loggerUrl}
                disableLoggerLinks={config?.disableLoggerLinks}
              />
            </div>
          )}

          {isRecords && (
            <div id="metrics" className={`section vertical-charts tab-pane ${activeTab === 'metrics' ? 'show active' : ''}`}>
              <BiocacheCharts
                entityUid={resource.uid}
                facetField="data_resource_uid"
                biocacheServicesUrl={config?.biocacheServicesUrl}
                biocacheUiUrl={config?.biocacheUiUrl}
              />
            </div>
          )}
          {isPublications && (
            <div id="metrics" className={`section vertical-charts tab-pane ${activeTab === 'metrics' ? 'show active' : ''}`}>
              <BiocacheCharts
                entityUid={resource.uid}
                facetField="annotationsUid"
                biocacheServicesUrl={config?.biocacheServicesUrl}
                biocacheUiUrl={config?.biocacheUiUrl}
              />
            </div>
          )}
        </div>
      </div>

      {/* Sidebar */}
      <div className="col-md-3">
        {/* DP logo (linked to dp show page) takes priority; fall back to resource logo */}
        {(resource.dataProvider?.logoRef?.uri ?? resource.dataProvider?.logoRef?.file) ? (
          <section className="public-metadata">
            <Link to={`/public/show/${resource.dataProvider!.uid}`}>
              <img
                className="institutionImage"
                src={resource.dataProvider!.logoRef!.uri ?? `/data/dataProvider/${resource.dataProvider!.logoRef!.file}`}
                alt={resource.dataProvider!.name}
              />
            </Link>
          </section>
        ) : (resource.logoRef?.uri ?? resource.logoRef?.file) ? (
          <img
            className="institutionImage"
            src={resource.logoRef!.uri ?? `/data/dataResource/${resource.logoRef!.file}`}
            alt={resource.name}
          />
        ) : null}

        <section className="public-metadata">
          <h3>{t(`resourceType.${resource.resourceType}`, resource.resourceType ?? '')}</h3>
        </section>

        <EntityImage
          imageRef={resource.imageRef}
          logoRef={undefined}
          entityName={resource.name}
        />

        {/* _dataLinks panel for publications (annotated records link) */}
        {isPublications && (
          <div className="public-metadata">
            <h4>{t('dataAccess.title', 'Data access')}</h4>
            <div className="dataAccess btn-group-vertical">
              <a
                href={`${config?.biocacheUiUrl ?? ''}/occurrences/search?fq=annotationsUid:${resource.uid}`}
                className="btn btn-outline-dark"
                target="_blank"
                rel="noopener noreferrer"
              >
                <i className="fa fa-list me-1" />
                {t('dataAccess.viewAnnotatedRecords', 'View annotated records')}
              </a>
            </div>
          </div>
        )}

        <DataAccessPanel
          entityUid={resource.uid}
          facetField={biocacheFacetField}
          biocacheUiUrl={config?.biocacheUiUrl}
          biocacheServicesUrl={config?.biocacheServicesUrl}
          loggerUrl={config?.loggerUrl}
          alertsUrl={config?.alertsUrl}
          recordCount={biocacheCount ?? 0}
          disableLoggerLinks={config?.disableLoggerLinks}
          disableAlertLinks={config?.disableAlertLinks}
        />

        {resource.verified && (
          <section className="public-metadata">
            <h5>
              {t('public.verified', 'Verified dataset')}{' '}
              <i className="fa fa-check-circle" style={{ color: 'green' }} />
            </h5>
          </section>
        )}

        {resource.gbifDoi && (
          <section className="public-metadata">
            <h4>{t('public.citations', 'Citations')}</h4>
            <div className="btn-group-vertical dataAccess">
              <a className="btn btn-outline-dark d-flex align-items-center gap-2" href={`https://doi.org/${resource.gbifDoi}`} target="_blank" rel="noopener noreferrer">
                <span className="badge bg-secondary flex-shrink-0">DOI</span>
                <span className="text-break">{resource.gbifDoi}</span>
              </a>
              {resource.gbifRegistryKey && (
                <a className="btn btn-outline-dark" href={`${config?.gbifWebsite ?? 'https://www.gbif.org'}/dataset/${resource.gbifRegistryKey}`} target="_blank" rel="noopener noreferrer">
                  View on GBIF
                </a>
              )}
            </div>
          </section>
        )}

        {resource.licenseType && (
          <section className="public-metadata">
            <h4>{t('public.license', 'Licence')}</h4>
            <p style={{ marginTop: 10, marginBottom: 10 }}>
              {resource.licenseType}
              {resource.licenseVersion && ` (v${resource.licenseVersion})`}
            </p>
          </section>
        )}

        {resource.beginDate && (
          <section className="public-metadata">
            <h4>{t('public.temporal', 'Temporal scope')}</h4>
            <p>{resource.beginDate}{resource.endDate ? ` - ${resource.endDate}` : ''}</p>
          </section>
        )}

        <LocationPanel
          address={resource.address}
          phone={resource.phone}
          email={resource.email}
        />

        {/* Contacts: three-way makeContactPublic logic matching GSP
            true  → show ALL resource contacts
            false/null → show public-only resource contacts; if none, fall back to data provider
            ContactsPanel fetches /entityType/entityUid/contacts — pass includePrivate as a note
            For now: true shows resource contacts; false/null shows dp contacts as fallback */}
        {resource.makeContactPublic === true ? (
          <ContactsPanel entityType="dataResource" entityUid={resource.uid} />
        ) : (
          <>
            <ContactsPanel entityType="dataResource" entityUid={resource.uid} />
            {resource.dataProvider && (
              <ContactsPanel entityType="dataProvider" entityUid={resource.dataProvider.uid} />
            )}
          </>
        )}

        {/* Website link — type-specific label matching _showDataResourceWebsite.gsp */}
        {resource.resourceType === 'species-list' ? (
          config?.speciesListToolUrl && (
            <section className="public-metadata">
              <h4>{t('public.sdr.content.label12', 'Species list')}</h4>
              <div className="webSite">
                <a className="external_icon" target="_blank" rel="noopener noreferrer"
                   href={`${config.speciesListToolUrl}${resource.uid}`}>
                  {t('public.sdr.content.link03', 'View species list')}
                </a>
              </div>
            </section>
          )
        ) : resource.resourceType === 'publications' ? (
          resource.websiteUrl && (
            <section className="public-metadata">
              <h4>{t('public.website', 'Website')}</h4>
              <div className="webSite">
                <a className="external_icon" target="_blank" rel="noopener noreferrer" href={resource.websiteUrl}>
                  {t('public.sdr.content.link05', 'Visit publication website')}
                </a>
              </div>
            </section>
          )
        ) : resource.websiteUrl ? (
          <section className="public-metadata">
            <h4>{t('public.website', 'Website')}</h4>
            <div className="webSite">
              <a className="external" target="_blank" rel="noopener noreferrer" href={resource.websiteUrl}>
                {t('public.sdr.content.link04', 'Visit website')}
              </a>
            </div>
          </section>
        ) : null}

        <NetworkMembership networkMembership={resource.networkMembership} entityType="dataResource" />

        <AttributionList entityUid={resource.uid} />

        <ExternalIds entityType="dataResource" entityUid={resource.uid} />
      </div>
    </div>
    </>
  );
}
