import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';

const ENTITY_LABELS: Record<string, string> = {
  collection: 'Collections', institution: 'Institutions', dataResource: 'Data Resources',
  dataProvider: 'Data Providers', dataHub: 'Data Hubs',
};
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { contactsApi } from '../../api/endpoints/contacts';
import type { Contact, ContactFor } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

const ENTITY_CONFIG: Record<string, { apiPath: string; singular: string; showPath: string }> = {
  collection: { apiPath: 'collection', singular: 'Collection', showPath: '/public/showCollection' },
  institution: { apiPath: 'institution', singular: 'Institution', showPath: '/public/showInstitution' },
  dataResource: { apiPath: 'dataResource', singular: 'Data Resource', showPath: '/public/showDataResource' },
  dataProvider: { apiPath: 'dataProvider', singular: 'Data Provider', showPath: '/public/showDataProvider' },
  dataHub: { apiPath: 'dataHub', singular: 'Data Hub', showPath: '/public/showDataHub' },
};

export default function ContactRoleEditor() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>();
  const queryClient = useQueryClient();
  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;

  const [showAddModal, setShowAddModal] = useState(false);
  const [contactSearch, setContactSearch] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);

  // Fetch entity contacts
  const { data: entityContacts = [], isLoading: contactsLoading } = useQuery({
    queryKey: [entityType, id, 'contacts'],
    queryFn: () => contactsApi.forEntity(config!.apiPath, id!),
    enabled: !!config && !!id,
  });

  // Fetch all contacts (for add modal)
  const { data: allContacts = [], isLoading: allContactsLoading } = useQuery({
    queryKey: ['contacts'],
    queryFn: contactsApi.list,
    enabled: showAddModal,
  });

  // Add contact to entity
  const addMutation = useMutation({
    mutationFn: (data: Partial<ContactFor>) =>
      contactsApi.addToEntity(config!.apiPath, id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id, 'contacts'] });
      setShowAddModal(false);
      setContactSearch('');
    },
    onError: (err: Error) => setServerError(err.message),
  });

  // Remove contact from entity
  const removeMutation = useMutation({
    mutationFn: (contactId: number) =>
      contactsApi.removeFromEntity(config!.apiPath, id!, contactId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id, 'contacts'] });
    },
    onError: (err: Error) => setServerError(err.message),
  });

  // Update contact role/flags
  const updateMutation = useMutation({
    mutationFn: ({ contactId, data }: { contactId: number; data: Partial<ContactFor> }) =>
      contactsApi.updateForEntity(config!.apiPath, id!, contactId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entityType, id, 'contacts'] });
    },
    onError: (err: Error) => setServerError(err.message),
  });

  function handleAddContact(contact: Contact) {
    addMutation.mutate({
      contact,
      entityUid: id,
      role: '',
      primaryContact: false,
      administrator: false,
      notify: false,
    });
  }

  function handleToggleFlag(cf: ContactFor, field: 'primaryContact' | 'administrator' | 'notify') {
    updateMutation.mutate({
      contactId: cf.contact.id,
      data: { [field]: !cf[field] },
    });
  }

  function handleRoleChange(cf: ContactFor, role: string) {
    updateMutation.mutate({
      contactId: cf.contact.id,
      data: { role },
    });
  }

  // Filter contacts for the add modal (exclude already-added ones)
  const existingContactIds = new Set(entityContacts.map((ec) => ec.contact.id));
  const filteredContacts = allContacts.filter((c) => {
    if (existingContactIds.has(c.id)) return false;
    if (!contactSearch.trim()) return true;
    const q = contactSearch.toLowerCase();
    const name = `${c.firstName ?? ''} ${c.lastName ?? ''}`.toLowerCase();
    return name.includes(q) || (c.email ?? '').toLowerCase().includes(q);
  });

  if (!config) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Unknown entity type: {entityType}</div>
      </div>
    );
  }

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
            <li className="breadcrumb-item active">Contacts</li>
          </ol>
        </nav>
        <h1>Contacts</h1>
        <p className="text-muted"><code>{id}</code></p>

        {serverError && <div className="alert alert-danger">{serverError}</div>}

        <div className="d-flex gap-2 mb-3">
          <button className="btn btn-primary" onClick={() => setShowAddModal(true)}>
            <i className="fa fa-plus me-1" /> Add Contact
          </button>
          <Link to="/contact/create" className="btn btn-outline-dark">
            <i className="fa fa-user-plus me-1" /> Create New Contact
          </Link>
        </div>

        {contactsLoading ? (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
          </div>
        ) : entityContacts.length === 0 ? (
          <div className="alert alert-info">No contacts associated with this entity.</div>
        ) : (
          <table className="table table-striped">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th className="text-center">Primary</th>
                <th className="text-center">Admin</th>
                <th className="text-center">Notify</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {entityContacts.map((cf) => (
                <tr key={cf.id}>
                  <td>
                    <Link to={`/contact/edit/${cf.contact.id}`}>
                      {cf.contact.firstName} {cf.contact.lastName}
                    </Link>
                  </td>
                  <td>{cf.contact.email ?? '-'}</td>
                  <td>
                    <input
                      type="text"
                      className="form-control form-control-sm"
                      value={cf.role ?? ''}
                      onChange={(e) => handleRoleChange(cf, e.target.value)}
                      style={{ width: '150px' }}
                    />
                  </td>
                  <td className="text-center">
                    <input
                      type="checkbox"
                      className="form-check-input"
                      checked={cf.primaryContact}
                      onChange={() => handleToggleFlag(cf, 'primaryContact')}
                    />
                  </td>
                  <td className="text-center">
                    <input
                      type="checkbox"
                      className="form-check-input"
                      checked={cf.administrator}
                      onChange={() => handleToggleFlag(cf, 'administrator')}
                    />
                  </td>
                  <td className="text-center">
                    <input
                      type="checkbox"
                      className="form-check-input"
                      checked={cf.notify}
                      onChange={() => handleToggleFlag(cf, 'notify')}
                    />
                  </td>
                  <td>
                    <button
                      className="btn btn-sm btn-outline-danger"
                      onClick={() => removeMutation.mutate(cf.contact.id)}
                      disabled={removeMutation.isPending}
                    >
                      <i className="fa fa-times" /> Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        <div className="mt-3">
          <Link to={`${config.showPath}/${id}`} className="btn btn-outline-dark">
            Back to Entity
          </Link>
        </div>

        {/* Add Contact Modal */}
        {showAddModal && (
          <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
            <div className="modal-dialog modal-lg">
              <div className="modal-content">
                <div className="modal-header">
                  <h5 className="modal-title">Add Contact</h5>
                  <button type="button" className="btn-close" onClick={() => setShowAddModal(false)} />
                </div>
                <div className="modal-body">
                  <input
                    type="text"
                    className="form-control mb-3"
                    placeholder="Search contacts by name or email..."
                    value={contactSearch}
                    onChange={(e) => setContactSearch(e.target.value)}
                  />

                  {allContactsLoading ? (
                    <div className="d-flex justify-content-center p-3">
                      <div className="spinner-border spinner-border-sm" role="status">
                        <span className="visually-hidden">Loading...</span>
                      </div>
                    </div>
                  ) : filteredContacts.length === 0 ? (
                    <p className="text-muted">No contacts found.</p>
                  ) : (
                    <div className="list-group" style={{ maxHeight: '300px', overflowY: 'auto' }}>
                      {filteredContacts.slice(0, 50).map((contact) => (
                        <button
                          key={contact.id}
                          type="button"
                          className="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
                          onClick={() => handleAddContact(contact)}
                          disabled={addMutation.isPending}
                        >
                          <div>
                            <strong>{contact.firstName} {contact.lastName}</strong>
                            {contact.email && (
                              <span className="text-muted ms-2">{contact.email}</span>
                            )}
                          </div>
                          <i className="fa fa-plus text-primary" />
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <div className="modal-footer">
                  <button type="button" className="btn btn-outline-dark" onClick={() => setShowAddModal(false)}>
                    Close
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
