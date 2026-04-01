import apiClient from '../client';

export interface AppConfig {
  biocacheServicesUrl: string;
  biocacheUiUrl: string;
  loggerUrl: string;
  disableLoggerLinks: boolean;
  disableAlertLinks: boolean;
  alertsUrl?: string;
  isPipelinesCompatible: boolean;
  serverUrl: string;

  // Map
  collectionsMap?: {
    centreMapLon: number;
    centreMapLat: number;
    defaultZoom: number;
  };
  mapboxAccessToken?: string;

  // Branding
  projectName?: string;
  regionName?: string;
  orgNameLong?: string;
  orgNameShort?: string;
  homeUrl?: string;
  orgSupportEmail?: string;
  favicon?: string;

  // Header/footer
  headerAndFooterBaseUrl?: string;
  headerAndFooterVersion?: number;

  // External services
  speciesListToolUrl?: string;
  bieBaseUrl?: string;
  bieSearchPath?: string;
  spatialBaseUrl?: string;
  userDetailsUrl?: string;

  // GBIF
  gbifRegistrationEnabled?: boolean;
  gbifWebsite?: string;

  // Display
  showExtraInfoInDataSetsView?: {
    enabled: boolean;
    relativeTime: boolean;
  };

  // Auth
  auth?: {
    oidcEnabled: boolean;
    oidcDiscoveryUri?: string;
    oidcClientId?: string;
    oidcScope?: string;
    oidcLogoutUrl?: string;
  };

  // Charts
  cartodbPattern?: string;
  googleApiKey?: string;

  // Form option lists
  contactTitles?: string[];
  kingdoms?: string[];
  connectionProfiles?: unknown[];
  networkTypes?: string[];
  statusList?: string[];
  provenanceTypesList?: string[];
  permissionsDocumentTypes?: string[];
  resourceTypes?: string[];
  contentTypes?: string[];
  collectionTypes?: string[];
  institutionTypes?: string[];
}

export async function getConfig(): Promise<AppConfig> {
  const { data } = await apiClient.get<AppConfig>('/config');
  return data;
}
