import { useState, useMemo } from 'react';
import { useParams, Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import type { ProviderGroup } from '../../api/types';
import Pagination from '../../components/common/Pagination';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

/** Maps URL entityType param to API path and display label */
const ENTITY_CONFIG: Record<string, { apiPath: string; plural: string; singular: string }> = {
  collection: { apiPath: '/collection', plural: 'Collections', singular: 'Collection' },
  institution: { apiPath: '/institution', plural: 'Institutions', singular: 'Institution' },
  dataResource: { apiPath: '/dataResource', plural: 'Data Resources', singular: 'Data Resource' },
  dataProvider: { apiPath: '/dataProvider', plural: 'Data Providers', singular: 'Data Provider' },
  dataHub: { apiPath: '/dataHub', plural: 'Data Hubs', singular: 'Data Hub' },
};

type SortField = string;
type SortDir = 'asc' | 'desc';

const PAGE_SIZE = 20;

/* ------------------------------------------------------------------ */
/*  Column configuration per entity type                              */
/* ------------------------------------------------------------------ */

interface ColumnDef {
  key: string;
  label: string;
  sortable: boolean;
  render: (entity: Record<string, unknown>, entityType: string) => React.ReactNode;
}

/** Name column (shared by all entity types) */
const nameColumn = (showAcronym: boolean): ColumnDef => ({
  key: 'name',
  label: 'Name',
  sortable: true,
  render: (entity, entityType) => (
    <>
      <Link to={`/${entityType}/show/${entity.uid}`}>{entity.name as string}</Link>
      {showAcronym && entity.acronym && (
        <span className="text-muted ms-1">({entity.acronym as string})</span>
      )}
    </>
  ),
});

const acronymColumn: ColumnDef = {
  key: 'acronym',
  label: 'Acronym',
  sortable: true,
  render: (entity) => <span>{(entity.acronym as string) || ''}</span>,
};

const uidColumn: ColumnDef = {
  key: 'uid',
  label: 'UID',
  sortable: true,
  render: (entity) => <code>{entity.uid as string}</code>,
};

const alaPartnerColumn: ColumnDef = {
  key: 'isALAPartner',
  label: 'ALA Partner',
  sortable: true,
  render: (entity) =>
    entity.isALAPartner ? (
      <i className="fa fa-check text-success" />
    ) : (
      <i className="fa fa-times text-danger" />
    ),
};

const lastUpdatedColumn: ColumnDef = {
  key: 'lastUpdated',
  label: 'Last Updated',
  sortable: true,
  render: (entity) => <span>{new Date(entity.lastUpdated as string).toLocaleDateString()}</span>,
};

/* Entity-specific columns */

const institutionColumn: ColumnDef = {
  key: 'institution.name',
  label: 'Institution',
  sortable: true,
  render: (entity) => {
    const inst = entity.institution as { uid?: string; name?: string } | null | undefined;
    if (!inst?.name) return <span className="text-muted">-</span>;
    return inst.uid ? <Link to={`/institution/show/${inst.uid}`}>{inst.name}</Link> : <span>{inst.name}</span>;
  },
};

const collectionTypeColumn: ColumnDef = {
  key: 'keywords',
  label: 'Collection Type',
  sortable: true,
  render: (entity) => <span>{(entity.collectionType as string) || (entity.keywords as string) || ''}</span>,
};

const resourceTypeColumn: ColumnDef = {
  key: 'resourceType',
  label: 'Resource Type',
  sortable: true,
  render: (entity) => <span>{(entity.resourceType as string) || ''}</span>,
};

const privateColumn: ColumnDef = {
  key: 'isPrivate',
  label: 'Private',
  sortable: true,
  render: (entity) =>
    entity.isPrivate ? (
      <span className="badge bg-warning text-dark">Yes</span>
    ) : (
      <span className="badge bg-secondary">No</span>
    ),
};

const licenceColumn: ColumnDef = {
  key: 'licenseType',
  label: 'Licence',
  sortable: true,
  render: (entity) => <span>{(entity.licenseType as string) || ''}</span>,
};

const verifiedColumn: ColumnDef = {
  key: 'verified',
  label: 'Verified',
  sortable: true,
  render: (entity) =>
    entity.verified ? (
      <i className="fa fa-check text-success" />
    ) : (
      <i className="fa fa-times text-danger" />
    ),
};

const resourceCountColumn: ColumnDef = {
  key: 'resources',
  label: 'Resources',
  sortable: true,
  render: (entity) => {
    const resources = entity.resources as unknown[] | null | undefined;
    return <span>{Array.isArray(resources) ? resources.length : 0}</span>;
  },
};

const gbifRegisteredColumn: ColumnDef = {
  key: 'gbifRegistryKey',
  label: 'GBIF Registered',
  sortable: true,
  render: (entity) =>
    entity.gbifRegistryKey ? (
      <i className="fa fa-check text-success" />
    ) : (
      <i className="fa fa-times text-danger" />
    ),
};

/** Column definitions per entity type */
const COLUMNS_BY_TYPE: Record<string, ColumnDef[]> = {
  collection: [nameColumn(false), acronymColumn, institutionColumn, collectionTypeColumn],
  institution: [nameColumn(false), acronymColumn, alaPartnerColumn],
  dataResource: [nameColumn(true), uidColumn, resourceTypeColumn, privateColumn, licenceColumn, verifiedColumn],
  dataProvider: [nameColumn(true), uidColumn, resourceCountColumn, gbifRegisteredColumn],
  dataHub: [nameColumn(true), uidColumn, acronymColumn],
};

/** Fallback generic columns */
const DEFAULT_COLUMNS: ColumnDef[] = [nameColumn(true), uidColumn, alaPartnerColumn, lastUpdatedColumn];

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function EntityList() {
  const { entityType } = useParams<{ entityType: string }>();
  const config = entityType ? ENTITY_CONFIG[entityType] : undefined;
  const columns = (entityType && COLUMNS_BY_TYPE[entityType]) || DEFAULT_COLUMNS;

  const [searchParams] = useSearchParams();
  const [search, setSearch] = useState(searchParams.get('term') ?? '');
  const [sortField, setSortField] = useState<SortField>('name');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [currentPage, setCurrentPage] = useState(1);

  const { data: entities = [], isLoading, error } = useQuery({
    queryKey: [entityType, 'list'],
    queryFn: async () => {
      if (!config) return [];
      const { data } = await apiClient.get<ProviderGroup[]>(config.apiPath);
      return data;
    },
    enabled: !!config,
  });

  // Filter
  const filtered = useMemo(() => {
    if (!search.trim()) return entities;
    const q = search.toLowerCase();
    return entities.filter(
      (e) =>
        e.name.toLowerCase().includes(q) ||
        e.uid.toLowerCase().includes(q) ||
        (e.acronym && e.acronym.toLowerCase().includes(q)),
    );
  }, [entities, search]);

  // Sort
  const sorted = useMemo(() => {
    const copy = [...filtered];
    copy.sort((a, b) => {
      let cmp = 0;
      const aRec = a as unknown as Record<string, unknown>;
      const bRec = b as unknown as Record<string, unknown>;

      // Handle dotted keys like 'institution.name'
      const getVal = (obj: Record<string, unknown>, key: string): unknown => {
        const parts = key.split('.');
        let v: unknown = obj;
        for (const p of parts) {
          if (v == null || typeof v !== 'object') return undefined;
          v = (v as Record<string, unknown>)[p];
        }
        return v;
      };

      const aVal = getVal(aRec, sortField);
      const bVal = getVal(bRec, sortField);

      if (typeof aVal === 'string' && typeof bVal === 'string') {
        cmp = aVal.localeCompare(bVal);
      } else if (typeof aVal === 'number' && typeof bVal === 'number') {
        cmp = aVal - bVal;
      } else if (typeof aVal === 'boolean' && typeof bVal === 'boolean') {
        cmp = (aVal ? 1 : 0) - (bVal ? 1 : 0);
      } else if (Array.isArray(aVal) && Array.isArray(bVal)) {
        cmp = aVal.length - bVal.length;
      } else {
        cmp = String(aVal ?? '').localeCompare(String(bVal ?? ''));
      }

      return sortDir === 'asc' ? cmp : -cmp;
    });
    return copy;
  }, [filtered, sortField, sortDir]);

  // Paginate
  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const page = Math.min(currentPage, totalPages);
  const paged = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  function handleSort(field: string) {
    if (sortField === field) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDir('asc');
    }
    setCurrentPage(1);
  }

  function sortIcon(field: string) {
    if (sortField !== field) return <i className="fa fa-sort text-muted ms-1" />;
    return sortDir === 'asc' ? (
      <i className="fa fa-sort-asc ms-1" />
    ) : (
      <i className="fa fa-sort-desc ms-1" />
    );
  }

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
        <Breadcrumb parent={{ url: '/manage/list', label: 'Metadata management' }} current={config.plural} />
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h1>{config.plural}</h1>
          <Link to={`/${entityType}/create`} className="btn btn-primary">
            <i className="fa fa-plus me-1" /> New {config.singular}
          </Link>
        </div>

        {/* Search */}
        <div className="mb-3">
          <input
            type="text"
            className="form-control"
            placeholder={`Search ${config.plural.toLowerCase()}...`}
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setCurrentPage(1);
            }}
          />
        </div>

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
          </div>
        )}

        {error && (
          <div className="alert alert-danger">
            Failed to load {config.plural.toLowerCase()}.
          </div>
        )}

        {!isLoading && !error && (
          <>
            <p className="text-muted">
              Showing {paged.length} of {filtered.length} {config.plural.toLowerCase()}
            </p>

            <table className="table table-striped table-hover">
              <thead>
                <tr>
                  {columns.map((col) => (
                    <th
                      key={col.key}
                      role={col.sortable ? 'button' : undefined}
                      onClick={col.sortable ? () => handleSort(col.key) : undefined}
                    >
                      {col.label} {col.sortable && sortIcon(col.key)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {paged.map((entity) => (
                  <tr key={entity.uid}>
                    {columns.map((col) => (
                      <td key={col.key}>
                        {col.render(entity as unknown as Record<string, unknown>, entityType!)}
                      </td>
                    ))}
                  </tr>
                ))}
                {paged.length === 0 && (
                  <tr>
                    <td colSpan={columns.length} className="text-center text-muted py-4">
                      {search ? 'No matching entities found.' : `No ${config.plural.toLowerCase()} found.`}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>

            <Pagination
              currentPage={page}
              totalPages={totalPages}
              onPageChange={setCurrentPage}
            />
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
