import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

/**
 * TaxonTree — mirrors the legacy `_taxonTree.gsp` / `initTaxonTree()` behaviour.
 *
 * The GSP called initTaxonTree() from the ala-charts-plugin which used:
 *   GET {biocacheServicesUrl}/explore/group/ALL?q={facet}:{uid}&pageSize=50&fq=rank:phylum
 *   GET {biocacheServicesUrl}/explore/group/{group}?q={facet}:{uid}&pageSize=50
 *
 * We replicate this with the biocache /explore/group endpoint, expanding one level at a time.
 * Each node shows: name (linked to BIE) + record count (linked to biocache occurrences).
 */

interface TaxonTreeProps {
  entityUid: string;
  facetField: string; // e.g. 'collection_uid'
  biocacheServicesUrl?: string;
  biocacheUiUrl?: string;
  bieBaseUrl?: string;
  /** resourceType — only show tree when this is 'records' or undefined */
  resourceType?: string;
}

interface TaxonGroup {
  name: string;
  common?: string;
  count: number;
  speciesCount?: number;
}

async function fetchTaxonGroups(
  facetField: string,
  entityUid: string,
  group: string,
): Promise<TaxonGroup[]> {
  // Use backend proxy to avoid CORS
  const { data } = await apiClient.get<TaxonGroup[]>(
    `/proxy/biocache/explore/group/${encodeURIComponent(group)}`,
    {
      params: {
        q: `${facetField}:${entityUid}`,
        pageSize: 50,
      },
    },
  );
  return Array.isArray(data) ? data : [];
}

/** A single expandable node in the taxon tree */
function TaxonNode({
  group,
  facetField,
  entityUid,
  biocacheUiUrl,
  bieBaseUrl,
  groupName,
}: {
  group: TaxonGroup;
  facetField: string;
  entityUid: string;
  biocacheUiUrl?: string;
  bieBaseUrl?: string;
  groupName: string; // parent group name used to identify this level
}) {
  const [expanded, setExpanded] = useState(false);

  const { data: children, isLoading } = useQuery({
    queryKey: ['taxonTree', group.name, facetField, entityUid],
    queryFn: () => fetchTaxonGroups(facetField, entityUid, group.name),
    enabled: expanded,
    staleTime: 5 * 60_000,
  });

  const recordsUrl = biocacheUiUrl
    ? `${biocacheUiUrl}/occurrences/search?q=${facetField}:${entityUid}&fq=species_group:${encodeURIComponent(group.name)}`
    : undefined;

  const bieUrl = bieBaseUrl
    ? `${bieBaseUrl}/species/${encodeURIComponent(group.name)}`
    : undefined;

  const hasChildren = (group.speciesCount ?? 0) > 0 || (!expanded && groupName === 'ALL');

  return (
    <li className="taxon-tree-node">
      <div className="d-flex align-items-center gap-2">
        {hasChildren ? (
          <button
            className="btn btn-link btn-sm p-0 text-decoration-none"
            style={{ minWidth: 16 }}
            onClick={() => setExpanded((v) => !v)}
            aria-expanded={expanded}
            title={expanded ? 'Collapse' : 'Expand'}
          >
            <i className={`fa fa-${expanded ? 'minus' : 'plus'}-square-o`} aria-hidden="true" />
          </button>
        ) : (
          <span style={{ minWidth: 16, display: 'inline-block' }} />
        )}

        {bieUrl ? (
          <a href={bieUrl} target="_blank" rel="noopener noreferrer">
            {group.name}
            {group.common ? ` (${group.common})` : ''}
          </a>
        ) : (
          <span>
            {group.name}
            {group.common ? ` (${group.common})` : ''}
          </span>
        )}

        {recordsUrl ? (
          <a href={recordsUrl} target="_blank" rel="noopener noreferrer" className="text-muted small ms-1">
            {group.count.toLocaleString()} records
          </a>
        ) : (
          <span className="text-muted small ms-1">{group.count.toLocaleString()} records</span>
        )}
      </div>

      {expanded && (
        <div className="ms-3 mt-1">
          {isLoading ? (
            <div className="spinner-border spinner-border-sm" role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
          ) : children && children.length > 0 ? (
            <ul className="list-unstyled mb-0">
              {children.map((child) => (
                <TaxonNode
                  key={child.name}
                  group={child}
                  facetField={facetField}
                  entityUid={entityUid}
                  biocacheUiUrl={biocacheUiUrl}
                  bieBaseUrl={bieBaseUrl}
                  groupName={group.name}
                />
              ))}
            </ul>
          ) : (
            <p className="text-muted small mb-0">No sub-groups found.</p>
          )}
        </div>
      )}
    </li>
  );
}

export default function TaxonTree({
  entityUid,
  facetField,
  biocacheUiUrl,
  bieBaseUrl,
  resourceType,
}: TaxonTreeProps) {
  const { t } = useTranslation();

  // Only show for 'records' resource type (mirrors GSP condition)
  if (resourceType && resourceType !== 'records') return null;

  return (
    <TaxonTreeInner
      entityUid={entityUid}
      facetField={facetField}
      biocacheUiUrl={biocacheUiUrl}
      bieBaseUrl={bieBaseUrl}
      t={t}
    />
  );
}

function TaxonTreeInner({
  entityUid,
  facetField,
  biocacheUiUrl,
  bieBaseUrl,
  t,
}: Pick<TaxonTreeProps, 'entityUid' | 'facetField'> & {
  biocacheUiUrl?: string;
  bieBaseUrl?: string;
  t: ReturnType<typeof useTranslation>['t'];
}) {
  const { data: groups, isLoading, isError } = useQuery({
    queryKey: ['taxonTree', 'ALL', facetField, entityUid],
    queryFn: () => fetchTaxonGroups(facetField, entityUid, 'ALL'),
    staleTime: 5 * 60_000,
  });

  if (isLoading) {
    return (
      <div className="d-flex align-items-center gap-2 my-2">
        <div className="spinner-border spinner-border-sm" role="status">
          <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
        </div>
        <span className="text-muted small">{t('taxonTree.loading', 'Loading taxonomic breakdown...')}</span>
      </div>
    );
  }

  if (isError || !groups || groups.length === 0) return null;

  return (
    <div id="treeContainer" className="mt-3">
      <div id="taxonTreeHead">
        <p>
          <strong>{t('taxonTree.title', 'Taxonomic breakdown')}</strong>
          {biocacheUiUrl && (
            <a
              href={`${biocacheUiUrl}/occurrences/search?q=${facetField}:${entityUid}`}
              className="ms-2 small"
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('taxonTree.viewAll', 'View all in biocache')}
            </a>
          )}
        </p>
      </div>
      <div id="taxaTree">
        <ul className="list-unstyled mb-0" id="tree">
          {groups.map((group) => (
            <TaxonNode
              key={group.name}
              group={group}
              facetField={facetField}
              entityUid={entityUid}
              biocacheUiUrl={biocacheUiUrl}
              bieBaseUrl={bieBaseUrl}
              groupName="ALL"
            />
          ))}
        </ul>
      </div>
    </div>
  );
}
