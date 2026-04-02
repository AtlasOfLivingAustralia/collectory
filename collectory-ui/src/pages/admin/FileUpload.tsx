import { useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { DataResource } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

export default function FileUpload() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);

  const { data: resource, isLoading } = useQuery({
    queryKey: ['dataResource', id],
    queryFn: async () => {
      const { data } = await apiClient.get<DataResource>(`/dataResource/${id}`);
      return data;
    },
    enabled: !!id,
  });

  async function handleUpload() {
    if (!selectedFile || !id) return;

    setUploading(true);
    setProgress(0);
    setResult(null);

    try {
      const formData = new FormData();
      formData.append('myFile', selectedFile);

      await apiClient.post(`/dataResource/${id}/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (e) => {
          setProgress(Math.round((e.loaded * 100) / (e.total ?? 1)));
        },
      });

      setResult({ success: true, message: 'File uploaded successfully.' });
      setSelectedFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Upload failed.';
      setResult({ success: false, message });
    } finally {
      setUploading(false);
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
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[
            { url: '/dataResource/list', label: 'Data Resources' },
            { url: `/dataResource/show/${id}`, label: resource.name || id || '' },
          ]}
          current="Upload"
        />
        <h1>Upload Data File - {resource.name}</h1>
        <p className="text-muted">
          Upload a Darwin Core Archive (DwC-A) file for this data resource. The file should be a
          ZIP archive containing the data and metadata files.
        </p>

        {result && (
          <div className={`alert ${result.success ? 'alert-success' : 'alert-danger'}`}>
            {result.message}
          </div>
        )}

        <div className="card card-body mb-3">
          <div className="mb-3">
            <label htmlFor="fileInput" className="form-label">Select file</label>
            <input
              ref={fileInputRef}
              type="file"
              id="fileInput"
              className="form-control"
              accept=".zip,.gz,.tar.gz"
              onChange={(e) => {
                setSelectedFile(e.target.files?.[0] ?? null);
                setResult(null);
              }}
            />
          </div>

          {uploading && (
            <div className="mb-3">
              <div className="progress">
                <div
                  className="progress-bar bg-success"
                  role="progressbar"
                  style={{ width: `${progress}%` }}
                  aria-valuenow={progress}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  {progress}%
                </div>
              </div>
            </div>
          )}

          <div className="d-flex gap-2">
            <button
              type="button"
              className="btn btn-primary"
              disabled={!selectedFile || uploading}
              onClick={handleUpload}
            >
              {uploading ? 'Uploading...' : 'Upload'}
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
      </div>
    </ProtectedRoute>
  );
}
