import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import { reportsApi, type MapLocationItem } from '../../api/endpoints/reports';
import { useConfig } from '../../hooks/useConfig';
import ReportLayout from './ReportLayout';

export default function MapReport() {
  const { data: config } = useConfig();
  const [search, setSearch] = useState('');

  const { data, isLoading, error } = useQuery<{ locations: MapLocationItem[] }>({
    queryKey: ['reports', 'map'],
    queryFn: reportsApi.map,
    staleTime: 5 * 60 * 1000,
  });

  const mapCenter: [number, number] = [
    config?.collectionsMap?.centreMapLat ?? -28.0,
    config?.collectionsMap?.centreMapLon ?? 133.0,
  ];
  const mapZoom = config?.collectionsMap?.defaultZoom ?? 4;

  const tileUrl = config?.cartodbPattern
    ? config.cartodbPattern
    : config?.mapboxAccessToken
      ? `https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/{z}/{x}/{y}?access_token=${config.mapboxAccessToken}`
      : 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';

  const tileAttribution = config?.cartodbPattern
    ? '&copy; <a href="https://carto.com/">CARTO</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    : config?.mapboxAccessToken
      ? '&copy; <a href="https://www.mapbox.com/">Mapbox</a>'
      : '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

  const mappable = useMemo(
    () => (data?.locations ?? []).filter((l) => l.latitude !== -1 && l.longitude !== -1),
    [data],
  );

  const unmappable = useMemo(
    () => (data?.locations ?? []).filter((l) => l.latitude === -1 || l.longitude === -1),
    [data],
  );

  const filteredUnmappable = useMemo(() => {
    const q = search.toLowerCase();
    return unmappable.filter((l) => l.name.toLowerCase().includes(q));
  }, [unmappable, search]);

  return (
    <ReportLayout title="Collection Locations" isLoading={isLoading} error={error as Error | null}>
      <p className="text-muted">
        <strong>{mappable.length}</strong> collections with coordinates &nbsp;|&nbsp;
        <strong>{unmappable.length}</strong> without
      </p>

      {/* Map */}
      <MapContainer
        center={mapCenter}
        zoom={mapZoom}
        style={{ height: '600px', width: '100%' }}
        scrollWheelZoom={true}
      >
        <TileLayer attribution={tileAttribution} url={tileUrl} />
        {mappable.map((loc) => (
          <Marker key={loc.link} position={[loc.latitude, loc.longitude]}>
            <Popup>
              <Link to={`/public/showCollection/${loc.link}`}>{loc.name}</Link>
              {loc.streetAddress && (
                <p className="mb-0 small text-muted">{loc.streetAddress}</p>
              )}
            </Popup>
          </Marker>
        ))}
      </MapContainer>

      {/* Unmappable list */}
      {unmappable.length > 0 && (
        <div className="mt-4">
          <h4>Collections without coordinates ({unmappable.length})</h4>
          <input
            type="text"
            className="form-control mb-2"
            placeholder="Search…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <table className="table table-sm table-bordered table-hover">
            <thead className="table-light">
              <tr>
                <th>Name</th>
                <th>Address</th>
              </tr>
            </thead>
            <tbody>
              {filteredUnmappable.map((loc) => (
                <tr key={loc.link}>
                  <td>
                    <Link to={`/public/showCollection/${loc.link}`}>{loc.name}</Link>
                  </td>
                  <td>{loc.streetAddress || <span className="text-muted">—</span>}</td>
                </tr>
              ))}
              {filteredUnmappable.length === 0 && (
                <tr>
                  <td colSpan={2} className="text-muted text-center">
                    No results
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </ReportLayout>
  );
}
