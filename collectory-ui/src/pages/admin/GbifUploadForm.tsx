import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { DataResource } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import { useConfig } from '../../hooks/useConfig';

export default function GbifUploadForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: appConfig } = useConfig();
  const gbifWebsite = appConfig?.gbifWebsite ?? 'https://www.gbif.org';
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<{
    success: boolean;
    message: string;
    dataResourceUid?: string;
    dataResourceName?: string;
  } | null>(null);

  const { data: resource, isLoading } = useQuery({
    queryKey: ['dataResource', id],
    queryFn: async () => {
      const { data } = await apiClient.get<DataResource>(`/dataResource/${id}`);
      return data;
    },
    enabled: !!id,
  });

  async function handleDownload() {
    if (!url.trim()) return;

    setLoading(true);
    setResult(null);

    try {
      const { data } = await apiClient.get<{ dataResourceUid?: string; dataResourceName?: string }>(
        `/dataResource/${id}/downloadGBIFFile`,
        { params: { url: url.trim() } },
      );

      setResult({
        success: true,
        message: 'GBIF archive downloaded successfully.',
        dataResourceUid: data.dataResourceUid,
        dataResourceName: data.dataResourceName,
      });
      setUrl('');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Download failed.';
      setResult({ success: false, message });
    } finally {
      setLoading(false);
    }
  }

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (!resource) {
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
            <li className="breadcrumb-item active">GBIF Upload</li>
          </ol>
        </nav>
        <h1>Upload GBIF Archive - {resource.name}</h1>

        <div className="row">
          <div className="col-md-6">
            {result && (
              <div className={`alert ${result.success ? 'alert-success' : 'alert-danger'} mb-3`}>
                {result.message}
                {result.success && result.dataResourceName && (
                  <div className="mt-2">
                    <strong>{result.dataResourceName}</strong>
                    {result.dataResourceUid && (
                      <div>
                        <a href={`/dataResource/show/${result.dataResourceUid}`}>
                          View data resource
                        </a>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            <div className="mb-3">
              <label htmlFor="gbifUrl" className="form-label">
                GBIF Archive URL
              </label>
              <input
                type="url"
                id="gbifUrl"
                className="form-control"
                placeholder="e.g. https://api.gbif.org/v1/occurrence/download/request/0001008-150512124619364.zip"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
              />
            </div>

            <div className="d-flex gap-2">
              <button
                type="button"
                className="btn btn-outline-dark"
                disabled={!url.trim() || loading}
                onClick={handleDownload}
              >
                {loading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-1" role="status" />
                    Downloading...
                  </>
                ) : (
                  'Download Archive'
                )}
              </button>
              <button
                type="button"
                className="btn btn-outline-dark"
                onClick={() => navigate(`/dataResource/show/${id}`)}
              >
                Cancel
              </button>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card card-body">
              <p>
                Download a Darwin Core Archive from the{' '}
                <a href={gbifWebsite} target="_blank" rel="noopener noreferrer">
                  GBIF portal
                </a>
                .
              </p>
              <p>
                <b>Note</b>: Enter the direct download URL for a GBIF occurrence download or
                dataset export. The file will be downloaded server-side and processed.
              </p>
              <p>
                Example URL format:
                <br />
                <strong>
                  https://api.gbif.org/v1/occurrence/download/request/0001008-150512124619364.zip
                </strong>
              </p>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
