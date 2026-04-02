import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getContact } from '../../api/endpoints/contacts';
import apiClient from '../../api/client';
import { contactsApi } from '../../api/endpoints/contacts';
import type { ContactFor } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

/** Map entity UID prefix to entity type for linking */
function entityTypeFromPrefix(uid: string): string | null {
  const prefix = uid.substring(0, 2);
  switch (prefix) {
    case 'co': return 'collection';
    case 'in': return 'institution';
    case 'dp': return 'dataProvider';
    case 'dr': return 'dataResource';
    case 'dh': return 'dataHub';
    default: return null;
  }
}

export default function ContactShow() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const contactId = Number(id);

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const {
    data: contact,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['contact', contactId],
    queryFn: () => getContact(contactId),
    enabled: !isNaN(contactId),
  });

  // Fetch entity associations for this contact
  const { data: associations = [] } = useQuery({
    queryKey: ['contact', contactId, 'entities'],
    queryFn: async () => {
      const { data } = await apiClient.get<ContactFor[]>(`/contacts/${contactId}/entities`);
      return data;
    },
    enabled: !isNaN(contactId),
  });

  async function handleDelete() {
    if (isNaN(contactId)) return;
    setDeleting(true);
    try {
      await contactsApi.delete(contactId);
      navigate('/contact/list');
    } catch (err) {
      console.error('Delete failed:', err);
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
  }

  if (isLoading) {
    return (
      <div className="container mt-4 d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (error || !contact) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load contact. ID: {id}</div>
      </div>
    );
  }

  const fullName =
    [contact.title, contact.firstName, contact.lastName].filter(Boolean).join(' ') ||
    contact.email ||
    `Contact #${contact.id}`;

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[{ url: '/contact/list', label: 'Contacts' }]}
          current={fullName}
        />

        <div className="d-flex justify-content-between align-items-start mb-3">
          <h1>{fullName}</h1>
          <Link to={`/contact/edit/${id}`} className="btn btn-primary">
            <i className="fa fa-edit me-1" /> Edit
          </Link>
        </div>

        <div className="row">
          <div className="col-md-8">
            {/* Contact details */}
            <div className="card mb-3">
              <div className="card-header">
                <strong>Contact Details</strong>
              </div>
              <div className="card-body">
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Title</div>
                  <div className="col-sm-8">{contact.title || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">First Name</div>
                  <div className="col-sm-8">{contact.firstName || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Last Name</div>
                  <div className="col-sm-8">{contact.lastName || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Email</div>
                  <div className="col-sm-8">
                    {contact.email ? (
                      <a href={`mailto:${contact.email}`}>{contact.email}</a>
                    ) : (
                      '-'
                    )}
                  </div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Phone</div>
                  <div className="col-sm-8">{contact.phone || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Mobile</div>
                  <div className="col-sm-8">{contact.mobile || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Fax</div>
                  <div className="col-sm-8">{contact.fax || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Organization</div>
                  <div className="col-sm-8">{contact.organizationName || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Position</div>
                  <div className="col-sm-8">{contact.positionName || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">User ID</div>
                  <div className="col-sm-8">{contact.userId || '-'}</div>
                </div>
                <div className="row mb-1">
                  <div className="col-sm-4 text-muted">Publish</div>
                  <div className="col-sm-8">
                    {contact.publish ? (
                      <span className="badge bg-success">Yes</span>
                    ) : (
                      <span className="badge bg-secondary">No</span>
                    )}
                  </div>
                </div>
                {contact.notes && (
                  <div className="row mb-1">
                    <div className="col-sm-4 text-muted">Notes</div>
                    <div className="col-sm-8">{contact.notes}</div>
                  </div>
                )}
              </div>
            </div>

            {/* Entity associations */}
            <div className="card mb-3">
              <div className="card-header">
                <strong>Associated Entities</strong>
              </div>
              <div className="card-body">
                {associations.length === 0 ? (
                  <p className="text-muted mb-0">Not associated with any entities.</p>
                ) : (
                  <table className="table table-sm mb-0">
                    <thead>
                      <tr>
                        <th>Entity</th>
                        <th>Role</th>
                        <th>Primary</th>
                        <th>Admin</th>
                      </tr>
                    </thead>
                    <tbody>
                      {associations.map((cf) => {
                        const eType = entityTypeFromPrefix(cf.entityUid);
                        return (
                          <tr key={cf.id}>
                            <td>
                              {eType ? (
                                <Link to={`/${eType}/show/${cf.entityUid}`}>
                                  {cf.entityUid}
                                </Link>
                              ) : (
                                cf.entityUid
                              )}
                            </td>
                            <td>{cf.role || '-'}</td>
                            <td className="text-center">
                              {cf.primaryContact && <i className="fa fa-check text-success" />}
                            </td>
                            <td className="text-center">
                              {cf.administrator && <i className="fa fa-check text-success" />}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          </div>

          <div className="col-md-4">
            {/* Delete */}
            <div className="card mb-3 border-danger">
              <div className="card-header bg-danger text-white">
                <strong>Danger Zone</strong>
              </div>
              <div className="card-body">
                {!showDeleteConfirm ? (
                  <button
                    className="btn btn-outline-danger w-100"
                    onClick={() => setShowDeleteConfirm(true)}
                  >
                    <i className="fa fa-trash me-1" /> Delete Contact
                  </button>
                ) : (
                  <div>
                    <p className="text-danger mb-2">
                      Are you sure you want to delete <strong>{fullName}</strong>? This action
                      cannot be undone.
                    </p>
                    <div className="d-flex gap-2">
                      <button
                        className="btn btn-danger flex-fill"
                        onClick={handleDelete}
                        disabled={deleting}
                      >
                        {deleting ? 'Deleting...' : 'Yes, Delete'}
                      </button>
                      <button
                        className="btn btn-outline-secondary flex-fill"
                        onClick={() => setShowDeleteConfirm(false)}
                        disabled={deleting}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
