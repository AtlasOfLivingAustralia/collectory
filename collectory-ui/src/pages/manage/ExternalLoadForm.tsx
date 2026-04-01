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

export default function ExternalLoadForm() {
  const navigate = useNavigate();
  const { data: appConfig } = useConfig();
  const gbifWebsite = appConfig?.gbifWebsite ?? 'https://www.gbif.org';
  const [country, setCountry] = useState('');
  const [name, setName] = useState('');
  const [dataProviderUid, setDataProviderUid] = useState('');
  const [maxDatasets, setMaxDatasets] = useState(10);

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
    if (name.trim()) params.set('name', name.trim());
    if (dataProviderUid) params.set('dataProviderUid', dataProviderUid);
    params.set('maxDatasets', String(maxDatasets));
    navigate(`/manage/externalLoad/review?${params.toString()}`);
  }

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><Link to="/manage/list">Manage</Link></li>
            <li className="breadcrumb-item active">External Data Load</li>
          </ol>
        </nav>
        <h1>Load External Datasets</h1>

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
                <label htmlFor="name" className="form-label">Name (search term)</label>
                <input
                  type="text"
                  id="name"
                  className="form-control"
                  placeholder="Search by dataset name..."
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>

              <div className="mb-3">
                <label htmlFor="dataProvider" className="form-label">Data Provider</label>
                <select
                  id="dataProvider"
                  className="form-select"
                  value={dataProviderUid}
                  onChange={(e) => setDataProviderUid(e.target.value)}
                >
                  <option value="">-- Select data provider --</option>
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

              <button
                type="button"
                className="btn btn-primary"
                onClick={handleReview}
              >
                <i className="fa fa-search me-1" />
                Review
              </button>
            </div>
          </div>

          <div className="col-md-5">
            <div className="card card-body">
              <h5>About External Dataset Loading</h5>
              <p>
                Search for datasets published to{' '}
                <a href={gbifWebsite} target="_blank" rel="noopener noreferrer">GBIF</a>{' '}
                and load them into this collectory.
              </p>
              <p>
                Use the <strong>Country</strong> filter to find datasets published by institutions
                in a specific country. Use the <strong>Name</strong> field to search by dataset title.
              </p>
              <p>
                Select a <strong>Data Provider</strong> to associate the loaded datasets with an
                existing provider, or leave blank to create new providers as needed.
              </p>
              <p>
                After clicking <strong>Review</strong>, you will be able to select which datasets
                to load and configure update options before proceeding.
              </p>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
