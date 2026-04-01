package au.org.ala.collectory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "collectory")
public class AppProperties {

    private String serverUrl;
    private String projectName = "Atlas of Living Australia";
    private String regionName = "Australia";
    private String uploadFilePath = "/data/collectory/upload";
    private String uploadExternalUrlPath;
    private String siteDefaultLanguage = "en";

    // GBIF
    private String gbifApiUrl = "https://api.gbif.org/v1/";
    private String gbifUniqueKeyTerm = "http://rs.gbif.org/terms/1.0/gbifID";
    private String gbifWebsite = "https://www.gbif-uat.org/";
    private String gbifDefaultEntityCountry;
    private boolean gbifRegistrationEnabled = false;
    private String gbifEndorsingNodeKey;
    private String gbifInstallationKey;
    private String gbifApiUser;
    private String gbifApiPassword;
    private String gbifExportUrlBase;
    private boolean gbifRegistrationDryRun = true;
    private String gbifLicenceMappingUrl;
    private String gbifOrphansPublisherID;
    private boolean useGbifDoi = true;
    private String gbifRegistrationRole = "ROLE_ADMIN";

    // GBIF Citations
    private GbifCitations gbifCitations = new GbifCitations();

    // Skin
    private Skin skin = new Skin();

    // Header and Footer
    private HeaderAndFooter headerAndFooter = new HeaderAndFooter();

    // Repository
    private String repositoryLocationImages = "/data/collectory/data";

    // External services
    private String loggerUrl;
    private String alertsUrl;
    private String alertResourceName = "Alerts";
    private String speciesListToolUrl;
    private boolean disableAlertLinks = false;
    private boolean disableLoggerLinks = false;
    private String biocacheServicesUrl;
    private String biocacheUiUrl;
    private boolean isPipelinesCompatible = true;

    // Roles
    private String roleAdmin = "ROLE_ADMIN";
    private String roleEditor = "ROLE_EDITOR";
    private String requiredScopes = "ala/internal";

    // Security
    private Security security = new Security();

    // RIF-CS
    private boolean rifcsExcludeBounds = true;

    // Templates
    private String publicArchiveUrlTemplate;
    private String gbifExportUrlTemplate;
    private String citationTemplate = "Records provided by @entityName@, accessed through ALA website.";
    private String citationLinkTemplate = "For more information: @link@";
    private String citationRightsTemplate = "";

    // EML
    private Eml eml = new Eml();

    // Maps
    private String cartodbPattern;
    private String googleApiKey;
    private double collectionsMapCentreLon = 134.0;
    private double collectionsMapCentreLat = -28.2;
    private int collectionsMapDefaultZoom = 4;
    private String mapboxAccessToken;

    // Charts
    private String chartsConfigPath = "/data/collectory/config/charts.json";

    // BIE / Spatial
    private String bieBaseUrl;
    private String bieSearchPath = "/search";
    private String spatialBaseUrl;

    // Extra info
    private ShowExtraInfoInDataSetsView showExtraInfoInDataSetsView = new ShowExtraInfoInDataSetsView();

    // User Details
    private String userDetailsUrl;

    // Sitemap
    private String sitemapDir = "/data/collectory";
    private boolean sitemapEnabled = true;

    // Lists
    private List<String> networkTypes;
    private DataResourceConfig dataResource = new DataResourceConfig();
    private CollectionConfig collection = new CollectionConfig();
    private InstitutionConfig institution = new InstitutionConfig();
    private ContactsConfig contacts = new ContactsConfig();

    @Data
    public static class GbifCitations {
        private String lookup;
        private String search;
        private boolean enabled = true;
    }

    @Data
    public static class Skin {
        private String layout = "ala-main";
        private boolean fluidLayout = true;
        private String homeUrl;
        private String orgNameLong = "Atlas of Living Australia";
        private String orgNameShort = "Atlas";
        private String orgSupportEmail;
        private String favicon;
    }

    @Data
    public static class HeaderAndFooter {
        private String baseUrl;
        private int version = 2;
    }

    @Data
    public static class Eml {
        private String organizationName = "Atlas of Living Australia";
        private String deliveryPoint;
        private String city;
        private String administrativeArea;
        private String postalCode;
        private String country;
        private String electronicMailAddress;
    }

    @Data
    public static class DataResourceConfig {
        private List<String> statusList;
        private List<String> resourceTypeList;
        private List<String> permissionsDocumentTypes;
        private List<String> contentTypesList;
        private List<String> provenanceTypesList;
    }

    @Data
    public static class CollectionConfig {
        private List<String> collectionTypes;
        private List<String> kingdoms;
        private List<String> developmentStatuses;
    }

    @Data
    public static class InstitutionConfig {
        private List<String> institutionTypes;
    }

    @Data
    public static class ContactsConfig {
        private List<String> titles;
    }

    @Data
    public static class ShowExtraInfoInDataSetsView {
        private boolean enabled = false;
        private boolean relativeTime = false;
    }

    @Data
    public static class Security {
        private boolean oidcEnabled = true;
        private String oidcDiscoveryUri;
        private String oidcClientId;
        private String oidcScope = "openid profile email ala roles";
        private String oidcLogoutUrl;
    }
}
