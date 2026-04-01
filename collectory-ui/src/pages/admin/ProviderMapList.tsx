import { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import Pagination from '../../components/common/Pagination';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

const PAGE_SIZE = 20;

interface ProviderMap {
  id: number;
  institution?: { uid: string; name: string };
  collection?: { uid: string; name: string };
  exact: boolean;
  matchAnyCollectionCode: boolean;
  institutionCodes: string;
  collectionCodes: string;
}

type SortDir = 'asc' | 'desc';

export default function ProviderMapList() {
  const [currentPage, setCurrentPage] = useState(1);
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const { data: maps = [], isLoading, error } = useQuery({
    queryKey: ['providerMaps'],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderMap[]>('/providerMap');
      return data;
    },
  });

  const sorted = useMemo(() => {
    const copy = [...maps];
    copy.sort((a, b) => {
      const cmp = a.id - b.id;
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return copy;
  }, [maps, sortDir]);

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const page = Math.min(currentPage, totalPages);
  const paged = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  function handleSort() {
    setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    setCurrentPage(1);
  }

  const sortIconEl = sortDir === 'asc' ? (
    <i className="fa fa-sort-asc ms-1" />
  ) : (
    <i className="fa fa-sort-desc ms-1" />
  );

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item active">Provider Maps</li>
          </ol>
        </nav>
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h1>Provider Maps</h1>
          <Link to="/providerMap/create" className="btn btn-primary">
            <i className="fa fa-plus me-1" /> New Provider Map
          </Link>
        </div>

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
          </div>
        )}

        {error && <div className="alert alert-danger">Failed to load provider maps.</div>}

        {!isLoading && !error && (
          <>
            <p className="text-muted">
              Showing {paged.length} of {sorted.length} provider maps
            </p>

            <table className="table table-striped table-hover">
              <thead>
                <tr>
                  <th role="button" onClick={handleSort}>
                    ID {sortIconEl}
                  </th>
                  <th>Institution</th>
                  <th>Collection</th>
                  <th>Exact</th>
                  <th>Match Any</th>
                  <th>Institution Codes</th>
                  <th>Collection Codes</th>
                </tr>
              </thead>
              <tbody>
                {paged.map((m) => (
                  <tr key={m.id}>
                    <td>
                      <Link to={`/providerMap/show/${m.id}`}>{m.id}</Link>
                    </td>
                    <td>
                      {m.institution ? (
                        <Link to={`/institution/show/${m.institution.uid}`}>{m.institution.name}</Link>
                      ) : '-'}
                    </td>
                    <td>
                      {m.collection ? (
                        <Link to={`/collection/show/${m.collection.uid}`}>{m.collection.name}</Link>
                      ) : '-'}
                    </td>
                    <td className="text-center">
                      {m.exact && <i className="fa fa-check text-success" />}
                    </td>
                    <td className="text-center">
                      {m.matchAnyCollectionCode && <i className="fa fa-check text-success" />}
                    </td>
                    <td>{m.institutionCodes || '-'}</td>
                    <td>{m.collectionCodes || '-'}</td>
                  </tr>
                ))}
                {paged.length === 0 && (
                  <tr>
                    <td colSpan={7} className="text-center text-muted py-4">
                      No provider maps found.
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
