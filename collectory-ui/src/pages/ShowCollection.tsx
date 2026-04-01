import { useState, useEffect } from 'react';
import apiClient from '../api/client';
import { useParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useCollection } from '../hooks/useCollections';
import { useConfig } from '../hooks/useConfig';
import EntityImage from '../components/public/EntityImage';
import ContactsPanel from '../components/public/ContactsPanel';
import DataAccessPanel from '../components/public/DataAccessPanel';
import RecordsMetrics from '../components/public/RecordsMetrics';
import UsageStats from '../components/public/UsageStats';
import BiocacheCharts from '../components/public/BiocacheCharts';
import TaxonTree from '../components/public/TaxonTree';
import ExternalIds from '../components/public/ExternalIds';
import LocationPanel from '../components/public/LocationPanel';
import EditButton from '../components/public/EditButton';
import NetworkMembership from '../components/public/NetworkMembership';
import AttributionList from '../components/public/AttributionList';
import Breadcrumb from '../components/public/Breadcrumb';
import FormattedText from '../components/common/FormattedText';

/** Parse a JSON array string into a comma-separated display string */
function formatJsonArray(value: unknown): string {
  if (!value) return '';
  if (typeof value !== 'string') return String(value);
  if (!value.startsWith('[')) return value;
  try {
    const arr = JSON.parse(value) as string[];
    if (arr.length === 0) return value;
    if (arr.length === 1) return arr[0] ?? value;
    return arr.slice(0, -1).join(', ') + ' and ' + arr[arr.length - 1];
  } catch {
    return value;
  }
}

/**
 * Format a collection name by appending "Collection" if it doesn't already
 * contain "collection" or "herbari" (mirrors the Grails cl:collectionName tag).
 */
function formatCollectionName(name: string, prefix = ''): string {
  const lower = name.toLowerCase();
  if (lower.includes('collection') || lower.includes('herbari')) {
    return prefix ? `${prefix} ${name}` : name;
  }
  return prefix ? `${prefix} ${name} Collection` : `${name} Collection`;
}

/** Map collectionType to a noun */
function collectionTypeNoun(collectionType?: unknown): string {
  if (!collectionType || typeof collectionType !== 'string') return 'specimens';
  const lower = collectionType.toLowerCase();
  if (lower.includes('preserved')) return 'specimens';
  if (lower.includes('culture')) return 'cultures';
  if (lower.includes('tissue')) return 'samples';
  return 'specimens';
}

/** Format percentage */
function percentIfKnown(dividend?: number, divisor?: number): string {
  if (dividend == null || divisor == null || divisor === 0 || dividend === -1 || divisor === -1)
    return '';
  return `${Math.round((dividend / divisor) * 100)}%`;
}

interface BiocacheImage {
  occurrenceID: string;
  uuid: string;
  image?: string;
  smallImageUrl?: string;
  largeImageUrl?: string;
  thumbnailUrl?: string;
}

export default function ShowCollection() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const { data: collection, isLoading, error } = useCollection(id ?? '');
  const { data: config } = useConfig();
  const [activeTab, setActiveTab] = useState('overview');
  const [images, setImages] = useState<BiocacheImage[]>([]);
  const [imagesLoading, setImagesLoading] = useState(false);
  const [hasImages, setHasImages] = useState(false);
  const [lsidVisible, setLsidVisible] = useState(false);

  // Fetch images when Images tab is activated
  useEffect(() => {
    if (activeTab !== 'images' || !collection?.uid) return;
    if (images.length > 0 || imagesLoading) return;

    setImagesLoading(true);
    const q = `collection_uid:${collection.uid}`;

    apiClient.get<{ facetResults?: Array<{ fieldName: string; fieldResult?: Array<{ label: string; count: number }> }> }>(
      `/proxy/biocache/occurrences/search`,
      { params: { q, facets: 'multimedia', pageSize: 0 } },
    )
      .then(({ data }) => {
        const multimediaFacet = data.facetResults?.find(
          (f) => f.fieldName === 'multimedia',
        );
        const imageEntry = multimediaFacet?.fieldResult?.find(
          (r) => r.label === 'Image',
        );
        if (imageEntry && imageEntry.count > 0) {
          setHasImages(true);
          return apiClient.get<{ occurrences?: Record<string, unknown>[] }>(
            `/proxy/biocache/occurrences/search`,
            { params: { q, fq: 'multimedia:Image', pageSize: 20, im: true } },
          )
            .then(({ data: imgData }) => {
              const occurrences = imgData.occurrences ?? [];
              const imgs: BiocacheImage[] = occurrences
                .filter((occ) => occ.image || occ.smallImageUrl || occ.thumbnailUrl)
                .map((occ) => ({
                  occurrenceID: String(occ.occurrenceID ?? occ.uuid ?? ''),
                  uuid: String(occ.uuid ?? ''),
                  image: occ.image as string | undefined,
                  smallImageUrl: occ.smallImageUrl as string | undefined,
                  largeImageUrl: occ.largeImageUrl as string | undefined,
                  thumbnailUrl: occ.thumbnailUrl as string | undefined,
                }));
              setImages(imgs);
            });
        } else {
          setHasImages(false);
        }
      })
      .catch(() => {
        setHasImages(false);
      })
      .finally(() => {
        setImagesLoading(false);
      });
  }, [activeTab, collection?.uid, images.length, imagesLoading]);

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

  if (error || !collection) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">
          {t('error.notFound', 'Entity not found or an error occurred.')}
        </div>
      </div>
    );
  }

  const subCollections = collection.subCollections
    ? (() => { try { return JSON.parse(collection.subCollections) as Array<{ name: string; description?: string }>; } catch { return []; } })()
    : [];

  const showLoggerTab = !config?.disableLoggerLinks;
  const noun = collectionTypeNoun(collection.collectionType);
  const collName = formatCollectionName(collection.name, 'the');
  const collNameCap = formatCollectionName(collection.name, 'The');

  const formattedKingdomCoverage = collection.kingdomCoverage
    ? formatJsonArray(collection.kingdomCoverage)
    : null;

  const formattedScientificNames = collection.scientificNames
    ? formatJsonArray(collection.scientificNames)
    : null;

  // Temporal span text
  const temporalSpan = (() => {
    if (!collection.startDate && !collection.endDate) return null;
    let text = '';
    if (collection.startDate) {
      text = `The collection was established in ${collection.startDate}`;
      if (collection.endDate) {
        text += ` and continued until ${collection.endDate}.`;
      } else {
        text += ' and is ongoing.';
      }
    } else if (collection.endDate) {
      text = `The collection continued until ${collection.endDate}.`;
    }
    return text;
  })();

  return (
    <>
    <Breadcrumb
      parent={{ url: '/', label: t('breadcrumb.collections', 'Collections') }}
      current={collection.name}
    />
    <div className="row mt-3">
      <div className="col-md-9">
        <h1>{collection.name}</h1>
        <EditButton entityType="collection" uid={collection.uid} />
        {collection.institution && (
          <h3>
            <Link to={`/public/show/${collection.institution.uid}`}>
              {collection.institution.name}
            </Link>
          </h3>
        )}
        {collection.acronym && (
          <span className="acronym">Acronym: {collection.acronym}</span>
        )}
        {collection.guid?.startsWith('urn:lsid:') && (
          <span className="lsid">
            {' '}
            <a
              href="#lsidText"
              className="local"
              title="Life Science Identifier (pop-up)"
              onClick={(e) => { e.preventDefault(); setLsidVisible((v) => !v); }}
            >
              {t('public.lsid', 'LSID')}
            </a>
            {lsidVisible && (
              <div id="lsidText" style={{ textAlign: 'left', marginTop: 8 }}>
                <b>
                  <a
                    className="external_icon"
                    href="https://wayback.archive.org/web/20100515104710/http://lsids.sourceforge.net:80/"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {t('public.lsidtext.link', 'Life Science Identifiers')}:
                  </a>
                </b>
                <p style={{ margin: '10px 0' }}>
                  <a href={collection.guid} target="_blank" rel="noopener noreferrer">
                    {collection.guid}
                  </a>
                </p>
                <p style={{ fontSize: 12 }}>
                  {t('public.lsidtext.des', 'This is the Life Science Identifier for this record')}
                </p>
              </div>
            )}
          </span>
        )}

        <div className="tabbable">
          <ul className="nav nav-tabs" id="overviewTabs">
            <li className="nav-item">
              <a
                className={`nav-link ${activeTab === 'overview' ? 'active' : ''}`}
                href="#basic-metadata"
                data-bs-toggle="tab"
                onClick={(e) => { e.preventDefault(); setActiveTab('overview'); }}
              >
                {t('public.show.overviewtabs.overview', 'Overview')}
              </a>
            </li>
            {showLoggerTab && (
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
                className={`nav-link ${activeTab === 'records' ? 'active' : ''}`}
                href="#metrics"
                data-bs-toggle="tab"
                onClick={(e) => { e.preventDefault(); setActiveTab('records'); }}
              >
                {t('public.show.overviewtabs.records', 'Records')}
              </a>
            </li>
            {hasImages && (
              <li className="nav-item">
                <a
                  className={`nav-link ${activeTab === 'images' ? 'active' : ''}`}
                  href="#imagesTab"
                  data-bs-toggle="tab"
                  onClick={(e) => { e.preventDefault(); setActiveTab('images'); }}
                >
                  {t('public.show.overviewtabs.images', 'Images')}
                </a>
              </li>
            )}
          </ul>
        </div>

        <div className="tab-content">
          {/* Overview tab */}
          <div id="basic-metadata" className={`tab-pane ${activeTab === 'overview' ? 'show active' : ''}`}>
            <div id="overview-content">
              <h2>{t('public.des', 'Description')}</h2>
              {collection.pubDescription && <FormattedText text={collection.pubDescription} />}
              {collection.techDescription && <FormattedText text={collection.techDescription} />}
              {temporalSpan && <p>{temporalSpan}</p>}

              <h2>{t('public.show.oc.label02', 'Focus')}</h2>
              {collection.focus && <FormattedText text={collection.focus} />}
              {formattedScientificNames ? (
                <p>{collNameCap} includes members from the following taxa:<br />{formattedScientificNames}.</p>
              ) : formattedKingdomCoverage ? (
                <p>{collNameCap} includes members from the following kingdoms: {formattedKingdomCoverage}.</p>
              ) : null}

              {(collection.geographicDescription || collection.states) && (
                <>
                  <h2>{t('public.show.oc.label03', 'Geographic range')}</h2>
                  {collection.geographicDescription && <p>{collection.geographicDescription}</p>}
                  {collection.states && (
                    <p>
                      {collection.states.trim().toLowerCase() === 'all states' ||
                       collection.states.trim().toLowerCase() === 'all states and territories'
                        ? 'All states and territories'
                        : collection.states}
                    </p>
                  )}
                  {collection.westCoordinate != null && collection.westCoordinate !== -1 && (
                    <p>Western limit: {collection.westCoordinate}&deg;</p>
                  )}
                  {collection.eastCoordinate != null && collection.eastCoordinate !== -1 && (
                    <p>Eastern limit: {collection.eastCoordinate}&deg;</p>
                  )}
                  {collection.northCoordinate != null && collection.northCoordinate !== -1 && (
                    <p>Northern limit: {collection.northCoordinate}&deg;</p>
                  )}
                  {collection.southCoordinate != null && collection.southCoordinate !== -1 && (
                    <p>Southern limit: {collection.southCoordinate}&deg;</p>
                  )}
                </>
              )}

              <h2>Number of {noun} in the collection</h2>
              {collection.numRecords != null && collection.numRecords !== -1 && (
                <p>
                  The estimated number of {noun} in {collName} is {collection.numRecords.toLocaleString()}.
                </p>
              )}
              {collection.numRecordsDigitised != null && collection.numRecordsDigitised !== -1 && (
                <p>
                  Of these {collection.numRecordsDigitised.toLocaleString()} are databased.
                  {' '}This represents {percentIfKnown(collection.numRecordsDigitised, collection.numRecords)} of the collection.
                </p>
              )}
              <p>These figures may not be up-to-date.</p>

              {subCollections.length > 0 && (
                <>
                  <h2>{t('public.show.oc.label06', 'Sub-collections')}</h2>
                  <p>{collNameCap} contains these significant collections:</p>
                  <ul>
                    {subCollections.map((sc, i) => (
                      <li key={i}>
                        <strong>{sc.name}</strong>
                        {sc.description && <span> - {sc.description}</span>}
                      </li>
                    ))}
                  </ul>
                </>
              )}

              <p className="text-muted small">
                {t('common.lastUpdated', 'Last updated')}: {new Date(collection.lastUpdated).toLocaleDateString()}
                {collection.userLastModified && (
                  <span> by {collection.userLastModified}</span>
                )}
              </p>
            </div>
          </div>

          {/* Usage Stats tab */}
          {showLoggerTab && (
            <div id="usage-stats" className={`tab-pane ${activeTab === 'usage' ? 'show active' : ''}`}>
              <UsageStats
                entityUid={collection.uid}
                eventId="1002"
                loggerUrl={config?.loggerUrl}
                disableLoggerLinks={config?.disableLoggerLinks}
              />
            </div>
          )}

          {/* Records/Metrics tab */}
          <div id="metrics" className={`tab-pane ${activeTab === 'records' ? 'show active' : ''}`}>
            {(() => {
              const facetField = config?.isPipelinesCompatible ? 'collectionUid' : 'collection_uid';
              return (
                <>
                  <RecordsMetrics
                    entityUid={collection.uid}
                    facetField={facetField}
                    biocacheServicesUrl={config?.biocacheServicesUrl}
                    biocacheUiUrl={config?.biocacheUiUrl}
                    numRecords={collection.numRecords}
                    numRecordsDigitised={collection.numRecordsDigitised}
                    collectionName={collName}
                    noun={noun}
                    inexactMappingWarning={
                      collection.recordsProviderMapping && !collection.recordsProviderMapping.exact
                        ? (collection.recordsProviderMapping.warning ?? '')
                        : undefined
                    }
                  />
                  <BiocacheCharts
                    entityUid={collection.uid}
                    facetField={facetField}
                    biocacheServicesUrl={config?.biocacheServicesUrl}
                    biocacheUiUrl={config?.biocacheUiUrl}
                  />
                  <TaxonTree
                    entityUid={collection.uid}
                    facetField={facetField}
                    biocacheServicesUrl={config?.biocacheServicesUrl}
                    biocacheUiUrl={config?.biocacheUiUrl}
                    bieBaseUrl={config?.bieBaseUrl}
                  />
                </>
              );
            })()}
          </div>

          {/* Images tab */}
          <div id="imagesTab" className={`tab-pane ${activeTab === 'images' ? 'show active' : ''}`}>
            <h2>{t('public.show.it.title', 'Images from this collection')}</h2>
            {imagesLoading ? (
              <div className="d-flex justify-content-center my-4">
                <div className="spinner-border" role="status">
                  <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
                </div>
              </div>
            ) : images.length > 0 ? (
              <div id="imagesList">
                {images.map((img, i) => (
                  <span key={i} className="imgCon" style={{ display: 'inline-block', marginRight: 8, textAlign: 'center', backgroundColor: '#DDD', padding: 5, marginBottom: 8 }}>
                    <a
                      href={`${config?.biocacheUiUrl ?? ''}/occurrences/${img.uuid}`}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <img
                        src={img.thumbnailUrl ?? img.smallImageUrl ?? img.image ?? ''}
                        alt={`Occurrence ${img.uuid}`}
                        style={{ maxHeight: 150 }}
                        loading="lazy"
                      />
                    </a>
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-muted">
                {t('images.noImages', 'No images available for this collection.')}
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Sidebar */}
      <div className="col-md-3">
        {/* Institution logo */}
        {(collection.institution?.logoRef?.uri ?? collection.institution?.logoRef?.file) && (
          <section className="public-metadata">
            <Link to={`/public/show/${collection.institution!.uid}`}>
              <img
                className="institutionImage"
                src={collection.institution!.logoRef!.uri ?? `/data/institution/${collection.institution!.logoRef!.file}`}
                alt={collection.institution!.name}
              />
            </Link>
          </section>
        )}

        <EntityImage
          imageRef={collection.imageRef}
          logoRef={collection.logoRef}
          entityName={collection.name}
        />

        <DataAccessPanel
          entityUid={collection.uid}
          facetField={config?.isPipelinesCompatible ? 'collectionUid' : 'collection_uid'}
          biocacheUiUrl={config?.biocacheUiUrl}
          biocacheServicesUrl={config?.biocacheServicesUrl}
          loggerUrl={config?.loggerUrl}
          alertsUrl={config?.alertsUrl}
          recordCount={collection.numRecords}
          disableLoggerLinks={config?.disableLoggerLinks}
          disableAlertLinks={config?.disableAlertLinks}
        />

        <LocationPanel
          address={collection.address}
          phone={collection.phone}
          email={collection.email}
        />

        <ContactsPanel entityType="collection" entityUid={collection.uid} />

        {(collection.websiteUrl || collection.institution?.websiteUrl) && (
          <section className="public-metadata">
            <h4>{t('public.website', 'Website')}</h4>
            {collection.websiteUrl && (
              <div className="webSite">
                <a className="external" rel="nofollow" target="_blank" href={collection.websiteUrl}>
                  Visit collection website
                </a>
              </div>
            )}
            {collection.institution?.websiteUrl && (
              <div className="webSite">
                <a className="external" rel="nofollow" target="_blank" href={collection.institution.websiteUrl}>
                  Visit the {collection.institution.institutionType === 'governmentDepartment' ? "department's" : "institution's"} website
                </a>
              </div>
            )}
          </section>
        )}

        <NetworkMembership networkMembership={collection.networkMembership} entityType="collection" />

        <AttributionList entityUid={collection.uid} />

        <ExternalIds entityType="collection" entityUid={collection.uid} />
      </div>
    </div>
    </>
  );
}
