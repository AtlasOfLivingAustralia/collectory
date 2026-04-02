import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useParams, useNavigate, Link } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { MapContainer, TileLayer, Marker, useMapEvents } from 'react-leaflet';
import type { LatLngExpression } from 'leaflet';
import apiClient from '../../api/client';
import { updateEntity } from '../../api/endpoints/mutations';
import type { Institution } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import { useConfig } from '../../hooks/useConfig';
import Breadcrumb from '../../components/public/Breadcrumb';

const ENTITY_CONFIG: Record<string, { apiPath: string; singular: string; showPath: string }> = {
  collection: { apiPath: 'collection', singular: 'Collection', showPath: '/public/showCollection' },
  institution: { apiPath: 'institution', singular: 'Institution', showPath: '/public/showInstitution' },
  dataResource: { apiPath: 'dataResource', singular: 'Data Resource', showPath: '/public/showDataResource' },
  dataProvider: { apiPath: 'dataProvider', singular: 'Data Provider', showPath: '/public/showDataProvider' },
  dataHub: { apiPath: 'dataHub', singular: 'Data Hub', showPath: '/public/showDataHub' },
};

/** Default map centre: Australia */
const DEFAULT_CENTER: LatLngExpression = [-25.2744, 133.7751];
const DEFAULT_ZOOM = 4;

interface LocationFormValues {
  street: string;
  postBox: string;
  city: string;
  addressState: string;
  postcode: string;
  country: string;
  latitude: string;
  longitude: string;
  altitude: string;
  state: string;
  email: string;
  phone: string;
  version?: number;
}

function LocationPicker({ onLocationSelect }: { onLocationSelect: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onLocationSelect(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

export default function LocationForm() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;
  const [serverError, setServerError] = useState<string | null>(null);
  const { data: appConfig } = useConfig();

  const { data: entity, isLoading } = useQuery({
    queryKey: [entityType, id],
    queryFn: async () => {
      if (!config || !id) return null;
      const { data } = await apiClient.get<Record<string, unknown>>(`/${config.apiPath}/${id}`);
      return data;
    },
    enabled: !!config && !!id,
  });

  // For collections, fetch the parent institution to support coordinate inheritance
  const institutionRef = entity?.institution as { uid: string; name?: string } | null | undefined;
  const { data: parentInstitution } = useQuery({
    queryKey: ['institution', institutionRef?.uid],
    queryFn: async () => {
      const { data } = await apiClient.get<Institution>(`/institution/${institutionRef!.uid}`);
      return data;
    },
    enabled: entityType === 'collection' && !!institutionRef?.uid,
  });

  const parentHasCoords =
    parentInstitution != null &&
    parentInstitution.latitude != null &&
    parentInstitution.longitude != null;

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { isSubmitting },
  } = useForm<LocationFormValues>();

  useEffect(() => {
    if (!entity) return;
    const address = (entity.address as Record<string, string> | null) ?? {};
    reset({
      street: address.street ?? '',
      postBox: address.postBox ?? '',
      city: address.city ?? '',
      addressState: address.state ?? '',
      postcode: address.postcode ?? '',
      country: address.country ?? '',
      latitude: entity.latitude != null ? String(entity.latitude) : '',
      longitude: entity.longitude != null ? String(entity.longitude) : '',
      altitude: (entity.altitude as string) ?? '',
      state: (entity.state as string) ?? '',
      email: (entity.email as string) ?? '',
      phone: (entity.phone as string) ?? '',
      version: entity.version as number | undefined,
    });
  }, [entity, reset]);

  const mutation = useMutation({
    mutationFn: (data: Record<string, unknown>) => updateEntity(config!.apiPath, id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id] });
      navigate(`${config!.showPath}/${id}`);
    },
    onError: (err: Error) => {
      setServerError(err.message || 'Failed to save location.');
    },
  });

  function onSubmit(values: LocationFormValues) {
    setServerError(null);
    const payload: Record<string, unknown> = {
      address: {
        street: values.street || undefined,
        postBox: values.postBox || undefined,
        city: values.city || undefined,
        state: values.addressState || undefined,
        postcode: values.postcode || undefined,
        country: values.country || undefined,
      },
      latitude: values.latitude ? parseFloat(values.latitude) : undefined,
      longitude: values.longitude ? parseFloat(values.longitude) : undefined,
      altitude: values.altitude || undefined,
      state: values.state || undefined,
      email: values.email || undefined,
      phone: values.phone || undefined,
    };
    if (values.version !== undefined) payload.version = values.version;
    mutation.mutate(payload);
  }

  function handleMapClick(lat: number, lng: number) {
    setValue('latitude', lat.toFixed(6), { shouldDirty: true });
    setValue('longitude', lng.toFixed(6), { shouldDirty: true });
  }

  function inheritFromInstitution() {
    if (!parentInstitution || parentInstitution.latitude == null || parentInstitution.longitude == null) return;
    setValue('latitude', String(parentInstitution.latitude), { shouldDirty: true });
    setValue('longitude', String(parentInstitution.longitude), { shouldDirty: true });
  }

  const latStr = watch('latitude');
  const lngStr = watch('longitude');
  const lat = latStr ? parseFloat(latStr) : null;
  const lng = lngStr ? parseFloat(lngStr) : null;
  const hasMarker = lat != null && lng != null && !isNaN(lat) && !isNaN(lng);
  const mapCenter: LatLngExpression = hasMarker ? [lat, lng] : DEFAULT_CENTER;

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

  const entityName = entity?.name as string | undefined;

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[
            { url: `/${entityType}/list`, label: ENTITY_LABELS[entityType ?? ''] ?? entityType ?? '' },
            { url: `/${entityType}/show/${id}`, label: (entity?.name as string) || id || '' },
          ]}
          current="Location"
        />
        <h1>Location &amp; Contact</h1>
        {entityName && (
          <p className="text-muted">{entityName} (<code>{id}</code>)</p>
        )}

        {serverError && <div className="alert alert-danger">{serverError}</div>}

        <form onSubmit={handleSubmit(onSubmit)}>
          <fieldset className="mb-4">
            <legend>Address</legend>
            <div className="mb-3">
              <label htmlFor="street" className="form-label">Street</label>
              <input id="street" type="text" className="form-control" {...register('street')} />
            </div>
            <div className="mb-3">
              <label htmlFor="postBox" className="form-label">PO Box</label>
              <input id="postBox" type="text" className="form-control" {...register('postBox')} />
            </div>
            <div className="row">
              <div className="col-md-6 mb-3">
                <label htmlFor="city" className="form-label">City</label>
                <input id="city" type="text" className="form-control" {...register('city')} />
              </div>
              <div className="col-md-3 mb-3">
                <label htmlFor="addressState" className="form-label">State</label>
                <input id="addressState" type="text" className="form-control" {...register('addressState')} />
              </div>
              <div className="col-md-3 mb-3">
                <label htmlFor="postcode" className="form-label">Postcode</label>
                <input id="postcode" type="text" className="form-control" {...register('postcode')} />
              </div>
            </div>
            <div className="mb-3">
              <label htmlFor="country" className="form-label">Country</label>
              <input id="country" type="text" className="form-control" {...register('country')} />
            </div>
          </fieldset>

          <fieldset className="mb-4">
            <legend>Coordinates</legend>
            <div className="row">
              <div className="col-md-4 mb-3">
                <label htmlFor="latitude" className="form-label">Latitude</label>
                <input id="latitude" type="text" className="form-control" {...register('latitude')} />
              </div>
              <div className="col-md-4 mb-3">
                <label htmlFor="longitude" className="form-label">Longitude</label>
                <input id="longitude" type="text" className="form-control" {...register('longitude')} />
              </div>
              <div className="col-md-4 mb-3">
                <label htmlFor="altitude" className="form-label">Altitude</label>
                <input id="altitude" type="text" className="form-control" {...register('altitude')} />
              </div>
            </div>

            {/* Inherit coordinates from parent institution (collections only) */}
            {entityType === 'collection' && parentHasCoords && (
              <div className="alert alert-info d-flex align-items-center gap-2 mb-3">
                <span>
                  Parent institution <strong>{parentInstitution!.name}</strong> has coordinates
                  ({parentInstitution!.latitude}, {parentInstitution!.longitude}).
                </span>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-primary ms-auto"
                  onClick={inheritFromInstitution}
                >
                  <i className="fa fa-map-marker me-1" />
                  Inherit from institution
                </button>
              </div>
            )}

            {entityType === 'collection' && institutionRef && !parentHasCoords && parentInstitution && (
              <div className="form-text text-muted mb-3">
                Parent institution <strong>{parentInstitution.name}</strong> does not have coordinates set.
              </div>
            )}

            <div className="mb-3" style={{ height: '400px' }}>
              <MapContainer
                center={mapCenter}
                zoom={hasMarker ? 10 : DEFAULT_ZOOM}
                style={{ height: '100%', width: '100%' }}
              >
                <TileLayer
                  attribution={appConfig?.cartodbPattern
                    ? '&copy; <a href="https://carto.com/">CARTO</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                    : '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}
                  url={appConfig?.cartodbPattern || 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'}
                />
                {hasMarker && <Marker position={[lat, lng]} />}
                <LocationPicker onLocationSelect={handleMapClick} />
              </MapContainer>
              <div className="form-text text-muted">Click on the map to set coordinates.</div>
            </div>
          </fieldset>

          <fieldset className="mb-4">
            <legend>Entity State &amp; Contact</legend>
            <div className="mb-3">
              <label htmlFor="state" className="form-label">State (entity level)</label>
              <input id="state" type="text" className="form-control" {...register('state')} />
            </div>
            <div className="row">
              <div className="col-md-6 mb-3">
                <label htmlFor="email" className="form-label">Email</label>
                <input id="email" type="email" className="form-control" {...register('email')} />
              </div>
              <div className="col-md-6 mb-3">
                <label htmlFor="phone" className="form-label">Phone</label>
                <input id="phone" type="text" className="form-control" {...register('phone')} />
              </div>
            </div>
          </fieldset>

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
