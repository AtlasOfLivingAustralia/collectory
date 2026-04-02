import { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';

import { useQuery } from '@tanstack/react-query';
import apiClient from '../../api/client';
import Pagination from '../../components/common/Pagination';
import { ProtectedRoute } from '../../auth/ProtectedRoute';
import Breadcrumb from '../../components/public/Breadcrumb';

const PAGE_SIZE = 20;

interface ProviderCode {
  id: number;
  code: string;
}

type SortDir = 'asc' | 'desc';

export default function ProviderCodeList() {
  const [search, setSearch] = useState('');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [currentPage, setCurrentPage] = useState(1);

  const { data: codes = [], isLoading, error } = useQuery({
    queryKey: ['providerCodes'],
    queryFn: async () => {
      const { data } = await apiClient.get<ProviderCode[]>('/providerCode');
      return data;
    },
  });

  const filtered = useMemo(() => {
    if (!search.trim()) return codes;
    const q = search.toLowerCase();
    return codes.filter((c) => c.code && c.code.toLowerCase().includes(q));
  }, [codes, search]);

  const sorted = useMemo(() => {
    const copy = [...filtered];
    copy.sort((a, b) => {
      const cmp = (a.code ?? '').toLowerCase().localeCompare((b.code ?? '').toLowerCase());
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return copy;
  }, [filtered, sortDir]);

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
        <Breadcrumb parent={{ url: '/manage/list', label: 'Metadata management' }} current="Provider Codes" />
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h1>Provider Codes</h1>
          <Link to="/providerCode/create" className="btn btn-primary">
            <i className="fa fa-plus me-1" /> New Provider Code
          </Link>
        </div>

        <div className="mb-3">
          <input
            type="text"
            className="form-control"
            placeholder="Search by code..."
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

        {error && <div className="alert alert-danger">Failed to load provider codes.</div>}

        {!isLoading && !error && (
          <>
            <p className="text-muted">
              Showing {paged.length} of {filtered.length} provider codes
            </p>

            <table className="table table-striped table-hover">
              <thead>
                <tr>
                  <th role="button" onClick={handleSort}>
                    Code {sortIconEl}
                  </th>
                </tr>
              </thead>
              <tbody>
                {paged.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <Link to={`/providerCode/show/${c.id}`}>{c.code || '-'}</Link>
                    </td>
                  </tr>
                ))}
                {paged.length === 0 && (
                  <tr>
                    <td className="text-center text-muted py-4">
                      {search ? 'No matching provider codes found.' : 'No provider codes found.'}
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
