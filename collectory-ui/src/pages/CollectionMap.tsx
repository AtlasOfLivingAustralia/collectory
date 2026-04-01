import { useState, useMemo, useCallback, useEffect } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { MapContainer, TileLayer, CircleMarker, Popup } from 'react-leaflet';
import MarkerClusterGroup from 'react-leaflet-markercluster';
import 'leaflet.markercluster/dist/MarkerCluster.css';
import 'leaflet.markercluster/dist/MarkerCluster.Default.css';
import type { Feature, FeatureCollection, Point } from 'geojson';
import apiClient from '../api/client';
import { useConfig } from '../hooks/useConfig';
import { useAuth } from '../auth/useAuth';
import Breadcrumb from '../components/public/Breadcrumb';

/* ------------------------------------------------------------------ */
/* Types                                                               */
/* ------------------------------------------------------------------ */

interface MapFeatureProperties {
  name: string;
  uid: string;
  entityType: string;
  instName: string;
  instUid: string;
  acronym?: string;
  desc?: string;
  isMappable: boolean;
  // For institution features: nested collections list
  collections?: Array<{ uid: string; name: string; url?: string }>;
  collectionCount?: number;
}

type MapFeature = Feature<Point, MapFeatureProperties>;
type MapFeatureCollection = FeatureCollection<Point, MapFeatureProperties>;

/* ------------------------------------------------------------------ */
/* Filter definitions                                                  */
/* ------------------------------------------------------------------ */

interface FilterDef {
  key: string;
  labelKey: string;
  defaultLabel: string;
  subtitleKey?: string;
  defaultSubtitle?: string;
  test: (desc: string) => boolean;
}

const FILTERS: FilterDef[] = [
  { key: 'all', labelKey: 'map.filter.all', defaultLabel: 'All collections', test: () => true },
  { key: 'fauna', labelKey: 'map.filter.fauna', defaultLabel: 'Fauna', subtitleKey: 'map.filter.fauna.sub', defaultSubtitle: 'Mammals, birds, reptiles, fish, amphibians and invertebrates.', test: (d) => /fauna/i.test(d) },
  { key: 'insects', labelKey: 'map.filter.insects', defaultLabel: 'Insects', subtitleKey: 'map.filter.insects.sub', defaultSubtitle: 'Insects, spiders, mites and some other arthropods.', test: (d) => /entomology|insects/i.test(d) },
  { key: 'microbes', labelKey: 'map.filter.microbes', defaultLabel: 'Microorganisms', subtitleKey: 'map.filter.microbes.sub', defaultSubtitle: 'Protists, bacteria, viruses, microfungi and microalgae.', test: (d) => /microbes|microbiology/i.test(d) },
  { key: 'plants', labelKey: 'map.filter.plants', defaultLabel: 'Plants', subtitleKey: 'map.filter.plants.sub', defaultSubtitle: 'Vascular plants, algae, fungi, lichens and bryophytes.', test: (d) => /plants|botany|herbari/i.test(d) },
  { key: 'fungi', labelKey: 'map.filter.fungi', defaultLabel: 'Fungi', test: (d) => /fungi|mycol/i.test(d) },
];

/* ------------------------------------------------------------------ */
/* Data hook                                                           */
/* ------------------------------------------------------------------ */

function useMapFeatures() {
  return useQuery<MapFeatureCollection>({
    queryKey: ['mapFeatures'],
    queryFn: () => apiClient.get<MapFeatureCollection>('/public/mapFeatures.json').then((r) => r.data),
    staleTime: 5 * 60 * 1000,
  });
}

/* ------------------------------------------------------------------ */
/* Component                                                           */
/* ------------------------------------------------------------------ */

export default function CollectionMap() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialFilter = searchParams.get('start') ?? 'all';
  const [activeFilter, setActiveFilter] = useState(initialFilter);
  const [viewMode, setViewMode] = useState<'map' | 'list'>('map');
  const [popupInfo, setPopupInfo] = useState<MapFeatureProperties | null>(null);
  const [popupPosition, setPopupPosition] = useState<[number, number] | null>(null);

  const { data: config } = useConfig();
  const { isAdmin, isEditor } = useAuth();
  const { data: featureCollection, isLoading, error } = useMapFeatures();

  // Map settings from backend config with sensible defaults
  const mapCenter: [number, number] = [
    config?.collectionsMap?.centreMapLat ?? -28.2,
    config?.collectionsMap?.centreMapLon ?? 134.0,
  ];
  const mapZoom = config?.collectionsMap?.defaultZoom ?? 4;

  // Tile layer: prefer cartodbPattern, then Mapbox if token available, else OSM
  const tileUrl = config?.cartodbPattern
    ? config.cartodbPattern
    : config?.mapboxAccessToken
      ? `https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/{z}/{x}/{y}?access_token=${config.mapboxAccessToken}`
      : 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
  const tileAttribution = config?.cartodbPattern
    ? '&copy; <a href="https://carto.com/">CARTO</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    : config?.mapboxAccessToken
      ? '&copy; <a href="https://www.mapbox.com/">Mapbox</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
      : '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

  // Sync filter to URL
  useEffect(() => {
    const current = searchParams.get('start');
    if (activeFilter === 'all' && current) {
      searchParams.delete('start');
      setSearchParams(searchParams, { replace: true });
    } else if (activeFilter !== 'all' && current !== activeFilter) {
      setSearchParams({ start: activeFilter }, { replace: true });
    }
  }, [activeFilter, searchParams, setSearchParams]);

  const handleFilterChange = useCallback((key: string) => {
    setActiveFilter(key);
  }, []);

  const currentFilterDef = useMemo(
    () => FILTERS.find((f) => f.key === activeFilter) ?? FILTERS[0]!,
    [activeFilter],
  );

  // Filtered features
  const filteredFeatures = useMemo<MapFeature[]>(() => {
    if (!featureCollection?.features) return [];
    return featureCollection.features.filter((f) =>
      currentFilterDef.test(f.properties.desc ?? ''),
    ) as MapFeature[];
  }, [featureCollection, currentFilterDef]);

  const mappableFeatures = useMemo(
    () => filteredFeatures.filter((f) => f.properties.isMappable),
    [filteredFeatures],
  );

  const unmappableFeatures = useMemo(
    () => filteredFeatures.filter((f) => !f.properties.isMappable),
    [filteredFeatures],
  );

  // Match Grails map.js: count nested collections within institution features
  const totalCollectionsCount = useMemo(
    () => featureCollection?.features?.reduce((sum, f) =>
      sum + (f.properties.entityType === 'institution' ? (f.properties.collectionCount ?? 1) : 1), 0) ?? 0,
    [featureCollection],
  );

  const unmappableCollectionsCount = useMemo(
    () => unmappableFeatures.reduce((sum, f) =>
      sum + (f.properties.entityType === 'institution' ? (f.properties.collectionCount ?? 1) : 1), 0),
    [unmappableFeatures],
  );

  const handleMarkerClick = useCallback((props: MapFeatureProperties, lat: number, lng: number) => {
    setPopupInfo(props);
    setPopupPosition([lat, lng]);
  }, []);

  if (error) {
    return (
      <div className="container-fluid mt-3">
        <div className="alert alert-danger">{t('map.error', 'Failed to load map data.')}</div>
      </div>
    );
  }

  const regionName = config?.regionName ?? 'Australia';
  const projectName = config?.projectName ?? 'Atlas';

  return (
    <>
    <Breadcrumb current={t('public.map3.title', 'Natural History Collections')} />
    <div id="content" style={{ marginBottom: '40px' }}>
      {/* Header — matches map.gsp #header .hrgroup */}
      <div id="header">
        <div className="section full-width">
          <div className="hrgroup">
            <h1>{t('public.map3.header.title', `${regionName}'s natural history collections`)}</h1>
            <p>
              {t('public.map3.header.des', `Learn about the institution, the collections they hold and view records of specimens that have been databased. Currently only the collections of ${projectName} partners are shown. Over time this list will expand to include all natural history collections in ${regionName}.`)}
            </p>
          </div>
        </div>
      </div>

      <div className="row">
        {/* Sidebar — matches map.gsp col-md-4 layout */}
        <div className="col-md-4">
          <div className="section">
            <p>{t('map.intro', 'Click a button to only show those organisms.')}</p>
          </div>
          <div className="section filter-buttons">
            {FILTERS.map((f) => (
              <div
                key={f.key}
                className={`${f.key}${activeFilter === f.key ? ' selected' : ''}`}
                id={f.key === 'insects' ? 'entomology' : f.key}
                onClick={() => handleFilterChange(f.key)}
              >
                <h2>
                  <a href="" onClick={(e) => e.preventDefault()}>
                    {t(f.labelKey, f.defaultLabel)}
                    {f.key === 'all' ? (
                      <span id="allButtonTotal">
                        {t('map.showAll', 'Show all')} {totalCollectionsCount || ''}
                      </span>
                    ) : f.defaultSubtitle ? (
                      <span>{t(f.subtitleKey ?? '', f.defaultSubtitle)}</span>
                    ) : null}
                  </a>
                </h2>
              </div>
            ))}
          </div>

          {/* Footer counts — matches map.gsp #collectionTypesFooter */}
          {!isLoading && featureCollection && (
            <div id="collectionTypesFooter">
              <h4 className="collectionsCount">
                <span id="numFeatures">{totalCollectionsCount} collections in total.</span>
              </h4>
              {unmappableCollectionsCount > 0 && (
                <h4 className="collectionsCount">
                  <span id="numUnMappable">{unmappableCollectionsCount} collections cannot be mapped.</span>
                </h4>
              )}
            </div>
          )}

          {/* Admin link — matches map.gsp #adminLink */}
          {(isAdmin || isEditor) && (
            <div id="adminLink" className="dropdown" style={{ marginTop: '110px' }}>
              <Link to="/manage/list" style={{ color: '#DDDDDD', marginTop: '80px' }}>
                {t('public.map3.adminlink', 'Admin')}
              </Link>
            </div>
          )}
        </div>

        {/* Main area — matches map.gsp col-md-8 */}
        <div className="col-md-8" id="map-list-col">
          {/* View toggle tabs — matches map.gsp nav-tabs structure */}
          <div className="tabbable">
            <ul className="nav nav-tabs" id="home-tabs">
              <li className="nav-item">
                <a
                  className={`nav-link ${viewMode === 'map' ? 'active' : ''}`}
                  href="#map"
                  data-bs-toggle="tab"
                  onClick={(e) => { e.preventDefault(); setViewMode('map'); }}
                >
                  {t('map.tabMap', 'Map')}
                </a>
              </li>
              <li className="nav-item">
                <a
                  className={`nav-link ${viewMode === 'list' ? 'active' : ''}`}
                  href="#list"
                  data-bs-toggle="tab"
                  onClick={(e) => { e.preventDefault(); setViewMode('list'); }}
                >
                  {t('map.tabList', 'List')}
                </a>
              </li>
            </ul>
          </div>

          <div className="tab-content">
          {isLoading && (
            <div className="d-flex justify-content-center p-5">
              <div className="spinner-border" role="status">
                <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
              </div>
            </div>
          )}

          {!isLoading && viewMode === 'map' && (
            <div className="tab-pane show active" id="map">
            <div className="map-column">
              <div className="section">
                <p style={{ width: '100%', paddingBottom: 8 }}>
                  {t('map.mapDescription', 'Click on a map pin to see the collections at that location. Use the map controls to zoom into an area of interest. Or drag your mouse while holding the shift key to zoom to an area.')}
                </p>
              </div>
            </div>
            <MapContainer
              center={mapCenter}
              zoom={mapZoom}
              style={{ height: '600px', width: '100%' }}
              scrollWheelZoom={true}
            >
              <TileLayer
                attribution={tileAttribution}
                url={tileUrl}
              />
              {mappableFeatures.length > 0 && (
                <MarkerClusterGroup key={activeFilter}>
                  {mappableFeatures.map((f) => {
                    const lng = f.geometry.coordinates[0] as number;
                    const lat = f.geometry.coordinates[1] as number;
                    return (
                      <CircleMarker
                        key={f.properties.uid}
                        center={[lat, lng]}
                        radius={8}
                        pathOptions={{ color: '#e06f00', fillColor: '#e06f00', fillOpacity: 0.9 }}
                        eventHandlers={{
                          click: () => handleMarkerClick(f.properties, lat, lng),
                        }}
                      />
                    );
                  })}
                </MarkerClusterGroup>
              )}
              {popupInfo && popupPosition && (
                <Popup
                  position={popupPosition}
                  eventHandlers={{ remove: () => setPopupInfo(null) }}
                >
                  <div>
                    <h6>
                      <Link to={`/public/showCollection/${popupInfo.uid}`}>{popupInfo.name}</Link>
                    </h6>
                    {popupInfo.instName && (
                      <p className="mb-1">
                        <Link to={`/public/showInstitution/${popupInfo.instUid}`}>
                          {popupInfo.instName}
                        </Link>
                      </p>
                    )}
                    {popupInfo.desc && <p className="mb-0 small text-muted">{popupInfo.desc}</p>}
                  </div>
                </Popup>
              )}
            </MapContainer>
            </div>
          )}

          {!isLoading && viewMode === 'list' && (
            <div className="nameList section" id="names">
              <p>
                <span id="numFilteredCollections">
                  {filteredFeatures.length === 0
                    ? t('map.noCollectionsSelected', 'No collections are selected')
                    : filteredFeatures.length === 1
                      ? `1 ${t('map.collectionIsListed', 'collection is listed alphabetically')}`
                      : `${filteredFeatures.length} ${t('map.collectionsAreListed', 'collections are listed alphabetically')}`}
                </span>
                .{' '}
                {t('map.listDescription', 'Click on a collection name to see more details including the digitised specimen records for the collection. Collections not shown on the map (due to lack of location information) are marked')}
                {' '}<img style={{ verticalAlign: 'middle' }} src="/images/map/nomap.gif" alt="not mappable" />.
              </p>

              {filteredFeatures.length === 0 && (
                <p className="text-muted">{t('map.noResults', 'No collections found for this filter.')}</p>
              )}

              <ul style={{ paddingLeft: '15px' }}>
                {filteredFeatures.map((f) => {
                  const props = f.properties;
                  // Institution feature with nested collections
                  if (props.entityType === 'institution' && props.collections && props.collections.length > 0) {
                    return (
                      <li key={props.uid}>
                        <Link className="highlight" to={`/public/showInstitution/${props.uid}`}>
                          {props.name}
                        </Link>
                        <ul>
                          {props.collections.map((col: { uid: string; name: string; url?: string }) => (
                            <li key={col.uid}>
                              <Link to={`/public/showCollection/${col.uid}`}>{col.name}</Link>
                            </li>
                          ))}
                        </ul>
                      </li>
                    );
                  }
                  // Orphan collection or data provider feature (no nested collections)
                  return (
                    <li key={props.uid}>
                      <Link to={`/public/show/${props.uid}`}>{props.name}</Link>
                      {!props.isMappable && (
                        <img
                          style={{ verticalAlign: 'middle', marginLeft: '4px' }}
                          src="/images/map/nomap.gif"
                          alt={t('map.unmappable', 'Not mappable')}
                        />
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
          </div>{/* close tab-content */}
        </div>
      </div>
    </div>
    </>
  );
}
