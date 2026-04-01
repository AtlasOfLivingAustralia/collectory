import { Link, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { reportsApi, type DataReport } from '../api/endpoints/reports';
import { ProtectedRoute } from '../auth/ProtectedRoute';

// Report page components
import DataReportPage from './reports/DataReport';
import ActivityReport from './reports/ActivityReport';
import MembershipReport from './reports/MembershipReport';
import ChangesReport from './reports/ChangesReport';
import CollectionsReport from './reports/CollectionsReport';
import InstitutionsReport from './reports/InstitutionsReport';
import ProvidersReport from './reports/ProvidersReport';
import ResourcesReport from './reports/ResourcesReport';
import ContactsReport from './reports/ContactsReport';
import CodesReport from './reports/CodesReport';
import DuplicateContactsReport from './reports/DuplicateContactsReport';
import NotificationsReport from './reports/NotificationsReport';
import ClassificationReport from './reports/ClassificationReport';
import TaxonomicHintsReport from './reports/TaxonomicHintsReport';
import CollectionTypesReport from './reports/CollectionTypesReport';
import HarvestersReport from './reports/HarvestersReport';
import RightsReport from './reports/RightsReport';
import AttributionsReport from './reports/AttributionsReport';
import MissingRecordsReport from './reports/MissingRecordsReport';
import CollectionSpecimenDataReport from './reports/CollectionSpecimenDataReport';
import ProviderRecordsDataReport from './reports/ProviderRecordsDataReport';
import DataLinksReport from './reports/DataLinksReport';
import ContactsForCouncilMembersReport from './reports/ContactsForCouncilMembersReport';
import ContactsForCollectionsReport from './reports/ContactsForCollectionsReport';
import ContactsForInstitutionsReport from './reports/ContactsForInstitutionsReport';
import MapReport from './reports/MapReport';

/* ------------------------------------------------------------------ */
/*  Report categories (for the index page)                             */
/* ------------------------------------------------------------------ */

const REPORT_CATEGORIES = [
  {
    title: 'Data Summary',
    icon: 'fa fa-bar-chart',
    links: [
      { label: 'Data Completeness', path: '/reports/data' },
      { label: 'Activity', path: '/reports/activity' },
      { label: 'Change History', path: '/reports/changes' },
      { label: 'Network Membership', path: '/reports/membership' },
      { label: 'Notifications', path: '/reports/notifications' },
    ],
  },
  {
    title: 'Collections',
    icon: 'fa fa-archive',
    links: [
      { label: 'Collections', path: '/reports/collections' },
      { label: 'Provider Codes', path: '/reports/codes' },
      { label: 'Attributions', path: '/reports/attributions' },
      { label: 'Missing Records', path: '/reports/missingRecords' },
      { label: 'Classification', path: '/reports/classification' },
      { label: 'Collection Types', path: '/reports/collectionTypes' },
      { label: 'Taxonomic Hints', path: '/reports/taxonomicHints' },
      { label: 'Collection Specimen Data', path: '/reports/collectionSpecimenData' },
      { label: 'Collection Locations Map', path: '/reports/map' },
    ],
  },
  {
    title: 'Institutions',
    icon: 'fa fa-university',
    links: [
      { label: 'Institutions', path: '/reports/institutions' },
    ],
  },
  {
    title: 'Data Resources',
    icon: 'fa fa-database',
    links: [
      { label: 'Data Providers', path: '/reports/providers' },
      { label: 'Provider Records Data', path: '/reports/providerRecordsData' },
      { label: 'Rights & Licensing', path: '/reports/rights' },
      { label: 'Harvester Config', path: '/reports/harvesters' },
      { label: 'Data Resources', path: '/reports/resources' },
      { label: 'Data Links', path: '/reports/dataLinks' },
    ],
  },
  {
    title: 'Contacts',
    icon: 'fa fa-users',
    links: [
      { label: 'All Contacts', path: '/reports/contacts' },
      { label: 'Duplicate Contacts', path: '/reports/duplicateContacts' },
      { label: 'Contacts for Council Members', path: '/reports/contactsForCouncilMembers' },
      { label: 'Contacts for Collections', path: '/reports/contactsForCollections' },
      { label: 'Contacts for Institutions', path: '/reports/contactsForInstitutions' },
    ],
  },
];

/* ------------------------------------------------------------------ */
/*  Sub-path → component mapping                                       */
/* ------------------------------------------------------------------ */

const REPORT_ROUTES: Record<string, React.ComponentType> = {
  data: DataReportPage,
  activity: ActivityReport,
  membership: MembershipReport,
  changes: ChangesReport,
  collections: CollectionsReport,
  institutions: InstitutionsReport,
  providers: ProvidersReport,
  resources: ResourcesReport,
  contacts: ContactsReport,
  codes: CodesReport,
  duplicateContacts: DuplicateContactsReport,
  notifications: NotificationsReport,
  classification: ClassificationReport,
  taxonomicHints: TaxonomicHintsReport,
  collectionTypes: CollectionTypesReport,
  harvesters: HarvestersReport,
  rights: RightsReport,
  attributions: AttributionsReport,
  missingRecords: MissingRecordsReport,
  collectionSpecimenData: CollectionSpecimenDataReport,
  providerRecordsData: ProviderRecordsDataReport,
  dataLinks: DataLinksReport,
  contactsForCouncilMembers: ContactsForCouncilMembersReport,
  contactsForCollections: ContactsForCollectionsReport,
  contactsForInstitutions: ContactsForInstitutionsReport,
  map: MapReport,
};

/* ------------------------------------------------------------------ */
/*  ReportIndex — dashboard landing page                               */
/* ------------------------------------------------------------------ */

function ReportIndex() {
  const { data: counts, isLoading, error } = useQuery<DataReport>({
    queryKey: ['reports', 'data'],
    queryFn: reportsApi.data,
  });

  const statCards = [
    { label: 'Collections', value: counts?.totalCollections, icon: 'fa fa-archive' },
    { label: 'Institutions', value: counts?.totalInstitutions, icon: 'fa fa-university' },
    { label: 'Data Providers', value: counts?.totalDataProviders, icon: 'fa fa-cloud' },
    { label: 'Data Resources', value: counts?.totalDataResources, icon: 'fa fa-database' },
    { label: 'Contacts', value: counts?.totalContacts, icon: 'fa fa-address-book' },
  ];

  return (
    <div className="container-fluid mt-3">
      <div className="btn-toolbar mb-3">
        <ul className="btn-group list-unstyled mb-0">
          <li className="btn btn-outline-dark">
            <Link to="/" className="text-decoration-none text-reset">
              <i className="fa fa-home" />
            </Link>
          </li>
          <li className="btn btn-outline-dark">
            <i className="fa fa-th-list me-1" />
            Reports
          </li>
        </ul>
      </div>

      <h1>Reports</h1>
      <p>Select a report to view.</p>

      {/* Quick Stats */}
      <div className="card card-body float-end ms-3 mb-3" style={{ maxWidth: 280 }}>
        <h5>Totals</h5>
        <div className="lead">
          {statCards.map(stat => (
            <div key={stat.label}>
              <span className="fw-bold">
                {isLoading ? '...' : error ? '--' : (stat.value ?? 0).toLocaleString()}
              </span>{' '}
              {stat.label}
            </div>
          ))}
        </div>
      </div>

      {/* Report Category Sections */}
      {REPORT_CATEGORIES.map(cat => (
        <div key={cat.title} className="mb-4" style={{ clear: 'none' }}>
          <h2><i className={`${cat.icon} me-2`} />{cat.title}</h2>
          <div>
            {cat.links.map(link => (
              <p key={link.path} className="mb-1">
                <Link to={link.path} className="fw-bold">{link.label}</Link>
              </p>
            ))}
          </div>
        </div>
      ))}
      <div style={{ clear: 'both' }} />
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Main export — routes between dashboard index and sub-reports       */
/* ------------------------------------------------------------------ */

export default function ReportsDashboard() {
  const location = useLocation();
  const subPath = location.pathname.replace(/^\/reports\/?/, '');

  const ReportComponent = subPath ? REPORT_ROUTES[subPath] : null;

  return (
    <ProtectedRoute>
      {!subPath ? (
        <ReportIndex />
      ) : ReportComponent ? (
        <ReportComponent />
      ) : (
        <div className="container mt-4">
          <Link to="/reports" className="btn btn-outline-secondary btn-sm mb-3">
            <i className="fa fa-arrow-left me-1" /> Back to Reports
          </Link>
          <div className="alert alert-warning">
            <i className="fa fa-exclamation-triangle me-2" />
            Report not found: {subPath}
          </div>
        </div>
      )}
    </ProtectedRoute>
  );
}
