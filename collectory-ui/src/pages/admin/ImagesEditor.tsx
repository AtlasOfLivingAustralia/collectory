import { useEffect, useState, useRef } from 'react';
import { useForm } from 'react-hook-form';
import { useParams, useNavigate, Link } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { ImageRef } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

const ENTITY_CONFIG: Record<string, { apiPath: string; singular: string; showPath: string; dataPath: string }> = {
  collection: { apiPath: 'collection', singular: 'Collection', showPath: '/public/showCollection', dataPath: '/data/collection' },
  institution: { apiPath: 'institution', singular: 'Institution', showPath: '/public/showInstitution', dataPath: '/data/institution' },
  dataResource: { apiPath: 'dataResource', singular: 'Data Resource', showPath: '/public/showDataResource', dataPath: '/data/dataResource' },
  dataProvider: { apiPath: 'dataProvider', singular: 'Data Provider', showPath: '/public/showDataProvider', dataPath: '/data/dataProvider' },
  dataHub: { apiPath: 'dataHub', singular: 'Data Hub', showPath: '/public/showDataHub', dataPath: '/data/dataHub' },
};

interface ImageFormValues {
  // Logo
  logoCaption: string;
  logoAttribution: string;
  logoCopyright: string;
  // Image
  imageCaption: string;
  imageAttribution: string;
  imageCopyright: string;
  // Optimistic locking
  version?: number;
}

export default function ImagesEditor() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;
  const [serverError, setServerError] = useState<string | null>(null);

  // File state
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [logoPreview, setLogoPreview] = useState<string | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [logoRemoved, setLogoRemoved] = useState(false);
  const [imageRemoved, setImageRemoved] = useState(false);
  const logoInputRef = useRef<HTMLInputElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);

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
  } = useForm<ImageFormValues>();

  useEffect(() => {
    if (!entity) return;
    const logoRef = (entity.logoRef as ImageRef | null) ?? {};
    const imageRef = (entity.imageRef as ImageRef | null) ?? {};
    reset({
      logoCaption: logoRef.caption ?? '',
      logoAttribution: logoRef.attribution ?? '',
      logoCopyright: logoRef.copyright ?? '',
      imageCaption: imageRef.caption ?? '',
      imageAttribution: imageRef.attribution ?? '',
      imageCopyright: imageRef.copyright ?? '',
      version: entity.version as number | undefined,
    });
  }, [entity, reset]);

  // Clean up object URLs on unmount
  useEffect(() => {
    return () => {
      if (logoPreview) URL.revokeObjectURL(logoPreview);
      if (imagePreview) URL.revokeObjectURL(imagePreview);
    };
  }, [logoPreview, imagePreview]);

  function handleLogoFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setLogoFile(file);
    setLogoRemoved(false);
    if (logoPreview) URL.revokeObjectURL(logoPreview);
    setLogoPreview(file ? URL.createObjectURL(file) : null);
  }

  function handleImageFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setImageFile(file);
    setImageRemoved(false);
    if (imagePreview) URL.revokeObjectURL(imagePreview);
    setImagePreview(file ? URL.createObjectURL(file) : null);
  }

  function handleRemoveLogo() {
    setLogoFile(null);
    setLogoRemoved(true);
    if (logoPreview) URL.revokeObjectURL(logoPreview);
    setLogoPreview(null);
    if (logoInputRef.current) logoInputRef.current.value = '';
  }

  function handleRemoveImage() {
    setImageFile(null);
    setImageRemoved(true);
    if (imagePreview) URL.revokeObjectURL(imagePreview);
    setImagePreview(null);
    if (imageInputRef.current) imageInputRef.current.value = '';
  }

  const mutation = useMutation({
    mutationFn: async (values: ImageFormValues) => {
      if (!config || !id) throw new Error('Missing config or id');

      const formData = new FormData();

      // Logo fields
      if (logoFile) {
        formData.append('logoRef.file', logoFile);
      }
      if (logoRemoved) {
        formData.append('logoRef.remove', 'true');
      }
      formData.append('logoRef.caption', values.logoCaption || '');
      formData.append('logoRef.attribution', values.logoAttribution || '');
      formData.append('logoRef.copyright', values.logoCopyright || '');

      // Image fields
      if (imageFile) {
        formData.append('imageRef.file', imageFile);
      }
      if (imageRemoved) {
        formData.append('imageRef.remove', 'true');
      }
      formData.append('imageRef.caption', values.imageCaption || '');
      formData.append('imageRef.attribution', values.imageAttribution || '');
      formData.append('imageRef.copyright', values.imageCopyright || '');

      if (values.version !== undefined) {
        formData.append('version', String(values.version));
      }

      const res = await apiClient.post(
        `/${config.apiPath}/${id}/updateImages`,
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } },
      );
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id] });
      navigate(`${config!.showPath}/${id}`);
    },
    onError: (err: Error) => {
      setServerError(err.message || 'Failed to save images.');
    },
  });

  function onSubmit(values: ImageFormValues) {
    setServerError(null);
    mutation.mutate(values);
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

  const entityName = entity?.name as string | undefined;
  const logoRef = (entity?.logoRef as ImageRef | null) ?? {};
  const imageRef = (entity?.imageRef as ImageRef | null) ?? {};

  const hasExistingLogo = !logoRemoved && !!logoRef.file;
  const hasExistingImage = !imageRemoved && !!imageRef.file;

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item">
              <Link to={`/${entityType}/list`}>{ENTITY_LABELS[entityType ?? ''] ?? entityType}</Link>
            </li>
            <li className="breadcrumb-item">
              <Link to={`/${entityType}/show/${id}`}>{id}</Link>
            </li>
            <li className="breadcrumb-item active">Images</li>
          </ol>
        </nav>
        <h1>Images</h1>
        {entityName && (
          <p className="text-muted">{entityName} (<code>{id}</code>)</p>
        )}

        {serverError && <div className="alert alert-danger">{serverError}</div>}

        <form onSubmit={handleSubmit(onSubmit)}>
          {/* Logo section */}
          <fieldset className="mb-4">
            <legend>Logo</legend>

            {/* Current or preview image */}
            {logoPreview ? (
              <div className="mb-3">
                <img
                  src={logoPreview}
                  alt="Logo preview"
                  className="img-thumbnail"
                  style={{ maxHeight: '150px' }}
                />
                <p className="form-text text-muted mt-1">New file selected (not yet saved)</p>
                <button type="button" className="btn btn-sm btn-outline-danger mt-1" onClick={handleRemoveLogo}>
                  <i className="fa fa-times me-1" />Remove
                </button>
              </div>
            ) : hasExistingLogo ? (
              <div className="mb-3">
                <img
                  src={`${config.dataPath}/${logoRef.file}`}
                  alt="Current logo"
                  className="img-thumbnail"
                  style={{ maxHeight: '150px' }}
                />
                <p className="form-text text-muted mt-1">Current file: {logoRef.file}</p>
                <button type="button" className="btn btn-sm btn-outline-danger mt-1" onClick={handleRemoveLogo}>
                  <i className="fa fa-times me-1" />Remove
                </button>
              </div>
            ) : (
              <p className="text-muted mb-3">
                {logoRemoved ? 'Logo will be removed on save.' : 'No logo uploaded.'}
              </p>
            )}

            <div className="mb-3">
              <label htmlFor="logoUpload" className="form-label">Upload New Logo</label>
              <input
                id="logoUpload"
                ref={logoInputRef}
                type="file"
                className="form-control"
                accept="image/*"
                onChange={handleLogoFileChange}
              />
            </div>

            <div className="mb-3">
              <label htmlFor="logoCaption" className="form-label">Caption</label>
              <input id="logoCaption" type="text" className="form-control" {...register('logoCaption')} />
            </div>

            <div className="row">
              <div className="col-md-6 mb-3">
                <label htmlFor="logoAttribution" className="form-label">Attribution</label>
                <input id="logoAttribution" type="text" className="form-control" {...register('logoAttribution')} />
              </div>
              <div className="col-md-6 mb-3">
                <label htmlFor="logoCopyright" className="form-label">Copyright</label>
                <input id="logoCopyright" type="text" className="form-control" {...register('logoCopyright')} />
              </div>
            </div>
          </fieldset>

          {/* Image section */}
          <fieldset className="mb-4">
            <legend>Image</legend>

            {/* Current or preview image */}
            {imagePreview ? (
              <div className="mb-3">
                <img
                  src={imagePreview}
                  alt="Image preview"
                  className="img-thumbnail"
                  style={{ maxHeight: '200px' }}
                />
                <p className="form-text text-muted mt-1">New file selected (not yet saved)</p>
                <button type="button" className="btn btn-sm btn-outline-danger mt-1" onClick={handleRemoveImage}>
                  <i className="fa fa-times me-1" />Remove
                </button>
              </div>
            ) : hasExistingImage ? (
              <div className="mb-3">
                <img
                  src={`${config.dataPath}/${imageRef.file}`}
                  alt="Current image"
                  className="img-thumbnail"
                  style={{ maxHeight: '200px' }}
                />
                <p className="form-text text-muted mt-1">Current file: {imageRef.file}</p>
                <button type="button" className="btn btn-sm btn-outline-danger mt-1" onClick={handleRemoveImage}>
                  <i className="fa fa-times me-1" />Remove
                </button>
              </div>
            ) : (
              <p className="text-muted mb-3">
                {imageRemoved ? 'Image will be removed on save.' : 'No image uploaded.'}
              </p>
            )}

            <div className="mb-3">
              <label htmlFor="imageUpload" className="form-label">Upload New Image</label>
              <input
                id="imageUpload"
                ref={imageInputRef}
                type="file"
                className="form-control"
                accept="image/*"
                onChange={handleImageFileChange}
              />
            </div>

            <div className="mb-3">
              <label htmlFor="imageCaption" className="form-label">Caption</label>
              <input id="imageCaption" type="text" className="form-control" {...register('imageCaption')} />
            </div>

            <div className="row">
              <div className="col-md-6 mb-3">
                <label htmlFor="imageAttribution" className="form-label">Attribution</label>
                <input id="imageAttribution" type="text" className="form-control" {...register('imageAttribution')} />
              </div>
              <div className="col-md-6 mb-3">
                <label htmlFor="imageCopyright" className="form-label">Copyright</label>
                <input id="imageCopyright" type="text" className="form-control" {...register('imageCopyright')} />
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
