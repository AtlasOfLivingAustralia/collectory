import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { DataHub, ProviderGroup } from '../../api/types';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

type MemberType = 'institution' | 'collection' | 'dataResource';

interface MemberEntry {
  uid: string;
  name: string;
}

const MEMBER_SECTIONS: { type: MemberType; label: string; field: keyof DataHub }[] = [
  { type: 'institution', label: 'Member Institutions', field: 'memberInstitutions' },
  { type: 'collection', label: 'Member Collections', field: 'memberCollections' },
  { type: 'dataResource', label: 'Member Data Resources', field: 'memberDataResources' },
];

function parseMembers(json?: string): string[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? (parsed as string[]) : [];
  } catch {
    return [];
  }
}

export default function MembersEditor() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchType, setSearchType] = useState<MemberType>('institution');
  const [searchQuery, setSearchQuery] = useState('');
  const [opError, setOpError] = useState<string | null>(null);

  const { data: dataHub, isLoading, error } = useQuery({
    queryKey: ['dataHub', id],
    queryFn: async () => {
      const { data } = await apiClient.get<DataHub>(`/dataHub/${id}`);
      return data;
    },
    enabled: !!id,
  });

  // Resolve member UIDs to names for display
  const memberUids = dataHub
    ? [
        ...parseMembers(dataHub.memberInstitutions),
        ...parseMembers(dataHub.memberCollections),
        ...parseMembers(dataHub.memberDataResources),
      ]
    : [];

  const { data: memberDetails = {} } = useQuery({
    queryKey: ['memberDetails', memberUids.join(',')],
    queryFn: async () => {
      const details: Record<string, MemberEntry> = {};
      await Promise.all(
        memberUids.map(async (uid) => {
          try {
            const { data } = await apiClient.get<ProviderGroup>(`/lookup/summary/${uid}`);
            details[uid] = { uid, name: (data as { name: string }).name ?? uid };
          } catch {
            details[uid] = { uid, name: uid };
          }
        }),
      );
      return details;
    },
    enabled: memberUids.length > 0,
  });

  // Search for entities to add
  const { data: searchResults = [] } = useQuery({
    queryKey: ['search', searchType, searchQuery],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderGroup[]>(`/${searchType}`);
      const q = searchQuery.toLowerCase();
      const section = MEMBER_SECTIONS.find((s) => s.type === searchType);
      const currentMembers = section && dataHub ? parseMembers(dataHub[section.field] as string | undefined) : [];
      return data
        .filter((e) => e.name.toLowerCase().includes(q))
        .filter((e) => !currentMembers.includes(e.uid))
        .slice(0, 10);
    },
    enabled: searchQuery.length >= 2,
  });

  const mutation = useMutation({
    mutationFn: async ({ field, uids }: { field: string; uids: string[] }) => {
      await apiClient.put(`/dataHub/${id}`, { [field]: JSON.stringify(uids) });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataHub', id] });
      setOpError(null);
    },
    onError: (err: Error) => setOpError(err.message),
  });

  function addMember(uid: string) {
    if (!dataHub) return;
    const section = MEMBER_SECTIONS.find((s) => s.type === searchType);
    if (!section) return;
    const current = parseMembers(dataHub[section.field] as string | undefined);
    if (!current.includes(uid)) {
      mutation.mutate({ field: section.field, uids: [...current, uid] });
    }
    setSearchQuery('');
  }

  function removeMember(type: MemberType, uid: string) {
    if (!dataHub) return;
    const section = MEMBER_SECTIONS.find((s) => s.type === type);
    if (!section) return;
    const current = parseMembers(dataHub[section.field] as string | undefined);
    mutation.mutate({ field: section.field, uids: current.filter((u) => u !== uid) });
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

  if (error || !dataHub) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">Failed to load data hub.</div>
      </div>
    );
  }

  function showPath(type: MemberType): string {
    return `/${type}/show`;
  }

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <Breadcrumb
          parent={{ url: '/manage/list', label: 'Metadata management' }}
          extra={[
            { url: '/dataHub/list', label: 'Data Hubs' },
            { url: `/dataHub/show/${id}`, label: dataHub?.name || id || '' },
          ]}
          current="Members"
        />
        <h1>Members - {dataHub.name}</h1>

        {opError && <div className="alert alert-danger">{opError}</div>}

        {MEMBER_SECTIONS.map((section) => {
          const uids = parseMembers(dataHub[section.field] as string | undefined);
          return (
            <div key={section.type} className="mb-4">
              <h3>{section.label}</h3>
              {uids.length === 0 ? (
                <p className="text-muted">No members.</p>
              ) : (
                <ul className="list-group mb-2">
                  {uids.map((uid) => (
                    <li
                      key={uid}
                      className="list-group-item d-flex justify-content-between align-items-center"
                    >
                      <Link to={`${showPath(section.type)}/${uid}`}>
                        {memberDetails[uid]?.name ?? uid}
                      </Link>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-danger"
                        disabled={mutation.isPending}
                        onClick={() => {
                          if (window.confirm(`Remove ${memberDetails[uid]?.name ?? uid}?`)) {
                            removeMember(section.type, uid);
                          }
                        }}
                      >
                        <i className="fa fa-times" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          );
        })}

        {/* Add member */}
        <h3>Add Member</h3>
        <div className="card card-body mb-3">
          <div className="row mb-2">
            <div className="col-auto">
              <select
                className="form-select"
                value={searchType}
                onChange={(e) => {
                  setSearchType(e.target.value as MemberType);
                  setSearchQuery('');
                }}
              >
                <option value="institution">Institution</option>
                <option value="collection">Collection</option>
                <option value="dataResource">Data Resource</option>
              </select>
            </div>
            <div className="col">
              <input
                type="text"
                className="form-control"
                placeholder={`Search ${searchType}s...`}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          {searchResults.length > 0 && (
            <ul className="list-group">
              {searchResults.map((entity) => (
                <li
                  key={entity.uid}
                  className="list-group-item d-flex justify-content-between align-items-center"
                >
                  <span>
                    {entity.name} <code className="ms-1">{entity.uid}</code>
                  </span>
                  <button
                    type="button"
                    className="btn btn-sm btn-primary"
                    disabled={mutation.isPending}
                    onClick={() => addMember(entity.uid)}
                  >
                    <i className="fa fa-plus me-1" /> Add
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <button
          type="button"
          className="btn btn-outline-dark"
          onClick={() => navigate(`/dataHub/show/${id}`)}
        >
          Back
        </button>
      </div>
    </ProtectedRoute>
  );
}
