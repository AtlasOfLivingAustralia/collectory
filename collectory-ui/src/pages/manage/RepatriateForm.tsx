import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import { useConfig } from '../../hooks/useConfig';

interface DataProvider {
  uid: string;
  name: string;
}

export default function RepatriateForm() {
  const navigate = useNavigate();
  const { data: appConfig } = useConfig();
  const gbifWebsite = appConfig?.gbifWebsite ?? 'https://www.gbif.org';
  const [country, setCountry] = useState('');
  const [dataProviderUid, setDataProviderUid] = useState('');
  const [maxDatasets, setMaxDatasets] = useState(25);
  const [minRecordCount, setMinRecordCount] = useState(10000);
  const [maxRecordCount, setMaxRecordCount] = useState(1000000);

  const { data: countries = [], isLoading: countriesLoading } = useQuery({
    queryKey: ['manage', 'countries'],
    queryFn: async () => {
      const { data } = await apiClient.get<Record<string, string>>('/manage/countries');
      return Object.entries(data)
        .map(([code, name]) => ({ code, name }))
        .sort((a, b) => a.name.localeCompare(b.name));
    },
  });

  const { data: providers = [] } = useQuery({
    queryKey: ['dataProvider', 'list'],
    queryFn: async () => {
      const { data } = await apiClient.get<DataProvider[]>('/dataProvider');
      return data;
    },
  });

  function handleReview() {
    const params = new URLSearchParams();
    if (country) params.set('country', country);
    if (dataProviderUid) params.set('dataProviderUid', dataProviderUid);
    params.set('maxDatasets', String(maxDatasets));
    params.set('minRecordCount', String(minRecordCount));
    params.set('maxRecordCount', String(maxRecordCount));
    navigate(`/manage/repatriate/review?${params.toString()}`);
  }

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><Link to="/manage/list">Manage</Link></li>
            <li className="breadcrumb-item active">Repatriate</li>
          </ol>
        </nav>
        <h1>Repatriate GBIF Datasets</h1>

        <div className="row">
          <div className="col-md-7">
            <div className="card card-body mb-3">
              <div className="mb-3">
                <label htmlFor="country" className="form-label">Country</label>
                <select
                  id="country"
                  className="form-select"
                  value={country}
                  disabled={countriesLoading}
                  onChange={(e) => setCountry(e.target.value)}
                >
                  <option value="">{countriesLoading ? 'Loading countries...' : '-- Select country --'}</option>
                  {countries.map((c) => (
                    <option key={c.code} value={c.code}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div className="mb-3">
                <label htmlFor="dataProvider" className="form-label">Data Provider (optional)</label>
                <select
                  id="dataProvider"
                  className="form-select"
                  value={dataProviderUid}
                  onChange={(e) => setDataProviderUid(e.target.value)}
                >
                  <option value="">-- Any --</option>
                  {providers.map((p) => (
                    <option key={p.uid} value={p.uid}>{p.name}</option>
                  ))}
                </select>
              </div>

              <div className="mb-3">
                <label htmlFor="maxDatasets" className="form-label">Max datasets</label>
                <input
                  type="number"
                  id="maxDatasets"
                  className="form-control"
                  min={1}
                  max={1000}
                  value={maxDatasets}
                  onChange={(e) => setMaxDatasets(Number(e.target.value))}
                />
              </div>

              <div className="row">
                <div className="col-md-6 mb-3">
                  <label htmlFor="minRecordCount" className="form-label">Min record count</label>
                  <input
                    type="number"
                    id="minRecordCount"
                    className="form-control"
                    min={0}
                    value={minRecordCount}
                    onChange={(e) => setMinRecordCount(Number(e.target.value))}
                  />
                </div>
                <div className="col-md-6 mb-3">
                  <label htmlFor="maxRecordCount" className="form-label">Max record count</label>
                  <input
                    type="number"
                    id="maxRecordCount"
                    className="form-control"
                    min={0}
                    value={maxRecordCount}
                    onChange={(e) => setMaxRecordCount(Number(e.target.value))}
                  />
                </div>
              </div>

              <button
                type="button"
                className="btn btn-primary"
                disabled={!country}
                onClick={handleReview}
              >
                <i className="fa fa-search me-1" />
                Review
              </button>
            </div>
          </div>

          <div className="col-md-5">
            <div className="card card-body">
              <h5>About Repatriation</h5>
              <p>
                Repatriation finds datasets from{' '}
                <a href={gbifWebsite} target="_blank" rel="noopener noreferrer">GBIF</a>{' '}
                that contain records relevant to the selected country and loads them into this collectory.
              </p>
              <p>
                Use the <strong>record count</strong> filters to control the size range of datasets
                to include. Datasets outside this range will be excluded from the search results.
              </p>
              <p>
                A <strong>country</strong> must be selected to search for repatriation candidates.
              </p>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
