import { useState, useMemo, useCallback, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../api/client';
import Pagination from '../components/common/Pagination';
import FilterBadge from '../components/common/FilterBadge';
import { useConfig } from '../hooks/useConfig';
import Breadcrumb from '../components/public/Breadcrumb';

/* ------------------------------------------------------------------ */
/* Types                                                               */
/* ------------------------------------------------------------------ */

interface CondensedDataset {
  uid: string;
  name: string;
  uri?: string;
  provider?: string;
  institution?: string;
  acronym?: string;
  pubDescription?: string;
  techDescription?: string;
  resourceType?: string;
  licenseType?: string;
  licenseVersion?: string;
  status?: string;
  contentTypes?: string;
  websiteUrl?: string;
  dateCreated?: string;
  lastUpdated?: string;
  hubMembership?: string;
}

type SortField = 'name' | 'resourceType' | 'licenseType';
type SortDir = 'asc' | 'desc';

interface FacetState {
  resourceType: string[];
  licenseType: string[];
  status: string[];
  contentType: string[];
}

const EMPTY_FACETS: FacetState = {
  resourceType: [],
  licenseType: [],
  status: [],
  contentType: [],
};

const PAGE_SIZES = [10, 20, 50, 100, 500] as const;

/* ------------------------------------------------------------------ */
/* Data hook                                                           */
/* ------------------------------------------------------------------ */

function useCondensedDatasets() {
  return useQuery<CondensedDataset[]>({
    queryKey: ['condensedDatasets'],
    queryFn: () => apiClient.get<CondensedDataset[]>('/public/condensed.json').then((r) => r.data),
    staleTime: 5 * 60 * 1000,
  });
}

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

function parseContentTypes(raw?: string): string[] {
  if (!raw) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed.map(String);
  } catch {
    // not JSON — could be comma-separated
  }
  return raw.split(',').map((s) => s.trim()).filter(Boolean);
}

function buildFacetCounts(datasets: CondensedDataset[], facetKey: keyof FacetState): Map<string, number> {
  const counts = new Map<string, number>();
  for (const ds of datasets) {
    let values: string[];
    if (facetKey === 'contentType') {
      values = parseContentTypes(ds.contentTypes);
    } else {
      const raw = ds[facetKey as keyof Pick<CondensedDataset, 'resourceType' | 'licenseType' | 'status'>];
      values = raw ? [raw] : ['none'];
    }
    for (const v of values) {
      counts.set(v, (counts.get(v) ?? 0) + 1);
    }
  }
  return new Map([...counts.entries()].sort((a, b) => b[1] - a[1]));
}

function matchesFacets(ds: CondensedDataset, facets: FacetState): boolean {
  if (facets.resourceType.length > 0 && !facets.resourceType.includes(ds.resourceType ?? 'none')) {
    return false;
  }
  if (facets.licenseType.length > 0 && !facets.licenseType.includes(ds.licenseType ?? 'none')) {
    return false;
  }
  if (facets.status.length > 0 && !facets.status.includes(ds.status ?? 'none')) {
    return false;
  }
  if (facets.contentType.length > 0) {
    const types = parseContentTypes(ds.contentTypes);
    if (!facets.contentType.some((ct) => types.includes(ct))) return false;
  }
  return true;
}

function downloadCsv(datasets: CondensedDataset[]) {
  const headers = ['Name', 'UID', 'Resource Type', 'License', 'Status', 'Provider'];
  const rows = datasets.map(ds => [
    `"${(ds.name ?? '').replace(/"/g, '""')}"`,
    ds.uid,
    ds.resourceType ?? '',
    ds.licenseType ?? '',
    ds.status ?? '',
    `"${(ds.provider ?? '').replace(/"/g, '""')}"`,
  ]);
  const csv = [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'datasets.csv';
  a.click();
  URL.revokeObjectURL(url);
}

function serializeState(
  search: string,
  facets: FacetState,
  sortField: SortField,
  sortDir: SortDir,
  page: number,
  pageSize: number,
): string {
  const obj: Record<string, unknown> = {};
  if (search) obj.q = search;
  if (facets.resourceType.length) obj.rt = facets.resourceType;
  if (facets.licenseType.length) obj.lt = facets.licenseType;
  if (facets.status.length) obj.st = facets.status;
  if (facets.contentType.length) obj.ct = facets.contentType;
  if (sortField !== 'name') obj.sf = sortField;
  if (sortDir !== 'asc') obj.sd = sortDir;
  if (page !== 1) obj.p = page;
  if (pageSize !== 10) obj.ps = pageSize;
  return Object.keys(obj).length ? JSON.stringify(obj) : '';
}

function deserializeState(hash: string): {
  search: string;
  facets: FacetState;
  sortField: SortField;
  sortDir: SortDir;
  page: number;
  pageSize: number;
} {
  const defaults = { search: '', facets: { ...EMPTY_FACETS }, sortField: 'name' as SortField, sortDir: 'asc' as SortDir, page: 1, pageSize: 10 };
  if (!hash) return defaults;
  try {
    const raw = hash.startsWith('#') ? hash.slice(1) : hash;
    if (!raw) return defaults;
    const obj = JSON.parse(decodeURIComponent(raw)) as Record<string, unknown>;
    return {
      search: (obj.q as string) ?? '',
      facets: {
        resourceType: (obj.rt as string[]) ?? [],
        licenseType: (obj.lt as string[]) ?? [],
        status: (obj.st as string[]) ?? [],
        contentType: (obj.ct as string[]) ?? [],
      },
      sortField: (obj.sf as SortField) ?? 'name',
      sortDir: (obj.sd as SortDir) ?? 'asc',
      page: (obj.p as number) ?? 1,
      pageSize: (obj.ps as number) ?? 10,
    };
  } catch {
    return defaults;
  }
}

/* ------------------------------------------------------------------ */
/* Facet panel sub-component                                           */
/* ------------------------------------------------------------------ */

interface FacetPanelProps {
  title: string;
  counts: Map<string, number>;
  selected: string[];
  onToggle: (value: string) => void;
}

function FacetPanel({ title, counts, selected, onToggle }: FacetPanelProps) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(false);
  const entries = Array.from(counts.entries());
  const limit = 6;
  const visible = expanded ? entries : entries.slice(0, limit);
  const hasMore = entries.length > limit;

  return (
    <div className="mb-3">
      <h6 className="fw-bold">{title}</h6>
      <ul className="list-unstyled mb-1">
        {visible.map(([value, count]) => (
          <li key={value}>
            <button
              className={`btn btn-sm btn-link text-start text-decoration-none p-0 ${selected.includes(value) ? 'fw-bold' : ''}`}
              onClick={() => onToggle(value)}
            >
              {selected.includes(value) && <i className="fa fa-check-square-o me-1" />}
              {!selected.includes(value) && <i className="fa fa-square-o me-1" />}
              {value} <span className="text-muted">({count})</span>
            </button>
          </li>
        ))}
      </ul>
      {hasMore && (
        <button className="btn btn-sm btn-link p-0" onClick={() => setExpanded(!expanded)}>
          {expanded ? t('datasets.less', 'Less') : t('datasets.more', 'More...')}
        </button>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Main component                                                      */
/* ------------------------------------------------------------------ */

export default function DatasetList() {
  const { t } = useTranslation();
  const { data: config } = useConfig();
  const showExtraInfo = config?.showExtraInfoInDataSetsView?.enabled ?? false;
  const relativeTime = config?.showExtraInfoInDataSetsView?.relativeTime ?? false;
  const biocacheUiUrl = config?.biocacheUiUrl ?? '';

  // Initialize state from URL hash
  const initial = useMemo(() => deserializeState(window.location.hash), []);
  const [search, setSearch] = useState(initial.search);
  const [facets, setFacets] = useState<FacetState>(initial.facets);
  const [sortField] = useState<SortField>(initial.sortField);
  const [sortDir] = useState<SortDir>(initial.sortDir);
  const [currentPage, setCurrentPage] = useState(initial.page);
  const [pageSize, setPageSize] = useState(initial.pageSize);
  const [expandedRow, setExpandedRow] = useState<string | null>(null);

  const { data: allDatasets, isLoading, error } = useCondensedDatasets();

  // Fetch biocache record counts per data resource UID when showExtraInfo is enabled
  const { data: biocacheCountsData } = useQuery<Map<string, number>>({
    queryKey: ['biocacheResourceCounts'],
    queryFn: async () => {
      const resp = await apiClient.get<Array<{ fieldResult: Array<{ label: string; count: number }> }>>(
        '/proxy/biocache/occurrences/facets?facets=data_resource_uid&pageSize=0&flimit=-1'
      );
      const counts = new Map<string, number>();
      const facetData = resp.data?.[0];
      if (facetData?.fieldResult) {
        for (const item of facetData.fieldResult) {
          counts.set(item.label, item.count);
        }
      }
      return counts;
    },
    enabled: showExtraInfo,
    staleTime: 5 * 60 * 1000,
  });

  const biocacheCounts = biocacheCountsData ?? new Map<string, number>();

  // Sync state to URL hash
  useEffect(() => {
    const hash = serializeState(search, facets, sortField, sortDir, currentPage, pageSize);
    window.location.hash = hash;
  }, [search, facets, sortField, sortDir, currentPage, pageSize]);

  // Text-filtered datasets
  const textFiltered = useMemo(() => {
    if (!allDatasets) return [];
    if (!search.trim()) return allDatasets;
    const q = search.toLowerCase();
    return allDatasets.filter(
      (ds) =>
        ds.name?.toLowerCase().includes(q) || ds.pubDescription?.toLowerCase().includes(q),
    );
  }, [allDatasets, search]);

  // Facet-filtered datasets
  const facetFiltered = useMemo(
    () => textFiltered.filter((ds) => matchesFacets(ds, facets)),
    [textFiltered, facets],
  );

  // Sorted datasets
  const sorted = useMemo(() => {
    const items = [...facetFiltered];
    items.sort((a, b) => {
      const aVal = (a[sortField] ?? '').toLowerCase();
      const bVal = (b[sortField] ?? '').toLowerCase();
      const cmp = aVal.localeCompare(bVal);
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return items;
  }, [facetFiltered, sortField, sortDir]);

  // Pagination
  const totalPages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const safePage = Math.min(currentPage, totalPages);
  const paginated = useMemo(
    () => sorted.slice((safePage - 1) * pageSize, safePage * pageSize),
    [sorted, safePage, pageSize],
  );

  // Facet counts (computed from text-filtered, NOT facet-filtered, so users can see all available values)
  // But for cross-filtering, we compute from the current filtered set
  const resourceTypeCounts = useMemo(() => buildFacetCounts(facetFiltered, 'resourceType'), [facetFiltered]);
  const licenseTypeCounts = useMemo(() => buildFacetCounts(facetFiltered, 'licenseType'), [facetFiltered]);
  const statusCounts = useMemo(() => buildFacetCounts(facetFiltered, 'status'), [facetFiltered]);
  const contentTypeCounts = useMemo(() => buildFacetCounts(facetFiltered, 'contentType'), [facetFiltered]);

  // Facet toggle handler
  const toggleFacet = useCallback(
    (facetKey: keyof FacetState, value: string) => {
      setFacets((prev) => {
        const current = prev[facetKey];
        const next = current.includes(value)
          ? current.filter((v) => v !== value)
          : [...current, value];
        return { ...prev, [facetKey]: next };
      });
      setCurrentPage(1);
    },
    [],
  );

  const removeFilter = useCallback(
    (facetKey: keyof FacetState, value: string) => {
      setFacets((prev) => ({
        ...prev,
        [facetKey]: prev[facetKey].filter((v) => v !== value),
      }));
      setCurrentPage(1);
    },
    [],
  );

  const clearAllFilters = useCallback(() => {
    setFacets({ ...EMPTY_FACETS });
    setSearch('');
    setCurrentPage(1);
  }, []);

  const hasActiveFilters =
    search.trim().length > 0 ||
    facets.resourceType.length > 0 ||
    facets.licenseType.length > 0 ||
    facets.status.length > 0 ||
    facets.contentType.length > 0;

  if (error) {
    return (
      <div className="container-fluid mt-3">
        <div className="alert alert-danger">{t('datasets.error', 'Failed to load datasets.')}</div>
      </div>
    );
  }

  return (
    <>
    <Breadcrumb current={t('public.datasets.title', 'Datasets')} />
    <div className="container-fluid mt-3">
      <div id="header">
        <div className="full-width">
          <h1>{t('public.datasets.title', 'Datasets')}</h1>
          <p>{t('public.datasets.header.message', 'A list of all datasets available.')}</p>
        </div>
      </div>

      <div className="collectory-content row">
        {/* Sidebar — matches dataSets.gsp #sidebarBox */}
        <div id="sidebarBox" className="col-md-3 facets card card-body">
          <div className="sidebar-header">
            <h3>{t('datasets.sidebar.header', 'Refine results')}</h3>
          </div>

          {/* Active filters */}
          {hasActiveFilters && (
            <div id="currentFilterHolder" className="mb-3">
              {search.trim() && (
                <FilterBadge
                  label={`${t('datasets.search', 'Search')}: ${search}`}
                  value="search"
                  onRemove={() => { setSearch(''); setCurrentPage(1); }}
                />
              )}
              {facets.resourceType.map((v) => (
                <FilterBadge key={`rt-${v}`} label={v} value={v} onRemove={(val) => removeFilter('resourceType', val)} />
              ))}
              {facets.licenseType.map((v) => (
                <FilterBadge key={`lt-${v}`} label={v} value={v} onRemove={(val) => removeFilter('licenseType', val)} />
              ))}
              {facets.status.map((v) => (
                <FilterBadge key={`st-${v}`} label={v} value={v} onRemove={(val) => removeFilter('status', val)} />
              ))}
              {facets.contentType.map((v) => (
                <FilterBadge key={`ct-${v}`} label={v} value={v} onRemove={(val) => removeFilter('contentType', val)} />
              ))}
            </div>
          )}

          <div id="dsFacets">
            <FacetPanel
              title={t('datasets.facet.resourceType', 'Resource Type')}
              counts={resourceTypeCounts}
              selected={facets.resourceType}
              onToggle={(v) => toggleFacet('resourceType', v)}
            />
            <FacetPanel
              title={t('datasets.facet.licenseType', 'License Type')}
              counts={licenseTypeCounts}
              selected={facets.licenseType}
              onToggle={(v) => toggleFacet('licenseType', v)}
            />
            <FacetPanel
              title={t('datasets.facet.status', 'Status')}
              counts={statusCounts}
              selected={facets.status}
              onToggle={(v) => toggleFacet('status', v)}
            />
            <FacetPanel
              title={t('datasets.facet.contentType', 'Content Type')}
              counts={contentTypeCounts}
              selected={facets.contentType}
              onToggle={(v) => toggleFacet('contentType', v)}
            />
          </div>
        </div>

        {/* Main area — matches dataSets.gsp #data-set-list */}
        <div id="data-set-list" className="col-md-9">
          {/* Search/controls bar — matches dataSets.gsp form structure */}
          <div className="card card-body mb-3">
            <div>
              <span id="resultsReturned">
                {isLoading
                  ? t('common.loading', 'Loading...')
                  : <>{t('datasets.showing', 'Showing')} <strong>{sorted.length}</strong> {t('datasets.datasets', 'datasets')}.</>}
              </span>
            </div>
            <form className="d-flex align-items-center gap-2 mt-2" onSubmit={(e) => e.preventDefault()}>
              <div className="input-group flex-grow-1">
                <input
                  type="text"
                  name="dr-search"
                  id="dr-search"
                  className="form-control"
                  placeholder={t('datasets.searchPlaceholder', 'Search datasets...')}
                  value={search}
                  onChange={(e) => { setSearch(e.target.value); setCurrentPage(1); }}
                />
                <button
                  type="submit"
                  id="dr-search-link"
                  className="btn btn-outline-dark"
                  title={t('datasets.search.btn.title', 'Search')}
                >
                  {t('datasets.search', 'Search')}
                </button>
              </div>
              <button
                type="button"
                id="resetLink"
                className="btn btn-outline-dark text-nowrap"
                title={t('datasets.remove.all.filters', 'Remove all filters')}
                onClick={clearAllFilters}
              >
                {t('datasets.resetList', 'Reset list')}
              </button>
              <button
                type="button"
                id="downloadLink"
                className="btn btn-outline-dark text-nowrap"
                title={t('datasets.downloadCsv', 'Download metadata for datasets as a CSV file')}
                onClick={() => downloadCsv(sorted)}
              >
                <span className="fa fa-download" />{' '}
                {t('datasets.download', 'Download')}
              </button>
            </form>
          </div>

          {/* Sort controls */}
          <div id="sortWidgets" className="d-flex align-items-center gap-2 mb-2">
            <label className="form-label mb-0 me-1" htmlFor="pageSizeSelect">
              {t('datasets.show', 'Show')}:
            </label>
            <select
              id="pageSizeSelect"
              className="form-select form-select-sm"
              style={{ width: 'auto' }}
              value={pageSize}
              onChange={(e) => { setPageSize(Number(e.target.value)); setCurrentPage(1); }}
            >
              {PAGE_SIZES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          {isLoading && (
            <div className="d-flex justify-content-center p-5">
              <div className="spinner-border" role="status">
                <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
              </div>
            </div>
          )}

          {!isLoading && (
            <div id="results">
              {paginated.length === 0 && (
                <p className="text-muted text-center">
                  {t('datasets.noResults', 'No datasets match your criteria.')}
                </p>
              )}

              {paginated.map((ds) => {
                const isRecordOrEvent = ds.resourceType === 'records' || ds.resourceType === 'events';
                const recordCount = biocacheCounts.get(ds.uid);
                const biocacheSearchUrl = `${biocacheUiUrl}/occurrences/search?q=data_resource_uid:${ds.uid}`;

                return (
                <div key={ds.uid}>
                  {/* Row A: name + type */}
                  <div className="rowA" role="button" onClick={() => setExpandedRow(expandedRow === ds.uid ? null : ds.uid)}>
                    <Link to={`/public/showDataResource/${ds.uid}`} onClick={(e) => e.stopPropagation()}>{ds.name}</Link>
                    {ds.acronym && <span className="text-muted ms-1">({ds.acronym})</span>}
                    {ds.resourceType && (
                      <span className="resultsLabel text-muted">[{ds.resourceType}]</span>
                    )}
                  </div>

                  {/* Row B: extra info line */}
                  <div className="rowB d-flex flex-wrap gap-3 small text-muted">
                    {showExtraInfo && isRecordOrEvent && (
                      <>
                        <span className="lastUpdatedDrView">
                          <strong>{t('datasets.lastUpdated', 'Last updated')}: </strong>
                          {ds.lastUpdated
                            ? relativeTime
                              ? new Intl.RelativeTimeFormat(navigator.language, { numeric: 'auto' }).format(
                                  Math.round((new Date(ds.lastUpdated).getTime() - Date.now()) / (1000 * 60 * 60 * 24)),
                                  'day'
                                )
                              : new Date(ds.lastUpdated).toLocaleDateString(navigator.language, { year: 'numeric', month: 'long', day: 'numeric' })
                            : t('common.unknown', 'Unknown')}
                        </span>
                        {recordCount !== undefined && recordCount >= 0 && (
                          <span className="drNumRecordsDrView">
                            <strong>{t('datasets.numRecords', 'Records')}: </strong>
                            <a href={biocacheSearchUrl} title={t('datasets.viewRecords', 'View records in biocache')}>
                              {recordCount.toLocaleString()}
                            </a>
                          </span>
                        )}
                      </>
                    )}
                    {!showExtraInfo && isRecordOrEvent && biocacheUiUrl && (
                      <span className="viewRecords">
                        <a href={biocacheSearchUrl} title={t('datasets.viewRecords', 'View records in biocache')}>
                          {t('datasets.viewRecordsLink', 'View records')}
                        </a>
                      </span>
                    )}
                    {ds.resourceType === 'website' && ds.websiteUrl && (
                      <span className="viewWebsite">
                        <a href={ds.websiteUrl} target="_blank" rel="noopener noreferrer" title={t('datasets.visitWebsite', 'Visit website')}>
                          {t('datasets.visitWebsiteLink', 'Visit website')}
                        </a>
                      </span>
                    )}
                  </div>

                  {/* Row C: expanded description */}
                  {expandedRow === ds.uid && (
                    <div className="rowC">
                      <p>
                        {ds.pubDescription || t('datasets.noDescription', 'No description available.')}
                      </p>
                      {ds.provider && (
                        <small className="text-muted">
                          {t('datasets.provider', 'Provider')}: {ds.provider}
                        </small>
                      )}
                    </div>
                  )}
                </div>
                );
              })}

              <div id="searchNavBar" className="mt-3">
                <Pagination
                  currentPage={safePage}
                  totalPages={totalPages}
                  onPageChange={setCurrentPage}
                />
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
    </>
  );
}
