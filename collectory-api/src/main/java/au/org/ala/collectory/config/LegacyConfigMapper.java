package au.org.ala.collectory.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Maps legacy Grails-style property names from the external collectory-config.properties
 * to AppProperties fields. This allows the existing external config file (which uses
 * unprefixed Grails property names) to work with the Spring Boot app (which expects
 * the collectory.* prefix via @ConfigurationProperties).
 *
 * Priority: if a collectory.* prefixed property is already set (from application.properties
 * or environment), it takes precedence. Legacy properties only fill in gaps.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyConfigMapper {

    private final Environment env;
    private final AppProperties props;

    @PostConstruct
    public void mapLegacyProperties() {
        int mapped = 0;

        // --- Core ---
        mapped += setIfLegacy("grails.serverURL", () -> props.getServerUrl(), props::setServerUrl);
        mapped += setIfLegacy("projectName", () -> props.getProjectName(), props::setProjectName);
        mapped += setIfLegacy("regionName", () -> props.getRegionName(), props::setRegionName);
        mapped += setIfLegacy("siteDefaultLanguage", () -> props.getSiteDefaultLanguage(), props::setSiteDefaultLanguage);
        mapped += setIfLegacy("defaultLocale", () -> props.getSiteDefaultLanguage(), props::setSiteDefaultLanguage);

        // --- Upload ---
        mapped += setIfLegacy("uploadFilePath", () -> props.getUploadFilePath(), props::setUploadFilePath);
        mapped += setIfLegacy("uploadExternalUrlPath", () -> props.getUploadExternalUrlPath(), props::setUploadExternalUrlPath);

        // --- Repository ---
        mapped += setIfLegacy("repository.location.images", () -> props.getRepositoryLocationImages(), props::setRepositoryLocationImages);

        // --- Roles ---
        mapped += setIfLegacy("ROLE_ADMIN", () -> props.getRoleAdmin(), props::setRoleAdmin);
        mapped += setIfLegacy("ROLE_EDITOR", () -> props.getRoleEditor(), props::setRoleEditor);

        // --- External Services ---
        mapped += setIfLegacy("loggerURL", () -> props.getLoggerUrl(), props::setLoggerUrl);
        mapped += setIfLegacy("alertsUrl", () -> props.getAlertsUrl(), props::setAlertsUrl);
        mapped += setIfLegacy("alertUrl", () -> props.getAlertsUrl(), props::setAlertsUrl); // backward compat
        mapped += setIfLegacy("alertResourceName", () -> props.getAlertResourceName(), props::setAlertResourceName);
        mapped += setIfLegacy("speciesListToolUrl", () -> props.getSpeciesListToolUrl(), props::setSpeciesListToolUrl);
        mapped += setIfLegacy("disableAlertLinks", () -> str(props.isDisableAlertLinks()), v -> props.setDisableAlertLinks(bool(v)));
        mapped += setIfLegacy("disableLoggerLinks", () -> str(props.isDisableLoggerLinks()), v -> props.setDisableLoggerLinks(bool(v)));
        mapped += setIfLegacy("biocacheServicesUrl", () -> props.getBiocacheServicesUrl(), props::setBiocacheServicesUrl);
        mapped += setIfLegacy("biocacheUiURL", () -> props.getBiocacheUiUrl(), props::setBiocacheUiUrl);
        mapped += setIfLegacy("isPipelinesCompatible", () -> str(props.isPipelinesCompatible()), v -> props.setPipelinesCompatible(bool(v)));

        // --- Display ---
        mapped += setIfLegacy("showExtraInfoInDataSetsView.enabled",
                () -> str(props.getShowExtraInfoInDataSetsView().isEnabled()),
                v -> props.getShowExtraInfoInDataSetsView().setEnabled(bool(v)));
        mapped += setIfLegacy("showExtraInfoInDataSetsView.relativeTime",
                () -> str(props.getShowExtraInfoInDataSetsView().isRelativeTime()),
                v -> props.getShowExtraInfoInDataSetsView().setRelativeTime(bool(v)));

        // --- Skin ---
        mapped += setIfLegacy("skin.homeUrl", () -> props.getSkin().getHomeUrl(), v -> props.getSkin().setHomeUrl(v));
        mapped += setIfLegacy("skin.orgNameLong", () -> props.getSkin().getOrgNameLong(), v -> props.getSkin().setOrgNameLong(v));
        mapped += setIfLegacy("skin.orgNameShort", () -> props.getSkin().getOrgNameShort(), v -> props.getSkin().setOrgNameShort(v));
        mapped += setIfLegacy("skin.orgSupportEmail", () -> props.getSkin().getOrgSupportEmail(), v -> props.getSkin().setOrgSupportEmail(v));
        mapped += setIfLegacy("skin.favicon", () -> props.getSkin().getFavicon(), v -> props.getSkin().setFavicon(v));

        // --- Header and Footer ---
        mapped += setIfLegacy("headerAndFooter.baseURL", () -> props.getHeaderAndFooter().getBaseUrl(), v -> props.getHeaderAndFooter().setBaseUrl(v));
        mapped += setIfLegacy("headerAndFooter.version",
                () -> str(props.getHeaderAndFooter().getVersion()),
                v -> props.getHeaderAndFooter().setVersion(Integer.parseInt(v)));

        // --- GBIF ---
        mapped += setIfLegacy("gbifApiUrl", () -> props.getGbifApiUrl(), props::setGbifApiUrl);
        mapped += setIfLegacy("gbifUniqueKeyTerm", () -> props.getGbifUniqueKeyTerm(), props::setGbifUniqueKeyTerm);
        mapped += setIfLegacy("gbifWebsite", () -> props.getGbifWebsite(), props::setGbifWebsite);
        mapped += setIfLegacy("gbifDefaultEntityCountry", () -> props.getGbifDefaultEntityCountry(), props::setGbifDefaultEntityCountry);
        mapped += setIfLegacy("gbifRegistrationEnabled", () -> str(props.isGbifRegistrationEnabled()), v -> props.setGbifRegistrationEnabled(bool(v)));
        mapped += setIfLegacy("gbifEndorsingNodeKey", () -> props.getGbifEndorsingNodeKey(), props::setGbifEndorsingNodeKey);
        mapped += setIfLegacy("gbifInstallationKey", () -> props.getGbifInstallationKey(), props::setGbifInstallationKey);
        mapped += setIfLegacy("gbifApiUser", () -> props.getGbifApiUser(), props::setGbifApiUser);
        mapped += setIfLegacy("gbifApiPassword", () -> props.getGbifApiPassword(), props::setGbifApiPassword);
        mapped += setIfLegacy("gbifExportUrlBase", () -> props.getGbifExportUrlBase(), props::setGbifExportUrlBase);
        mapped += setIfLegacy("gbifRegistrationDryRun", () -> str(props.isGbifRegistrationDryRun()), v -> props.setGbifRegistrationDryRun(bool(v)));
        mapped += setIfLegacy("gbifLicenceMappingUrl", () -> props.getGbifLicenceMappingUrl(), props::setGbifLicenceMappingUrl);
        mapped += setIfLegacy("gbifOrphansPublisherID", () -> props.getGbifOrphansPublisherID(), props::setGbifOrphansPublisherID);
        mapped += setIfLegacy("useGbifDoi", () -> str(props.isUseGbifDoi()), v -> props.setUseGbifDoi(bool(v)));
        mapped += setIfLegacy("gbifRegistrationRole", () -> props.getGbifRegistrationRole(), props::setGbifRegistrationRole);

        // --- GBIF Citations ---
        mapped += setIfLegacy("gbif.citations.lookup", () -> props.getGbifCitations().getLookup(), v -> props.getGbifCitations().setLookup(v));
        mapped += setIfLegacy("gbif.citations.search", () -> props.getGbifCitations().getSearch(), v -> props.getGbifCitations().setSearch(v));
        mapped += setIfLegacy("gbif.citations.enabled",
                () -> str(props.getGbifCitations().isEnabled()),
                v -> props.getGbifCitations().setEnabled(bool(v)));

        // --- Maps ---
        mapped += setIfLegacy("collectionsMap.centreMapLon",
                () -> str(props.getCollectionsMapCentreLon()),
                v -> props.setCollectionsMapCentreLon(Double.parseDouble(v)));
        mapped += setIfLegacy("collectionsMap.centreMapLat",
                () -> str(props.getCollectionsMapCentreLat()),
                v -> props.setCollectionsMapCentreLat(Double.parseDouble(v)));
        mapped += setIfLegacy("collectionsMap.defaultZoom",
                () -> str(props.getCollectionsMapDefaultZoom()),
                v -> props.setCollectionsMapDefaultZoom(Integer.parseInt(v)));
        mapped += setIfLegacy("mapboxAccessToken", () -> props.getMapboxAccessToken(), props::setMapboxAccessToken);
        mapped += setIfLegacy("cartodb.pattern", () -> props.getCartodbPattern(), props::setCartodbPattern);

        // --- EML ---
        mapped += setIfLegacy("eml.organizationName", () -> props.getEml().getOrganizationName(), v -> props.getEml().setOrganizationName(v));
        mapped += setIfLegacy("eml.deliveryPoint", () -> props.getEml().getDeliveryPoint(), v -> props.getEml().setDeliveryPoint(v));
        mapped += setIfLegacy("eml.city", () -> props.getEml().getCity(), v -> props.getEml().setCity(v));
        mapped += setIfLegacy("eml.administrativeArea", () -> props.getEml().getAdministrativeArea(), v -> props.getEml().setAdministrativeArea(v));
        mapped += setIfLegacy("eml.postalCode", () -> props.getEml().getPostalCode(), v -> props.getEml().setPostalCode(v));
        mapped += setIfLegacy("eml.country", () -> props.getEml().getCountry(), v -> props.getEml().setCountry(v));
        mapped += setIfLegacy("eml.electronicMailAddress", () -> props.getEml().getElectronicMailAddress(), v -> props.getEml().setElectronicMailAddress(v));

        // --- BIE / Spatial ---
        mapped += setIfLegacy("bie.baseURL", () -> props.getBieBaseUrl(), props::setBieBaseUrl);
        mapped += setIfLegacy("bie.searchPath", () -> props.getBieSearchPath(), props::setBieSearchPath);
        mapped += setIfLegacy("spatial.baseURL", () -> props.getSpatialBaseUrl(), props::setSpatialBaseUrl);

        // --- Google ---
        mapped += setIfLegacy("google.apikey", () -> props.getGoogleApiKey(), props::setGoogleApiKey);

        // --- URL Templates ---
        mapped += setIfLegacy("resource.publicArchive.url.template", () -> props.getPublicArchiveUrlTemplate(), props::setPublicArchiveUrlTemplate);
        mapped += setIfLegacy("resource.gbifExport.url.template", () -> props.getGbifExportUrlTemplate(), props::setGbifExportUrlTemplate);
        mapped += setIfLegacy("citation.template", () -> props.getCitationTemplate(), props::setCitationTemplate);
        mapped += setIfLegacy("citation.link.template", () -> props.getCitationLinkTemplate(), props::setCitationLinkTemplate);
        mapped += setIfLegacy("citation.rights.template", () -> props.getCitationRightsTemplate(), props::setCitationRightsTemplate);

        // --- RIFCS ---
        mapped += setIfLegacy("rifcs.excludeBounds", () -> str(props.isRifcsExcludeBounds()), v -> props.setRifcsExcludeBounds(bool(v)));

        // --- User Details ---
        mapped += setIfLegacy("userDetails.url", () -> props.getUserDetailsUrl(), props::setUserDetailsUrl);
        mapped += setIfLegacy("userdetails.url", () -> props.getUserDetailsUrl(), props::setUserDetailsUrl);

        // --- OIDC (for frontend) ---
        mapped += setIfLegacy("security.oidc.discoveryUri",
                () -> props.getSecurity().getOidcDiscoveryUri(),
                v -> props.getSecurity().setOidcDiscoveryUri(v));
        mapped += setIfLegacy("security.oidc.logoutUrl",
                () -> props.getSecurity().getOidcLogoutUrl(),
                v -> props.getSecurity().setOidcLogoutUrl(v));
        mapped += setIfLegacy("security.oidc.scope",
                () -> props.getSecurity().getOidcScope(),
                v -> props.getSecurity().setOidcScope(v));

        if (mapped > 0) {
            log.info("LegacyConfigMapper: mapped {} legacy properties to AppProperties", mapped);
        }
    }

    /**
     * If the legacy property exists in the environment and the current AppProperties value
     * is null/empty/default, apply the legacy value.
     *
     * @return 1 if a mapping was applied, 0 otherwise
     */
    private int setIfLegacy(String legacyKey, java.util.function.Supplier<String> currentValue,
                            java.util.function.Consumer<String> setter) {
        String legacyValue = env.getProperty(legacyKey);
        if (legacyValue != null && !legacyValue.isBlank()) {
            String current = currentValue.get();
            if (current == null || current.isBlank() || isPlaceholder(current)) {
                setter.accept(legacyValue.trim());
                log.debug("Mapped legacy property '{}' = '{}'", legacyKey, legacyValue.trim());
                return 1;
            }
        }
        return 0;
    }

    private static boolean isPlaceholder(String value) {
        return "XXXXXXXXXXXXXXXX".equals(value) || "change me".equals(value) || "TO-BE-ADDED".equals(value);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean bool(String value) {
        return Boolean.parseBoolean(value);
    }
}
