/** TypeScript interfaces mirroring JPA entities */

export interface Address {
  street?: string;
  postBox?: string;
  city?: string;
  state?: string;
  postcode?: string;
  country?: string;
}

export interface ImageRef {
  file?: string;       // legacy field name (used in some places)
  filename?: string;   // actual field name returned by the API
  uri?: string;        // full absolute URL returned by the API
  caption?: string;
  attribution?: string;
  copyright?: string;
}

/** Base type for all provider group entities */
export interface NetworkMembershipEntry {
  acronym: string;
  name?: string;
  logo?: string;
}

export interface ProviderGroup {
  id: number;
  uid: string;
  guid?: string;
  name: string;
  acronym?: string;
  pubShortDescription?: string;
  pubDescription?: string;
  techDescription?: string;
  focus?: string;
  address?: Address;
  latitude?: number;
  longitude?: number;
  altitude?: string;
  state?: string;
  websiteUrl?: string;
  logoRef?: ImageRef;
  imageRef?: ImageRef;
  email?: string;
  phone?: string;
  isALAPartner: boolean;
  notes?: string;
  networkMembership?: NetworkMembershipEntry[] | string;
  attributions?: string;
  taxonomyHints?: string;
  keywords?: string;
  gbifRegistryKey?: string;
  dateCreated: string;
  lastUpdated: string;
  userLastModified: string;
}

export interface Institution extends ProviderGroup {
  institutionType?: string;
  childInstitutions?: string;
  gbifCountryToAttribute: string;
  collections?: CollectionSummary[];
  parentInstitutions?: Array<{ uid: string; name: string; uri?: string }>;
}

export interface Collection extends ProviderGroup {
  collectionType?: string;
  active?: string;
  numRecords: number;
  numRecordsDigitised: number;
  states?: string;
  geographicDescription?: string;
  eastCoordinate?: number;
  westCoordinate?: number;
  northCoordinate?: number;
  southCoordinate?: number;
  startDate?: string;
  endDate?: string;
  kingdomCoverage?: string;
  scientificNames?: string;
  subCollections?: string;
  institution?: InstitutionSummary;
  recordsProviderMapping?: {
    exact: boolean;
    warning?: string;
  };
}

export interface DataProvider extends ProviderGroup {
  hiddenJSON?: string;
  gbifCountryToAttribute: string;
  resources?: DataResourceSummary[];
}

export interface DataResource extends ProviderGroup {
  rights?: string;
  citation?: string;
  licenseType?: string;
  licenseVersion?: string;
  resourceType: string;
  provenance?: string;
  status: string;
  connectionParameters?: string;
  dataProvider?: DataProviderSummary;
  institution?: InstitutionSummary;
  gbifDataset?: boolean;
  isShareableWithGBIF?: boolean;
  gbifDoi?: string;
  publicArchiveAvailable?: boolean;
  geographicDescription?: string;
  purpose?: string;
  qualityControlDescription?: string;
  methodStepDescription?: string;
  contentTypes?: string;
  dataCollectionProtocolName?: string;
  dataCollectionProtocolDoc?: string;
  suitableFor?: string;
  suitableForOtherDetail?: string;
  dataGeneralizations?: string;
  informationWithheld?: string;
  downloadLimit?: number;
  lastChecked?: string;
  dataCurrency?: string;
  harvestFrequency?: number;
  mobilisationNotes?: string;
  harvestingNotes?: string;
  permissionsDocument?: string;
  permissionsDocumentType?: string;
  riskAssessment?: boolean;
  filed?: boolean;
  defaultDarwinCoreValues?: Record<string, string>;
  beginDate?: string;
  endDate?: string;
  makeContactPublic?: boolean;
  verified?: boolean;
  isPrivate?: boolean;
  repatriationCountry?: string;
}

export interface DataHub extends ProviderGroup {
  memberInstitutions?: string;
  memberCollections?: string;
  memberDataResources?: string;
  members?: string;
}

export interface TempDataResource {
  id: number;
  uid: string;
  name?: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  alaId?: string;
  dateCreated: string;
  lastUpdated: string;
  numberOfRecords: number;
  status?: string;
  description?: string;
  license?: string;
  citation?: string;
}

export interface Contact {
  id: number;
  title?: string;
  firstName?: string;
  lastName?: string;
  phone?: string;
  mobile?: string;
  email?: string;
  fax?: string;
  organizationName?: string;
  positionName?: string;
  userId?: string;
  notes?: string;
  publish: boolean;
}

export interface ContactFor {
  id: number;
  contact: Contact;
  entityUid: string;
  role?: string;
  administrator: boolean;
  primaryContact: boolean;
  notify: boolean;
}

export interface ExternalIdentifier {
  id: number;
  entityUid: string;
  identifier: string;
  source: string;
  uri?: string;
}

export interface Attribution {
  id: number;
  name: string;
  uid: string;
  url?: string;
}

export interface Licence {
  id: number;
  acronym: string;
  name: string;
  url: string;
  licenceVersion: string;
  imageUrl?: string;
}

/** Summary types for list views */
export interface CollectionSummary {
  uid: string;
  name: string;
  acronym?: string;
  abstract?: string;
}

export interface InstitutionSummary {
  uid: string;
  name: string;
  acronym?: string;
  logoRef?: ImageRef;
  websiteUrl?: string;
  institutionType?: string;
}

export interface DataResourceSummary {
  uid: string;
  name: string;
  pubDescription?: string;
  isPrivate?: boolean;
  resourceType?: string;
}

export interface DataProviderSummary {
  uid: string;
  name: string;
  logoRef?: ImageRef;
}

/** Entity type enum based on UID prefix */
export type EntityType = 'collection' | 'institution' | 'dataProvider' | 'dataResource' | 'dataHub' | 'tempDataResource';

export function entityTypeFromUid(uid: string): EntityType | null {
  const prefix = uid.substring(0, 2);
  switch (prefix) {
    case 'co': return 'collection';
    case 'in': return 'institution';
    case 'dp': return 'dataProvider';
    case 'dr': return 'dataResource';
    case 'dh': return 'dataHub';
    case 'dt': return 'tempDataResource';
    default: return null;
  }
}
