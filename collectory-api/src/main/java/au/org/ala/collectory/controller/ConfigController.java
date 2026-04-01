package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/ws/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AppProperties appProperties;
    private final MetadataService metadataService;

    @GetMapping
    public ResponseEntity<?> config() {
        Map<String, Object> config = new LinkedHashMap<>();

        // Skin/branding
        config.put("projectName", appProperties.getProjectName());
        config.put("regionName", appProperties.getRegionName());
        config.put("orgNameLong", appProperties.getSkin().getOrgNameLong());
        config.put("orgNameShort", appProperties.getSkin().getOrgNameShort());
        config.put("homeUrl", appProperties.getSkin().getHomeUrl());

        // External service URLs
        config.put("serverUrl", appProperties.getServerUrl());
        // Return proxy paths so the SPA avoids CORS issues with external services
        config.put("biocacheServicesUrl", "/ws/proxy/biocache");
        config.put("biocacheUiUrl", appProperties.getBiocacheUiUrl());
        config.put("loggerUrl", "/ws/proxy/logger");
        config.put("alertsUrl", appProperties.getAlertsUrl());
        config.put("speciesListToolUrl", appProperties.getSpeciesListToolUrl());

        // Header/footer
        config.put("headerAndFooterBaseUrl", appProperties.getHeaderAndFooter().getBaseUrl());
        config.put("headerAndFooterVersion", appProperties.getHeaderAndFooter().getVersion());

        // Feature flags
        config.put("disableAlertLinks", appProperties.isDisableAlertLinks());
        config.put("disableLoggerLinks", appProperties.isDisableLoggerLinks());
        config.put("gbifRegistrationEnabled", appProperties.isGbifRegistrationEnabled());
        config.put("gbifWebsite", appProperties.getGbifWebsite());
        config.put("isPipelinesCompatible", appProperties.isPipelinesCompatible());

        // Maps
        config.put("cartodbPattern", appProperties.getCartodbPattern());
        config.put("googleApiKey", appProperties.getGoogleApiKey());
        config.put("mapboxAccessToken", appProperties.getMapboxAccessToken());

        Map<String, Object> collectionsMap = new LinkedHashMap<>();
        collectionsMap.put("centreMapLon", appProperties.getCollectionsMapCentreLon());
        collectionsMap.put("centreMapLat", appProperties.getCollectionsMapCentreLat());
        collectionsMap.put("defaultZoom", appProperties.getCollectionsMapDefaultZoom());
        config.put("collectionsMap", collectionsMap);

        // BIE / Spatial
        config.put("bieBaseUrl", appProperties.getBieBaseUrl());
        config.put("bieSearchPath", appProperties.getBieSearchPath());
        config.put("spatialBaseUrl", appProperties.getSpatialBaseUrl());

        // Extra info in data-sets view
        Map<String, Object> extraInfo = new LinkedHashMap<>();
        extraInfo.put("enabled", appProperties.getShowExtraInfoInDataSetsView().isEnabled());
        extraInfo.put("relativeTime", appProperties.getShowExtraInfoInDataSetsView().isRelativeTime());
        config.put("showExtraInfoInDataSetsView", extraInfo);

        // User details
        config.put("userDetailsUrl", appProperties.getUserDetailsUrl());

        // Skin extras
        config.put("orgSupportEmail", appProperties.getSkin().getOrgSupportEmail());
        config.put("favicon", appProperties.getSkin().getFavicon());

        // Auth / OIDC
        Map<String, Object> authConfig = new LinkedHashMap<>();
        authConfig.put("oidcEnabled", appProperties.getSecurity().isOidcEnabled());
        authConfig.put("oidcDiscoveryUri", appProperties.getSecurity().getOidcDiscoveryUri());
        authConfig.put("oidcClientId", appProperties.getSecurity().getOidcClientId());
        authConfig.put("oidcScope", appProperties.getSecurity().getOidcScope());
        authConfig.put("oidcLogoutUrl", appProperties.getSecurity().getOidcLogoutUrl());
        config.put("auth", authConfig);

        // Lists for forms
        config.put("resourceTypes", appProperties.getDataResource().getResourceTypeList());
        config.put("statusList", appProperties.getDataResource().getStatusList());
        config.put("contentTypes", appProperties.getDataResource().getContentTypesList());
        config.put("provenanceTypesList", appProperties.getDataResource().getProvenanceTypesList());
        config.put("permissionsDocumentTypes", appProperties.getDataResource().getPermissionsDocumentTypes());
        config.put("networkTypes", appProperties.getNetworkTypes());
        config.put("collectionTypes", appProperties.getCollection().getCollectionTypes());
        config.put("institutionTypes", appProperties.getInstitution().getInstitutionTypes());
        config.put("kingdoms", appProperties.getCollection().getKingdoms());
        config.put("contactTitles", appProperties.getContacts().getTitles());

        // Connection profiles
        config.put("connectionProfiles", metadataService.getConnectionProfiles());

        return ResponseEntity.ok(config);
    }

    @GetMapping("/charts")
    public ResponseEntity<?> charts() {
        try {
            String path = appProperties.getChartsConfigPath();
            String content = Files.readString(Path.of(path));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);
        } catch (Exception e) {
            return ResponseEntity.ok("{}");
        }
    }
}
