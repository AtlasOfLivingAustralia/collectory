import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface Organization {
  key: string;
  title: string;
  uid: string | null;
  statusAvailable: boolean;
  lastCreated?: boolean;
}

interface SearchResponse {
  countryMap: Record<string, string>;
  country: string | null;
  organizations: Organization[] | null;
}

interface ImportResult {
  success: boolean | number;
  uid?: string;
  message?: string;
  error?: number;
  skipped?: number;
  country?: string;
}

export default function GbifOrganizationImport() {
  const queryClient = useQueryClient();
  const [country, setCountry] = useState<string>('NO_VALUE');
  const [flash, setFlash] = useState<string | null>(null);
  const [flashType, setFlashType] = useState<'success' | 'warning'>('warning');

  // Fetch country map + organizations for selected country
  const { data, isFetching } = useQuery<SearchResponse>({
    queryKey: ['gbifOrganizations', country],
    queryFn: async () => {
      const params: Record<string, string> = {};
      if (country && country !== 'NO_VALUE') params.country = country;
      const { data } = await apiClient.get<SearchResponse>(
        '/dataProvider/gbif/searchForOrganizations',
        { params }
      );
      return data;
    },
  });

  // Import single organization
  const importOne = useMutation<ImportResult, Error, { organizationKey: string }>({
    mutationFn: async ({ organizationKey }) => {
      const params = new URLSearchParams({ organizationKey });
      if (country && country !== 'NO_VALUE') params.set('country', country);
      const { data } = await apiClient.post<ImportResult>(
        '/dataProvider/gbif/importFromOrganization',
        params
      );
      return data;
    },
    onSuccess: (result) => {
      if (result.success) {
        setFlash(`Organization imported successfully as ${result.uid}.`);
        setFlashType('success');
        queryClient.invalidateQueries({ queryKey: ['gbifOrganizations', country] });
      } else {
        setFlash(result.message || 'Import failed.');
        setFlashType('warning');
      }
    },
    onError: (err) => {
      setFlash(err.message || 'Import failed.');
      setFlashType('warning');
    },
  });

  // Import all organizations for country
  const importAll = useMutation<ImportResult, Error>({
    mutationFn: async () => {
      const params = new URLSearchParams({ country });
      const { data } = await apiClient.post<ImportResult>(
        '/dataProvider/gbif/importAllFromOrganizations',
        params
      );
      return data;
    },
    onSuccess: (result) => {
      setFlash(
        `Import complete. Imported: ${result.success}, Skipped: ${result.skipped}, Errors: ${result.error}.`
      );
      setFlashType('success');
      queryClient.invalidateQueries({ queryKey: ['gbifOrganizations', country] });
    },
    onError: (err) => {
      setFlash(err.message || 'Import all failed.');
      setFlashType('warning');
    },
  });

  const organizations = data?.organizations ?? null;
  const countryMap = data?.countryMap ?? {};
  const hasOrgs = organizations !== null && organizations.length > 0;
  const isBusy = importOne.isPending || importAll.isPending;

  function handleCountryChange(e: React.ChangeEvent<HTMLSelectElement>) {
    setFlash(null);
    setCountry(e.target.value);
  }

  function handleImportAll() {
    if (!window.confirm('Do you want to import all organizations?')) return;
    importAll.mutate();
  }

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><Link to="/manage/list">Manage</Link></li>
            <li className="breadcrumb-item active">Import GBIF Organizations</li>
          </ol>
        </nav>
        {/* Toolbar */}
        <div className="btn-toolbar mb-3">
          <div className="btn-group">
            <Link to="/" className="btn btn-outline-dark">
              <i className="fa fa-home me-1" />
              Home
            </Link>
            <Link to="/dataProvider/list" className="btn btn-outline-dark">
              <i className="fa fa-list me-1" />
              List Data Providers
            </Link>
          </div>
        </div>

        <h1>Import data providers from GBIF</h1>

        {flash && (
          <div className={`alert alert-${flashType} alert-dismissible`} role="alert">
            {flash}
            <button
              type="button"
              className="btn-close"
              onClick={() => setFlash(null)}
              aria-label="Close"
            />
          </div>
        )}

        <div className="list">
          <div className="row g-3">
            {/* Description card */}
            <div className="col-md-6">
              <div className="card card-body">
                <p>Use this tool to create Data Providers from Organizations defined on GBIF.</p>
                <p>
                  Choose a country to display its organizations.
                  <br />
                  Then, you can:
                  <br />
                  &mdash;&nbsp;import all organizations as data providers
                  <br />
                  &mdash;&nbsp;create data provider one by one
                </p>
              </div>
            </div>

            {/* Country selector */}
            <div className="col-md-6">
              <div className="mb-3">
                <label htmlFor="country" className="form-label">
                  Country
                </label>
                <select
                  id="country"
                  className="form-select"
                  value={country}
                  onChange={handleCountryChange}
                  disabled={isBusy}
                >
                  <option value="NO_VALUE">Please select a country.</option>
                  {Object.entries(countryMap)
                    .sort(([, a], [, b]) => a.localeCompare(b))
                    .map(([code, name]) => (
                      <option key={code} value={code}>
                        {name}
                      </option>
                    ))}
                </select>
              </div>
            </div>

            {/* Import All button */}
            {hasOrgs && (
              <div className="col-md-6 d-flex justify-content-end">
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={isBusy}
                  onClick={handleImportAll}
                >
                  {importAll.isPending ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-1" role="status" />
                      Importing...
                    </>
                  ) : (
                    'Import all as data providers'
                  )}
                </button>
              </div>
            )}
          </div>

          {/* Loading indicator */}
          {isFetching && !data && (
            <div className="d-flex justify-content-center p-4">
              <div className="spinner-border" role="status">
                <span className="visually-hidden">Loading...</span>
              </div>
            </div>
          )}

          {/* No results message */}
          {!isFetching && country !== 'NO_VALUE' && organizations !== null && organizations.length === 0 && (
            <div className="alert alert-info mt-3">No organizations found for this country.</div>
          )}

          {/* Organizations table */}
          {hasOrgs && (
            <table className="table table-bordered table-striped mt-3">
              <thead>
                <tr>
                  <th>GBIF UID</th>
                  <th>Title</th>
                  <th>UID</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {organizations!.map((org) => (
                  <tr
                    key={org.key}
                    className={org.lastCreated ? 'table-warning' : ''}
                  >
                    <td>
                      <a
                        href={`https://www.gbif.org/publisher/${org.key}`}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {org.key}
                      </a>
                    </td>
                    <td>{org.title}</td>
                    <td>
                      {org.uid ? (
                        <Link to={`/dataProvider/show/${org.uid}`}>{org.uid}</Link>
                      ) : (
                        ''
                      )}
                    </td>
                    <td>
                      {org.statusAvailable && (
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-primary"
                          disabled={isBusy}
                          onClick={() => importOne.mutate({ organizationKey: org.key })}
                        >
                          {importOne.isPending && importOne.variables?.organizationKey === org.key ? (
                            <>
                              <span className="spinner-border spinner-border-sm me-1" role="status" />
                              Importing...
                            </>
                          ) : (
                            'Import'
                          )}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </ProtectedRoute>
  );
}
