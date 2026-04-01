import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ProtectedRoute } from '../auth/ProtectedRoute';
import { useAuth } from '../auth/useAuth';
import apiClient from '../api/client';
import { entityTypeFromUid } from '../api/types';

interface ManagedEntity {
  uid: string;
  name: string;
  entityType?: string;
  lastUpdated?: string;
}

function entityLabel(uid: string): string {
  const type = entityTypeFromUid(uid);
  switch (type) {
    case 'collection': return 'Collection';
    case 'institution': return 'Institution';
    case 'dataProvider': return 'Data Provider';
    case 'dataResource': return 'Data Resource';
    case 'dataHub': return 'Data Hub';
    case 'tempDataResource': return 'Temp Data Resource';
    default: return 'Unknown';
  }
}

function entityShowPath(uid: string): string {
  const type = entityTypeFromUid(uid);
  switch (type) {
    case 'collection': return `/collection/${uid}`;
    case 'institution': return `/institution/${uid}`;
    case 'dataProvider': return `/dataProvider/${uid}`;
    case 'dataResource': return `/dataResource/${uid}`;
    case 'dataHub': return `/dataHub/${uid}`;
    case 'tempDataResource': return `/tempDataResource/${uid}`;
    default: return '#';
  }
}

function ManageListContent() {
  const { isAdmin, isEditor, isAuthenticated, email, userId } = useAuth();
  const [entities, setEntities] = useState<ManagedEntity[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState('tools');
  const [showRequirements, setShowRequirements] = useState(false);
  const [sitemapBuilding, setSitemapBuilding] = useState(false);
  const [sitemapMessage, setSitemapMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<ManagedEntity[]>('/manage/myEntities')
      .then((res) => {
        if (!cancelled) setEntities(res.data);
      })
      .catch(() => {
        if (!cancelled) setError('Could not load your entities.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  const tabs = [
    { id: 'tools', label: 'Admin Tools', icon: 'fa fa-wrench' },
    { id: 'metadata', label: 'Your Metadata', icon: 'fa fa-database' },
    ...(isEditor ? [{ id: 'add', label: 'Add New', icon: 'fa fa-plus' }] : []),
    ...(isAdmin ? [{ id: 'gbif', label: 'GBIF / Data Management', icon: 'fa fa-globe' }] : []),
  ];

  return (
    <div className="container mt-4">
      <h1>Manage Collections</h1>

      <ul className="nav nav-pills mb-4">
        {tabs.map((tab) => (
          <li className="nav-item" key={tab.id}>
            <button
              className={`nav-link ${activeTab === tab.id ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.id)}
            >
              <i className={`${tab.icon} me-1`} /> {tab.label}
            </button>
          </li>
        ))}
      </ul>

      {/* Admin Tools */}
      {activeTab === 'tools' && (
        <div className="row g-3">
          <div className="col-md-6">
            <div className="card h-100">
              <div className="card-header">
                <i className="fa fa-list me-1" /> Entity Lists
              </div>
              <div className="list-group list-group-flush">
                <Link to="/collection/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-archive me-2" /> Collections
                </Link>
                <Link to="/institution/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-university me-2" /> Institutions
                </Link>
                <Link to="/dataProvider/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-cloud me-2" /> Data Providers
                </Link>
                <Link to="/dataResource/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-file me-2" /> Data Resources
                </Link>
                <Link to="/dataHub/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-sitemap me-2" /> Data Hubs
                </Link>
              </div>
            </div>
          </div>
          <div className="col-md-6">
            <div className="card h-100">
              <div className="card-header">
                <i className="fa fa-cogs me-1" /> Other
              </div>
              <div className="list-group list-group-flush">
                <Link to="/contact/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-address-book me-2" /> Contacts
                </Link>
                <Link to="/licence/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-legal me-2" /> Licences
                </Link>
                <Link to="/reports" className="list-group-item list-group-item-action">
                  <i className="fa fa-bar-chart me-2" /> Reports
                </Link>
                <Link to="/providerCode/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-barcode me-2" /> Provider Codes
                </Link>
                <Link to="/providerMap/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-map me-2" /> Provider Maps
                </Link>
                <Link to="/auditLogEvent/list" className="list-group-item list-group-item-action">
                  <i className="fa fa-history me-2" /> Audit Log
                </Link>
                {isAdmin && (
                  <button
                    className="list-group-item list-group-item-action text-start"
                    disabled={sitemapBuilding}
                    onClick={() => {
                      setSitemapBuilding(true);
                      setSitemapMessage(null);
                      apiClient
                        .post('/admin/buildSitemap')
                        .then(() => setSitemapMessage('Sitemap built successfully.'))
                        .catch(() => setSitemapMessage('Failed to build sitemap.'))
                        .finally(() => setSitemapBuilding(false));
                    }}
                  >
                    <i className="fa fa-sitemap me-2" />
                    {sitemapBuilding ? 'Building sitemap…' : 'Build sitemap'}
                    {sitemapMessage && (
                      <span className="ms-2 small text-muted">{sitemapMessage}</span>
                    )}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Your Metadata */}
      {activeTab === 'metadata' && (
        <div>
          {/* Auth info card */}
          <div className="card mb-3">
            <div className="card-header">
              <i className="fa fa-user me-1" /> Authentication Status
            </div>
            <div className="card-body">
              <ul className="list-unstyled mb-0">
                <li>
                  <i className={`fa ${isAuthenticated ? 'fa-check-circle text-success' : 'fa-times-circle text-danger'} me-2`} />
                  Authentication: {isAuthenticated ? 'active' : 'inactive'}
                </li>
                <li>
                  <i className="fa fa-envelope me-2" />
                  Logged in as: <strong>{email ?? userId ?? 'unknown'}</strong>
                </li>
                <li>
                  <i className={`fa ${isAdmin ? 'fa-check text-success' : 'fa-times text-muted'} me-2`} />
                  {isAdmin ? 'User has ROLE_ADMIN' : 'User does not have ROLE_ADMIN'}
                </li>
                <li>
                  <i className={`fa ${isEditor ? 'fa-check text-success' : 'fa-times text-muted'} me-2`} />
                  {isEditor ? 'User has ROLE_EDITOR' : 'User does not have ROLE_EDITOR'}
                </li>
              </ul>
            </div>
          </div>

          {/* Collapsible requirements section */}
          <div className="card mb-3">
            <div className="card-header">
              <button
                className="btn btn-link text-decoration-none p-0"
                onClick={() => setShowRequirements(!showRequirements)}
              >
                <i className={`fa ${showRequirements ? 'fa-chevron-down' : 'fa-chevron-right'} me-2`} />
                Requirements for editing
              </button>
            </div>
            {showRequirements && (
              <div className="card-body">
                <p>To be able to edit metadata you need to:</p>
                <ol>
                  <li>Have an ALA account</li>
                  <li>Have the editor role</li>
                  <li>Be listed as a contact for the entity you want to edit</li>
                </ol>
              </div>
            )}
          </div>

          {/* Entities card */}
          <div className="card">
            <div className="card-header">
              <i className="fa fa-database me-1" /> Entities you manage
            </div>
            <div className="card-body">
              {loading && (
                <div className="d-flex justify-content-center p-3">
                  <div className="spinner-border spinner-border-sm" role="status">
                    <span className="visually-hidden">Loading...</span>
                  </div>
                </div>
              )}
              {error && <div className="alert alert-warning">{error}</div>}
              {!loading && !error && entities.length === 0 && !isAdmin && !isEditor && (
                <div className="alert alert-info mb-0">
                  You are not listed as a contact for any collections or institutions.
                  To be registered as a contact you must have an ALA account.
                </div>
              )}
              {!loading && !error && entities.length === 0 && isEditor && !isAdmin && (
                <div className="alert alert-info mb-0">
                  You have the editor role but are not listed as a contact for any entities.
                </div>
              )}
              {!loading && !error && entities.length === 0 && isAdmin && (
                <p className="text-muted mb-0">
                  No entities are currently associated with your account.
                </p>
              )}
              {!loading && !error && entities.length > 0 && (
                <div className="table-responsive">
                  <table className="table table-striped table-hover mb-0">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>UID</th>
                        <th>Type</th>
                        <th>Last Updated</th>
                      </tr>
                    </thead>
                    <tbody>
                      {entities.map((entity) => (
                        <tr key={entity.uid}>
                          <td>
                            <Link to={entityShowPath(entity.uid)}>{entity.name}</Link>
                          </td>
                          <td><code>{entity.uid}</code></td>
                          <td>{entity.entityType ?? entityLabel(entity.uid)}</td>
                          <td>
                            {entity.lastUpdated
                              ? new Date(entity.lastUpdated).toLocaleDateString()
                              : '-'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Add New */}
      {activeTab === 'add' && isEditor && (
        <div className="row g-3">
          <div className="col-md-4">
            <div className="card text-center">
              <div className="card-body">
                <i className="fa fa-archive fa-2x mb-3 text-muted" />
                <h5 className="card-title">New Collection</h5>
                <p className="card-text text-muted">Create a new collection record.</p>
                <Link to="/collection/create" className="btn btn-primary">
                  <i className="fa fa-plus me-1" /> Create Collection
                </Link>
              </div>
            </div>
          </div>
          <div className="col-md-4">
            <div className="card text-center">
              <div className="card-body">
                <i className="fa fa-file fa-2x mb-3 text-muted" />
                <h5 className="card-title">New Data Resource</h5>
                <p className="card-text text-muted">Add a new data resource.</p>
                <Link to="/dataResource/create" className="btn btn-primary">
                  <i className="fa fa-plus me-1" /> Create Data Resource
                </Link>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* GBIF / Data Management (admin only) */}
      {activeTab === 'gbif' && isAdmin && (
        <div className="card">
          <div className="card-header">
            <i className="fa fa-globe me-1" /> GBIF &amp; Data Management
          </div>
          <div className="list-group list-group-flush">
            <Link to="/manage/repatriate" className="list-group-item list-group-item-action">
              <i className="fa fa-exchange me-2" /> Repatriate datasets
              <span className="text-muted ms-2">- Import datasets from GBIF for repatriation</span>
            </Link>
            <Link to="/manage/externalLoad" className="list-group-item list-group-item-action">
              <i className="fa fa-download me-2" /> Load external resources
              <span className="text-muted ms-2">- Load data resources from external sources</span>
            </Link>
            <Link to="/manage/gbifHealthCheck" className="list-group-item list-group-item-action">
              <i className="fa fa-heartbeat me-2" /> GBIF health check
              <span className="text-muted ms-2">- Check registration status with GBIF</span>
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

export default function ManageList() {
  return (
    <ProtectedRoute>
      <ManageListContent />
    </ProtectedRoute>
  );
}
