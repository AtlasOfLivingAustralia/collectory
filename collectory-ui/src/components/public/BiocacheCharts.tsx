import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import {
  PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
  BarChart as RechartsBarChart, Bar, XAxis, YAxis, CartesianGrid,
} from 'recharts';

interface BiocacheChartsProps {
  entityUid: string;
  facetField: string; // e.g. 'collection_uid', 'collectionUid', 'institution_uid'
  biocacheServicesUrl?: string;
  biocacheUiUrl?: string;
}

interface FacetFieldResult {
  label: string;
  count: number;
  fq: string;
}

interface FacetResult {
  fieldName: string;
  fieldResult: FacetFieldResult[];
}

interface BiocacheSearchResponse {
  totalRecords: number;
  facetResults: FacetResult[];
}

interface ChartEntry {
  name: string;
  value: number;
  percent: number;
}

// ALA charts colour palette (matches ala-charts-plugin defaults)
const ALA_COLOURS = [
  '#6da7de', '#9bd4a0', '#f6b48d', '#b7b7b7', '#cda2cb',
  '#ee9090', '#f3d36b', '#70ccab', '#d5a5d5', '#a0c9e8',
  '#c2dda1', '#f9cb9c', '#d4d4d4', '#e0b6de', '#f5a8a8',
  '#f7e09c', '#94dfc1', '#e1bee7', '#b0d9f0', '#d4e8b2',
];

// Facets defined in charts.json (state, type_status, month, decade, assertions, family)
const CHART_CONFIG: { field: string; title: string; type: 'doughnut' | 'bar' | 'horizontal-bar' }[] = [
  { field: 'state',        title: 'By State',        type: 'doughnut' },
  { field: 'type_status',  title: 'Type Status',     type: 'doughnut' },
  { field: 'month',        title: 'By month',        type: 'bar' },
  { field: 'decade',       title: 'By decade',       type: 'bar' },
  { field: 'assertions',   title: 'By data quality', type: 'horizontal-bar' },
  { field: 'family',       title: 'By family',       type: 'doughnut' },
];

function buildChartEntries(
  facetResults: FacetResult[],
  fieldName: string,
  totalRecords: number,
): ChartEntry[] {
  const facet = facetResults.find((f) => f.fieldName === fieldName);
  if (!facet) return [];
  return facet.fieldResult
    .filter((fr) => fr.count > 0)
    .map((fr) => ({
      name: fr.label || 'Unknown',
      value: fr.count,
      percent: totalRecords > 0 ? Math.round((fr.count / totalRecords) * 100 * 10) / 10 : 0,
    }));
}

/** Doughnut/Pie chart */
function DoughnutChart({ entries, title }: { entries: ChartEntry[]; title: string }) {
  if (entries.length === 0) return null;
  return (
    <div className="mb-4">
      <h3 className="mb-2">{title}</h3>
      <ResponsiveContainer width="100%" height={280}>
        <PieChart>
          <Pie
            data={entries}
            cx="50%"
            cy="50%"
            innerRadius={50}
            outerRadius={90}
            dataKey="value"
            nameKey="name"
            paddingAngle={1}
          >
            {entries.map((_, i) => (
              <Cell key={i} fill={ALA_COLOURS[i % ALA_COLOURS.length]} />
            ))}
          </Pie>
          <Tooltip
            formatter={(value: number) => value.toLocaleString()}
          />
          <Legend
            layout="vertical"
            align="right"
            verticalAlign="middle"
            wrapperStyle={{ fontSize: 12, maxHeight: 260, overflow: 'auto' }}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Bar chart (vertical) for month/decade */
function VerticalBarChart({ entries, title }: { entries: ChartEntry[]; title: string }) {
  if (entries.length === 0) return null;
  return (
    <div className="mb-4">
      <h3 className="mb-2">{title}</h3>
      <ResponsiveContainer width="100%" height={280}>
        <RechartsBarChart data={entries} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="name"
            tick={{ fontSize: 11 }}
            interval={0}
            angle={-30}
            textAnchor="end"
            height={60}
          />
          <YAxis tick={{ fontSize: 11 }} />
          <Tooltip formatter={(value: number) => value.toLocaleString()} />
          <Bar dataKey="value" fill="#6da7de" />
        </RechartsBarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Horizontal bar chart for assertions/data quality */
function HorizontalBarChart({ entries, title }: { entries: ChartEntry[]; title: string }) {
  if (entries.length === 0) return null;
  return (
    <div className="mb-4">
      <h3 className="mb-2">{title}</h3>
      <ResponsiveContainer width="100%" height={Math.max(200, entries.length * 28 + 40)}>
        <RechartsBarChart
          data={entries}
          layout="vertical"
          margin={{ top: 5, right: 10, left: 10, bottom: 5 }}
        >
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis type="number" tick={{ fontSize: 11 }} />
          <YAxis
            type="category"
            dataKey="name"
            tick={{ fontSize: 11 }}
            width={150}
          />
          <Tooltip formatter={(value: number) => value.toLocaleString()} />
          <Bar dataKey="value" fill="#f6b48d" />
        </RechartsBarChart>
      </ResponsiveContainer>
    </div>
  );
}

export default function BiocacheCharts({
  entityUid,
  facetField,
  biocacheUiUrl,
}: BiocacheChartsProps) {
  const { t } = useTranslation();

  // Fetch all 6 facets in a single request — use backend proxy to avoid CORS
  const facetNames = CHART_CONFIG.map((c) => c.field).join(',');

  const { data, isLoading } = useQuery({
    queryKey: ['biocacheCharts', entityUid, facetField, facetNames],
    queryFn: async () => {
      const { data: res } = await apiClient.get<BiocacheSearchResponse>(
        `/proxy/biocache/occurrences/search`,
        {
          params: {
            q: `${facetField}:${entityUid}`,
            facets: facetNames,
            pageSize: 0,
            flimit: 15,
          },
        },
      );
      return res;
    },
    enabled: !!entityUid,
    staleTime: 60_000,
  });

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center my-4">
        <div className="spinner-border spinner-border-sm" role="status">
          <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
        </div>
      </div>
    );
  }

  if (!data || data.totalRecords === 0 || !data.facetResults) return null;

  const searchUrl = `${biocacheUiUrl ?? ''}/occurrences/search?q=${facetField}:${entityUid}`;

  // Build chart elements — skip empty facets
  const charts = CHART_CONFIG.map((cfg) => {
    const entries = buildChartEntries(data.facetResults, cfg.field, data.totalRecords);
    if (entries.length === 0) return null;
    if (cfg.type === 'doughnut') {
      return <DoughnutChart key={cfg.field} entries={entries} title={cfg.title} />;
    }
    if (cfg.type === 'horizontal-bar') {
      return <HorizontalBarChart key={cfg.field} entries={entries} title={cfg.title} />;
    }
    return <VerticalBarChart key={cfg.field} entries={entries} title={cfg.title} />;
  }).filter(Boolean);

  if (charts.length === 0) return null;

  return (
    <div className="mt-3">
      <div className="row">
        {charts.map((chart, i) => (
          <div key={i} className="col-md-6">
            {chart}
          </div>
        ))}
      </div>
      <a href={searchUrl} className="btn btn-outline-dark btn-sm" target="_blank" rel="noopener noreferrer">
        <i className="fa fa-list me-1" />
        {t('records.viewAll', 'View all records')}
      </a>
    </div>
  );
}
