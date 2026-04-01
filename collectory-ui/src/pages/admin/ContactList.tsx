import { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getContacts } from '../../api/endpoints/contacts';
import Pagination from '../../components/common/Pagination';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

const PAGE_SIZE = 20;

type SortField = 'firstName' | 'lastName' | 'email' | 'organizationName' | 'phone';
type SortDir = 'asc' | 'desc';

export default function ContactList() {
  const [search, setSearch] = useState('');
  const [sortField, setSortField] = useState<SortField>('lastName');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [currentPage, setCurrentPage] = useState(1);

  const { data: contacts = [], isLoading, error } = useQuery({
    queryKey: ['contacts'],
    queryFn: getContacts,
  });

  // Filter
  const filtered = useMemo(() => {
    if (!search.trim()) return contacts;
    const q = search.toLowerCase();
    return contacts.filter(
      (c) =>
        (c.firstName && c.firstName.toLowerCase().includes(q)) ||
        (c.lastName && c.lastName.toLowerCase().includes(q)) ||
        (c.email && c.email.toLowerCase().includes(q)) ||
        (c.organizationName && c.organizationName.toLowerCase().includes(q)),
    );
  }, [contacts, search]);

  // Sort
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

  // Paginate
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
            <li className="breadcrumb-item active">Contacts</li>
          </ol>
        </nav>
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h1>Contacts</h1>
          <Link to="/contact/create" className="btn btn-primary">
            <i className="fa fa-plus me-1" /> New Contact
          </Link>
        </div>

        {/* Search */}
        <div className="mb-3">
          <input
            type="text"
            className="form-control"
            placeholder="Search contacts..."
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

        {error && <div className="alert alert-danger">Failed to load contacts.</div>}

        {!isLoading && !error && (
          <>
            <p className="text-muted">
              Showing {paged.length} of {filtered.length} contacts
            </p>

            <table className="table table-striped table-hover">
              <thead>
                <tr>
                  <th role="button" onClick={() => handleSort('firstName')}>
                    First Name {sortIcon('firstName')}
                  </th>
                  <th role="button" onClick={() => handleSort('lastName')}>
                    Last Name {sortIcon('lastName')}
                  </th>
                  <th role="button" onClick={() => handleSort('email')}>
                    Email {sortIcon('email')}
                  </th>
                  <th role="button" onClick={() => handleSort('organizationName')}>
                    Organization {sortIcon('organizationName')}
                  </th>
                  <th role="button" onClick={() => handleSort('phone')}>
                    Phone {sortIcon('phone')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {paged.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <Link to={`/contact/show/${c.id}`}>{c.firstName || '-'}</Link>
                    </td>
                    <td>{c.lastName || '-'}</td>
                    <td>{c.email || '-'}</td>
                    <td>{c.organizationName || '-'}</td>
                    <td>{c.phone || '-'}</td>
                  </tr>
                ))}
                {paged.length === 0 && (
                  <tr>
                    <td colSpan={5} className="text-center text-muted py-4">
                      {search ? 'No matching contacts found.' : 'No contacts found.'}
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
