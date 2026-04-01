import apiClient from '../client';

// --- Types ---

export interface DataReport {
  totalCollections: number;
  totalInstitutions: number;
  totalDataProviders: number;
  totalDataResources: number;
  totalDataHubs: number;
  totalContacts: number;
  collectionsWithType: number;
  collectionsWithFocus: number;
  collectionsWithDescriptions: number;
  collectionsWithKeywords: number;
  collectionsWithProviderCodes: number;
  collectionsWithGeoDescription: number;
  collectionsWithNumRecords: number;
  collectionsWithNumRecordsDigitised: number;
  collectionsWithoutContacts: number;
  collectionsWithoutEmailContacts: number;
  institutionsWithoutContacts: number;
  institutionsWithoutEmailContacts: number;
}

export interface ActivityReport {
  curatorViews: number;
  curatorPreviews: number;
  curatorEdits: number;
  adminViews: number;
  adminPreviews: number;
  adminEdits: number;
  latestActivity: ActivityLogEntry[];
}

export interface ActivityLogEntry {
  id: number;
  action: string;
  admin: boolean;
  administratorForEntity: boolean;
  entityUid: string;
  timestamp: string;
  user: string;
}

export interface NetworkMember {
  uid: string;
  name: string;
  type: string;
}

export interface MembershipReport {
  partners: { uid: string; name: string }[];
  chahMembers: NetworkMember[];
  chaecMembers: NetworkMember[];
  chafcMembers: NetworkMember[];
  amrrnMembers: NetworkMember[];
  camdMembers: NetworkMember[];
}

export interface CollectionReportItem {
  uid: string;
  name: string;
  acronym?: string;
  collectionType?: string;
  focus?: string;
  keywords?: string;
  numRecords?: number;
  numRecordsDigitised?: number;
  institutionUid?: string;
  institutionName?: string;
}

export interface InstitutionReportItem {
  uid: string;
  name: string;
  acronym?: string;
  institutionType?: string;
}

export interface ProviderReportItem {
  uid: string;
  name: string;
}

export interface ResourceReportItem {
  uid: string;
  name: string;
  status?: string;
  resourceType?: string;
}

export interface ContactReportItem {
  id: number;
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  title?: string;
}

export interface HarvesterReportItem {
  uid: string;
  name: string;
  status?: string;
  harvestingFrequency?: string;
  lastChecked?: string;
  dataCurrency?: string;
  connectionParameters?: Record<string, unknown>;
}

export interface RightsReportItem {
  uid: string;
  name: string;
  rights?: string;
  licenseType?: string;
  licenseVersion?: string;
  permissionsDocument?: string;
  permissionsDocumentType?: string;
  filed?: boolean;
  riskAssessment?: boolean;
}

export interface AuditLogEvent {
  id: number;
  actor?: string;
  className?: string;
  dateCreated?: string;
  lastUpdated?: string;
  eventName?: string;
  newValue?: string;
  oldValue?: string;
  persistedObjectId?: string;
  propertyName?: string;
  uri?: string;
}

export interface ChangesReport {
  changes: AuditLogEvent[];
  offset: number;
  totalElements: number;
  who: string;
  what: string;
}

export interface DuplicateContactsReport {
  dupEmails: { email: string; contacts: ContactReportItem[] }[];
  dupNames: { firstName: string; lastName: string; contacts: ContactReportItem[] }[];
}

export interface CouncilContactItem {
  id: string;
  name: string;
  email?: string;
  contact?: string;
}

export interface CouncilMembersReport {
  chafc: CouncilContactItem[];
  chaec: CouncilContactItem[];
  chacm: CouncilContactItem[];
}

export interface EntityContactsReport {
  uid: string;
  name: string;
  contacts: { name: string; email?: string; role?: string; primaryContact: boolean }[];
}

export interface AttributionReportItem {
  entity: { uid: string; name: string };
}

export interface ClassificationReportItem {
  uid: string;
  name: string;
  keywords?: string;
}

export interface TaxonomicHintItem {
  uid: string;
  name: string;
  taxonomyHints?: string;
}

export interface CollectionTypeItem {
  uid: string;
  name: string;
  collectionType?: string;
}

export interface MissingRecordItem {
  collection: { uid: string; name: string };
  biocacheCount: number;
  claimed: number;
}

export interface SpecimenDataItem {
  uid: string;
  name: string;
  acronym?: string;
  numRecords?: number;
  numRecordsDigitised?: number;
  numBiocacheRecords?: number;
}

export interface DataLinkItem {
  consumer: string;
  provider: string;
}

export interface MapLocationItem {
  name: string;
  link: string;
  latitude: number;
  longitude: number;
  streetAddress: string;
}

export interface CodeSummaryItem {
  uid: string;
  name: string;
}

// --- API Functions ---

export const reportsApi = {
  data: () => apiClient.get<DataReport>('/reports/data').then(r => r.data),
  activity: () => apiClient.get<ActivityReport>('/reports/activity').then(r => r.data),
  membership: () => apiClient.get<MembershipReport>('/reports/membership').then(r => r.data),
  collections: (simple = 'false') =>
    apiClient.get<CollectionReportItem[]>('/reports/collections', { params: { simple } }).then(r => r.data),
  institutions: (simple = 'false') =>
    apiClient.get<InstitutionReportItem[]>('/reports/institutions', { params: { simple } }).then(r => r.data),
  providers: () => apiClient.get<ProviderReportItem[]>('/reports/providers').then(r => r.data),
  resources: () => apiClient.get<ResourceReportItem[]>('/reports/resources').then(r => r.data),
  contacts: () => apiClient.get<ContactReportItem[]>('/reports/contacts').then(r => r.data),
  codes: () => apiClient.get<CodeSummaryItem[]>('/reports/codes').then(r => r.data),
  duplicateContacts: () => apiClient.get<DuplicateContactsReport>('/reports/duplicateContacts').then(r => r.data),
  changes: (params: { offset?: number; who?: string; what?: string }) =>
    apiClient.get<ChangesReport>('/reports/changes', { params }).then(r => r.data),
  notifications: () =>
    apiClient.get<{ notices: ActivityLogEntry[] }>('/reports/notifications').then(r => r.data),
  classification: () =>
    apiClient.get<{ collections: ClassificationReportItem[] }>('/reports/classification').then(r => r.data),
  taxonomicHints: () => apiClient.get<TaxonomicHintItem[]>('/reports/taxonomicHints').then(r => r.data),
  collectionTypes: () => apiClient.get<CollectionTypeItem[]>('/reports/collectionTypes').then(r => r.data),
  harvesters: () => apiClient.get<HarvesterReportItem[]>('/reports/harvesters').then(r => r.data),
  rights: () => apiClient.get<RightsReportItem[]>('/reports/rights').then(r => r.data),
  attributions: () =>
    apiClient.get<{ collAttributions: AttributionReportItem[]; instAttributions: AttributionReportItem[] }>(
      '/reports/attributions'
    ).then(r => r.data),
  missingRecords: () =>
    apiClient.get<{ mrs: MissingRecordItem[] }>('/reports/missingRecords').then(r => r.data),
  collectionSpecimenData: () =>
    apiClient.get<{ statistics: SpecimenDataItem[] }>('/reports/collectionSpecimenData').then(r => r.data),
  providerRecordsData: () =>
    apiClient.get<{ statistics: SpecimenDataItem[] }>('/reports/providerRecordsData').then(r => r.data),
  dataLinks: () =>
    apiClient.get<{ links: DataLinkItem[] }>('/reports/dataLinks').then(r => r.data),
  contactsForCouncilMembers: () =>
    apiClient.get<CouncilMembersReport>('/reports/contactsForCouncilMembers').then(r => r.data),
  contactsForCollections: () =>
    apiClient.get<EntityContactsReport[]>('/reports/contactsForCollections').then(r => r.data),
  contactsForInstitutions: () =>
    apiClient.get<EntityContactsReport[]>('/reports/contactsForInstitutions').then(r => r.data),
  map: () =>
    apiClient.get<{ locations: MapLocationItem[] }>('/reports/map').then(r => r.data),
};
