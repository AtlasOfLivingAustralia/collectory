import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

function controllerFromUid(uid: string): string {
  if (uid.startsWith('co')) return 'collection';
  if (uid.startsWith('in')) return 'institution';
  if (uid.startsWith('dp')) return 'dataProvider';
  if (uid.startsWith('dr')) return 'dataResource';
  if (uid.startsWith('dh')) return 'dataHub';
  return 'public';
}

function shortClassName(className?: string): string {
  if (!className) return '';
  const parts = className.split('.');
  return parts[parts.length - 1] ?? '';
}

export default function ChangesReport() {
  const [who, setWho] = useState('');
  const [what, setWhat] = useState('');
  const [searchWho, setSearchWho] = useState('');
  const [searchWhat, setSearchWhat] = useState('');
  const [offset, setOffset] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'changes', searchWho, searchWhat, offset],
    queryFn: () => reportsApi.changes({ offset, who: searchWho, what: searchWhat }),
  });

  const handleSearch = () => {
    setOffset(0);
    setSearchWho(who);
    setSearchWhat(what);
  };

  const handleReset = () => {
    setWho('');
    setWhat('');
    setSearchWho('');
    setSearchWhat('');
    setOffset(0);
  };

  const handleNext = () => {
    setOffset(prev => prev + 100);
  };

  return (
    <ReportLayout title="Change History" isLoading={isLoading} error={error as Error}>
      <p>Recent changes to the registry.</p>

      <div className="card card-body mb-3">
        <fieldset>
          <legend className="h6">Search filters</legend>
          <div className="d-flex gap-3 align-items-end flex-wrap">
            <div>
              <label htmlFor="who" className="form-label">Who:</label>
              <input
                id="who"
                type="text"
                className="form-control"
                value={who}
                onChange={e => setWho(e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="what" className="form-label">What:</label>
              <input
                id="what"
                type="text"
                className="form-control"
                value={what}
                onChange={e => setWhat(e.target.value)}
              />
            </div>
            <button className="btn btn-outline-dark" onClick={handleSearch}>Search</button>
            <button className="btn btn-outline-dark" onClick={handleReset}>Reset</button>
            <button className="btn btn-outline-dark" onClick={handleNext}>
              Next 100 &raquo;
            </button>
          </div>
        </fieldset>
      </div>

      {data && (
        <table className="table table-striped table-bordered">
          <colgroup><col width="23%" /><col width="25%" /><col width="12%" /><col width="40%" /></colgroup>
          <thead>
            <tr className="table-secondary">
              <th>When</th>
              <th>Who</th>
              <th>Did</th>
              <th>What</th>
            </tr>
          </thead>
          <tbody>
            {data.changes.map(ch => (
              <tr key={ch.id}>
                <td>
                  <Link to={`/auditLogEvent/show/${ch.id}`}>
                    {ch.lastUpdated}
                  </Link>
                </td>
                <td>
                  <Link to={`/auditLogEvent/show/${ch.id}`}>
                    {ch.actor}
                  </Link>
                </td>
                <td>{ch.eventName}</td>
                <td>
                  {ch.eventName === 'UPDATE' && <><b>{ch.propertyName}</b> in </>}
                  {shortClassName(ch.className)}{' '}
                  {ch.uri ? (
                    ch.eventName === 'DELETE' ? (
                      <b>{ch.uri}</b>
                    ) : (
                      <Link to={`/${controllerFromUid(ch.uri)}/show/${ch.uri}`}>
                        <b>{ch.uri}</b>
                      </Link>
                    )
                  ) : ch.persistedObjectId ? (
                    <b>{ch.persistedObjectId}</b>
                  ) : null}
                </td>
              </tr>
            ))}
            {data.changes.length === 0 && (
              <tr><td colSpan={4} className="text-center text-muted py-3">No changes found.</td></tr>
            )}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
