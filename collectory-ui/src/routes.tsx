import { createBrowserRouter, type RouteObject, Navigate, useLocation } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import { AlaLayout } from './layouts/AlaLayout';

// Lazy-loaded page components
const CollectionMap = lazy(() => import('./pages/CollectionMap'));
const DatasetList = lazy(() => import('./pages/DatasetList'));
const ShowCollection = lazy(() => import('./pages/ShowCollection'));
const ShowInstitution = lazy(() => import('./pages/ShowInstitution'));
const ShowDataResource = lazy(() => import('./pages/ShowDataResource'));
const ShowDataProvider = lazy(() => import('./pages/ShowDataProvider'));
const ShowDataHub = lazy(() => import('./pages/ShowDataHub'));
const ShowTempDataResource = lazy(() => import('./pages/ShowTempDataResource'));
const InstitutionDirectory = lazy(() => import('./pages/InstitutionDirectory'));
const EntityShowRouter = lazy(() => import('./pages/EntityShowRouter'));
const NotFound = lazy(() => import('./pages/NotFound'));

// Admin/Manage pages (lazy)
const ManageDashboard = lazy(() => import('./pages/ManageDashboard'));
const ManageList = lazy(() => import('./pages/ManageList'));
const ManageShow = lazy(() => import('./pages/ManageShow'));
const ReportsDashboard = lazy(() => import('./pages/ReportsDashboard'));

// Admin entity pages (lazy)
const EntityList = lazy(() => import('./pages/admin/EntityList'));
const EntityAdminShow = lazy(() => import('./pages/admin/EntityAdminShow'));
const ContactList = lazy(() => import('./pages/admin/ContactList'));
const ContactShow = lazy(() => import('./pages/admin/ContactShow'));
// Placeholder removed — all routes now point to actual components

// Phase 6 edit forms (lazy)
const EntityEditForm = lazy(() => import('./pages/admin/EntityEditForm'));
const DescriptionForm = lazy(() => import('./pages/admin/DescriptionForm'));
const LocationForm = lazy(() => import('./pages/admin/LocationForm'));
const ImagesEditor = lazy(() => import('./pages/admin/ImagesEditor'));
const ContactRoleEditor = lazy(() => import('./pages/admin/ContactRoleEditor'));
const ContactForm = lazy(() => import('./pages/admin/ContactForm'));

// Phase 6 sub-edit pages (lazy)
const TaxonomyHintsEditor = lazy(() => import('./pages/admin/TaxonomyHintsEditor'));
const ExternalIdEditor = lazy(() => import('./pages/admin/ExternalIdEditor'));
const AttributionsEditor = lazy(() => import('./pages/admin/AttributionsEditor'));
const ContributionForm = lazy(() => import('./pages/admin/ContributionForm'));
const RightsForm = lazy(() => import('./pages/admin/RightsForm'));
const FileUpload = lazy(() => import('./pages/admin/FileUpload'));
const GbifUploadForm = lazy(() => import('./pages/admin/GbifUploadForm'));
const ConsumersEditor = lazy(() => import('./pages/admin/ConsumersEditor'));
const MembersEditor = lazy(() => import('./pages/admin/MembersEditor'));
const ProvidersEditor = lazy(() => import('./pages/admin/ProvidersEditor'));
const ImageMetadataForm = lazy(() => import('./pages/admin/ImageMetadataForm'));

// Phase 6 audit/minor pages (lazy)
const TaxonomicRangeEditor = lazy(() => import('./pages/admin/TaxonomicRangeEditor'));
const GbifEditForm = lazy(() => import('./pages/admin/GbifEditForm'));
const ChangeHistoryPage = lazy(() => import('./pages/admin/ChangeHistoryPage'));

// Scaffold CRUD pages (lazy)
const LicenceList = lazy(() => import('./pages/admin/LicenceList'));
const LicenceShow = lazy(() => import('./pages/admin/LicenceShow'));
const LicenceForm = lazy(() => import('./pages/admin/LicenceForm'));
const ProviderCodeList = lazy(() => import('./pages/admin/ProviderCodeList'));
const ProviderCodeShow = lazy(() => import('./pages/admin/ProviderCodeShow'));
const ProviderCodeForm = lazy(() => import('./pages/admin/ProviderCodeForm'));
const ProviderMapList = lazy(() => import('./pages/admin/ProviderMapList'));
const ProviderMapShow = lazy(() => import('./pages/admin/ProviderMapShow'));
const ProviderMapForm = lazy(() => import('./pages/admin/ProviderMapForm'));
const AuditLogList = lazy(() => import('./pages/admin/AuditLogList'));
const AuditLogShow = lazy(() => import('./pages/admin/AuditLogShow'));

// GBIF / External data loading pages (lazy)
const GbifOrganizationImport = lazy(() => import('./pages/manage/GbifOrganizationImport'));
const ExternalLoadForm = lazy(() => import('./pages/manage/ExternalLoadForm'));
const ExternalLoadReview = lazy(() => import('./pages/manage/ExternalLoadReview'));
const ExternalLoadStatus = lazy(() => import('./pages/manage/ExternalLoadStatus'));
const RepatriateForm = lazy(() => import('./pages/manage/RepatriateForm'));
const RepatriateReview = lazy(() => import('./pages/manage/RepatriateReview'));
const GbifHealthCheck = lazy(() => import('./pages/manage/GbifHealthCheck'));
const GbifDatasetDownload = lazy(() => import('./pages/manage/GbifDatasetDownload'));
const GbifDatasetLoadStatus = lazy(() => import('./pages/manage/GbifDatasetLoadStatus'));
const GbifCountryLoadStatus = lazy(() => import('./pages/manage/GbifCountryLoadStatus'));
const GbifSyncAllResources = lazy(() => import('./pages/manage/GbifSyncAllResources'));
const GbifHealthCheckLinked = lazy(() => import('./pages/manage/GbifHealthCheckLinked'));
const ContactProfile = lazy(() => import('./pages/manage/ContactProfile'));
const MyCollections = lazy(() => import('./pages/manage/MyCollections'));
const DataCatalogue = lazy(() => import('./pages/DataCatalogue'));
const OidcCallback = lazy(() => import('./pages/OidcCallback'));

function Loading() {
  return (
    <div className="d-flex justify-content-center align-items-center" style={{ minHeight: '200px' }}>
      <div className="spinner-border" role="status">
        <span className="visually-hidden">Loading...</span>
      </div>
    </div>
  );
}

function withSuspense(Component: React.LazyExoticComponent<() => React.JSX.Element>) {
  return (
    <Suspense fallback={<Loading />}>
      <Component />
    </Suspense>
  );
}

/** Redirects /collection/searchList?term=xxx → /collection/list?term=xxx */
function CollectionSearchRedirect() {
  const location = useLocation();
  return <Navigate to={`/collection/list${location.search}`} replace />;
}

const routes: RouteObject[] = [
  // OIDC callback — outside AlaLayout so it renders before auth/header settle
  { path: 'callback', element: withSuspense(OidcCallback) },
  {
    element: <AlaLayout />,
    children: [
      // Public routes
      { index: true, element: withSuspense(CollectionMap) },
      { path: 'datasets', element: withSuspense(DatasetList) },
      { path: 'public/showCollection/:id', element: withSuspense(ShowCollection) },
      { path: 'public/showInstitution/:id', element: withSuspense(ShowInstitution) },
      { path: 'public/showDataResource/:id', element: withSuspense(ShowDataResource) },
      { path: 'public/showDataProvider/:id', element: withSuspense(ShowDataProvider) },
      { path: 'public/showDataHub/:id', element: withSuspense(ShowDataHub) },
      { path: 'public/showTempDataResource/:id', element: withSuspense(ShowTempDataResource) },
      { path: 'public/show/:id', element: withSuspense(EntityShowRouter) },
      { path: 'public/listInstitutions', element: withSuspense(InstitutionDirectory) },

      // Data catalogue (API documentation — Grails: /data/catalogue)
      { path: 'data/catalogue', element: withSuspense(DataCatalogue) },

      // Admin routes (Grails: /admin → manage/list)
      { path: 'admin', element: withSuspense(ManageList) },
      { path: 'admin/gbif/healthcheck', element: withSuspense(GbifHealthCheck) },
      { path: 'admin/gbif/sync', element: withSuspense(GbifSyncAllResources) },

      // Manage routes (auth required)
      { path: 'manage', element: withSuspense(ManageDashboard) },
      { path: 'manage/list', element: withSuspense(ManageList) },
      { path: 'manage/externalLoad', element: withSuspense(ExternalLoadForm) },
      { path: 'manage/externalLoad/review', element: withSuspense(ExternalLoadReview) },
      { path: 'manage/externalLoad/status', element: withSuspense(ExternalLoadStatus) },
      { path: 'manage/repatriate', element: withSuspense(RepatriateForm) },
      { path: 'manage/repatriate/review', element: withSuspense(RepatriateReview) },
      { path: 'manage/gbifHealthCheck', element: withSuspense(GbifHealthCheck) },
      { path: 'manage/gbifDatasetDownload/:id', element: withSuspense(GbifDatasetDownload) },
      { path: 'manage/gbifDatasetLoadStatus/:datasetKey', element: withSuspense(GbifDatasetLoadStatus) },
      { path: 'manage/gbifCountryLoadStatus/:country', element: withSuspense(GbifCountryLoadStatus) },
      { path: 'manage/show/:id', element: withSuspense(ManageShow) },

      // GBIF admin routes
      { path: 'gbif/healthCheck', element: withSuspense(GbifHealthCheck) },
      { path: 'gbif/healthCheckLinked', element: withSuspense(GbifHealthCheckLinked) },
      { path: 'gbif/syncAllResources', element: withSuspense(GbifSyncAllResources) },

      // Reports (admin)
      { path: 'reports/*', element: withSuspense(ReportsDashboard) },

      // My collections (Grails: /collection/myList)
      { path: 'collection/myList', element: withSuspense(MyCollections) },

      // Admin/Editor routes — Contact
      { path: 'contact/list', element: withSuspense(ContactList) },
      { path: 'contact/show/:id', element: withSuspense(ContactShow) },
      { path: 'contact/showProfile', element: withSuspense(ContactProfile) },
      { path: 'contact/showProfile/:id', element: withSuspense(ContactProfile) },
      { path: 'contact/create', element: withSuspense(ContactForm) },
      { path: 'contact/edit/:id', element: withSuspense(ContactForm) },

      // Admin/Editor routes — DataResource sub-pages
      { path: 'dataResource/:id/contribution', element: withSuspense(ContributionForm) },
      { path: 'dataResource/:id/rights', element: withSuspense(RightsForm) },
      { path: 'dataResource/:id/upload', element: withSuspense(FileUpload) },
      { path: 'dataResource/:id/gbifUpload', element: withSuspense(GbifUploadForm) },
      { path: 'dataResource/:id/consumers', element: withSuspense(ConsumersEditor) },
      { path: 'dataResource/:id/imageMetadata', element: withSuspense(ImageMetadataForm) },
      { path: 'dataResource/:id/gbifEdit', element: withSuspense(GbifEditForm) },

      // Admin/Editor routes — Licence
      { path: 'licence/list', element: withSuspense(LicenceList) },
      { path: 'licence/show/:id', element: withSuspense(LicenceShow) },
      { path: 'licence/create', element: withSuspense(LicenceForm) },
      { path: 'licence/edit/:id', element: withSuspense(LicenceForm) },

      // Admin/Editor routes — ProviderCode
      { path: 'providerCode/list', element: withSuspense(ProviderCodeList) },
      { path: 'providerCode/show/:id', element: withSuspense(ProviderCodeShow) },
      { path: 'providerCode/create', element: withSuspense(ProviderCodeForm) },
      { path: 'providerCode/edit/:id', element: withSuspense(ProviderCodeForm) },

      // Admin/Editor routes — ProviderMap
      { path: 'providerMap/list', element: withSuspense(ProviderMapList) },
      { path: 'providerMap/show/:id', element: withSuspense(ProviderMapShow) },
      { path: 'providerMap/create', element: withSuspense(ProviderMapForm) },
      { path: 'providerMap/edit/:id', element: withSuspense(ProviderMapForm) },

      // Admin routes — Audit Log (read-only)
      { path: 'auditLogEvent/list', element: withSuspense(AuditLogList) },
      { path: 'auditLogEvent/show/:id', element: withSuspense(AuditLogShow) },

      // collection/searchList — Grails search results page, redirect to list with term pre-filled
      { path: 'collection/searchList', element: <CollectionSearchRedirect /> },

      // GBIF organization import wizard (Grails: dataProvider/gbif/list)
      { path: 'dataProvider/gbif/list', element: withSuspense(GbifOrganizationImport) },

      // Admin/Editor routes — Generic entity pages (must come after specific routes)
      { path: ':entityType/list', element: withSuspense(EntityList) },
      { path: ':entityType/show/:id', element: withSuspense(EntityAdminShow) },
      { path: ':entityType/create', element: withSuspense(EntityEditForm) },
      { path: ':entityType/edit/:id', element: withSuspense(EntityEditForm) },
      { path: ':entityType/:id/description', element: withSuspense(DescriptionForm) },
      { path: ':entityType/:id/location', element: withSuspense(LocationForm) },
      { path: ':entityType/:id/images', element: withSuspense(ImagesEditor) },
      { path: ':entityType/:id/contacts', element: withSuspense(ContactRoleEditor) },
      { path: ':entityType/:id/taxonomyHints', element: withSuspense(TaxonomyHintsEditor) },
      { path: ':entityType/:id/externalIdentifiers', element: withSuspense(ExternalIdEditor) },
      { path: ':entityType/:id/attributions', element: withSuspense(AttributionsEditor) },
      { path: ':entityType/:id/taxonomicRange', element: withSuspense(TaxonomicRangeEditor) },
      { path: ':entityType/:id/changes', element: withSuspense(ChangeHistoryPage) },
      { path: ':entityType/:id/providers', element: withSuspense(ProvidersEditor) },
      { path: 'dataHub/:id/members', element: withSuspense(MembersEditor) },

      // Catch-all
      { path: '*', element: withSuspense(NotFound) },
    ],
  },
];

export const router = createBrowserRouter(routes);
