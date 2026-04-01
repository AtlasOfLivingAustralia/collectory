import { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import Pagination from '../../components/common/Pagination';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

const PAGE_SIZE = 20;

interface Licence {
  id: number;
  name: string;
  acronym: string;
  licenceVersion: string;
  url: string;
  imageUrl: string;
}

type SortField = 'name' | 'acronym' | 'licenceVersion' | 'url';
type SortDir = 'asc' | 'desc';

export default function LicenceList() {
  const [search, setSearch] = useState('');
  const [sortField, setSortField] = useState<SortField>('name');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [currentPage, setCurrentPage] = useState(1);

  const { data: licences = [], isLoading, error } = useQuery({
    queryKey: ['licences'],
    queryFn: async () => {
      const { data } = await apiClient.get<Licence[]>('/licence');
      return data;
    },
  });

  const filtered = useMemo(() => {
    if (!search.trim()) return licences;
    const q = search.toLowerCase();
    return licences.filter((l) => l.name && l.name.toLowerCase().includes(q));
  }, [licences, search]);

  const sorted = useMemo(() => {
    const copy = [...filtered];
    copy.sort((a, b) => {
      const aVal = (a[sortField] ?? '').toLowerCase();
      const bVal = (b[sortField] ?? '').toLowerCase();
      const cmp = aVal.localeCompare(bVal);
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return copy;
  }, [filtered, sortField, sortDir]);

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const page = Math.min(currentPage, totalPages);
  const paged = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  function handleSort(field: SortField) {
    if (sortField === field) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDir('asc');
    }
    setCurrentPage(1);
  }

  function sortIcon(field: SortField) {
    if (sortField !== field) return <i className="fa fa-sort text-muted ms-1" />;
    return sortDir === 'asc' ? (
      <i className="fa fa-sort-asc ms-1" />
    ) : (
      <i className="fa fa-sort-desc ms-1" />
    );
  }

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item active">Licences</li>
          </ol>
        </nav>
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h1>Licences</h1>
          <Link to="/licence/create" className="btn btn-primary">
            <i className="fa fa-plus me-1" /> New Licence
          </Link>
        </div>

        <div className="mb-3">
          <input
            type="text"
            className="form-control"
            placeholder="Search by name..."
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

        {error && <div className="alert alert-danger">Failed to load licences.</div>}

        {!isLoading && !error && (
          <>
            <p className="text-muted">
              Showing {paged.length} of {filtered.length} licences
            </p>

            <table className="table table-striped table-hover">
              <thead>
                <tr>
                  <th role="button" onClick={() => handleSort('name')}>
                    Name {sortIcon('name')}
                  </th>
                  <th role="button" onClick={() => handleSort('acronym')}>
                    Acronym {sortIcon('acronym')}
                  </th>
                  <th role="button" onClick={() => handleSort('licenceVersion')}>
                    Version {sortIcon('licenceVersion')}
                  </th>
                  <th role="button" onClick={() => handleSort('url')}>
                    URL {sortIcon('url')}
                  </th>
                  <th>Image</th>
                </tr>
              </thead>
              <tbody>
                {paged.map((l) => (
                  <tr key={l.id} role="button" onClick={() => window.location.href = `/licence/show/${l.id}`} style={{ cursor: 'pointer' }}>
                    <td>
                      <Link to={`/licence/show/${l.id}`}>{l.name || '-'}</Link>
                    </td>
                    <td>{l.acronym || '-'}</td>
                    <td>{l.licenceVersion || '-'}</td>
                    <td>{l.url ? <a href={l.url} target="_blank" rel="noreferrer">{l.url}</a> : '-'}</td>
                    <td>
                      {l.imageUrl ? (
                        <img src={l.imageUrl} alt={l.name} style={{ maxHeight: '30px' }} />
                      ) : '-'}
                    </td>
                  </tr>
                ))}
                {paged.length === 0 && (
                  <tr>
                    <td colSpan={5} className="text-center text-muted py-4">
                      {search ? 'No matching licences found.' : 'No licences found.'}
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
