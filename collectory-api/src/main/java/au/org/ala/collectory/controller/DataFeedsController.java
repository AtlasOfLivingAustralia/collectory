package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.repository.DataResourceRepository;
import au.org.ala.collectory.service.RifCsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DataFeedsController {

    private final DataResourceRepository dataResourceRepository;
    private final AppProperties appProperties;
    private final RifCsService rifCsService;

    @GetMapping(value = {"/rif-cs", "/ws/rif-cs"}, produces = MediaType.APPLICATION_XML_VALUE)
    @SuppressWarnings("unchecked")
    public void rifCs(HttpServletResponse response) throws IOException {
        response.setContentType("text/xml;charset=UTF-8");

        try {
            List<DataResource> dataResources = rifCsService.getDataResources(null);
            List<DataProvider> providers = rifCsService.getDataProviders();
            Map<String, Object> resData = rifCsService.getResData(dataResources);
            Map<String, String> resContentTypes = (Map<String, String>) resData.get("resContentTypes");
            Map<String, List<Double>> resBoundingBoxCoords = appProperties.isRifcsExcludeBounds()
                    ? Map.of()
                    : (Map<String, List<Double>>) resData.get("resBoundingBoxCoords");

            PrintWriter writer = response.getWriter();
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<registryObjects xmlns=\"http://ands.org.au/standards/rif-cs/registryObjects\"" +
                    " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                    " xsi:schemaLocation=\"http://ands.org.au/standards/rif-cs/registryObjects" +
                    " http://services.ands.org.au/documentation/rifcs/1.2.0/schema/registryObjects.xsd\">");

            for (DataResource dr : dataResources) {
                writer.println("  <registryObject group=\"" + escapeXml(appProperties.getSkin().getOrgNameLong()) + "\">");
                writer.println("    <key>" + escapeXml(dr.getUid()) + "</key>");
                writer.println("    <originatingSource>" + escapeXml(appProperties.getServerUrl()) + "</originatingSource>");
                writer.println("    <collection type=\"dataset\">");
                writer.println("      <name type=\"primary\"><namePart>" + escapeXml(dr.getName()) + "</namePart></name>");
                if (dr.getPubDescription() != null) {
                    writer.println("      <description type=\"full\">" + escapeXml(dr.getPubDescription()) + "</description>");
                }
                writer.println("      <location><address><electronic type=\"url\">");
                writer.println("        <value>" + escapeXml(appProperties.getServerUrl() + "/public/showDataResource/" + dr.getUid()) + "</value>");
                writer.println("      </electronic></address></location>");
                if (resContentTypes != null && resContentTypes.containsKey(dr.getUid())) {
                    writer.println("      <subject type=\"local\">" + escapeXml(resContentTypes.get(dr.getUid())) + "</subject>");
                }
                if (resBoundingBoxCoords.containsKey(dr.getUid())) {
                    List<Double> coords = resBoundingBoxCoords.get(dr.getUid());
                    writer.println("      <coverage><spatial type=\"iso19139dcmiBox\">"
                            + "westlimit=" + coords.get(0) + "; southlimit=" + coords.get(1)
                            + "; eastlimit=" + coords.get(2) + "; northlimit=" + coords.get(3)
                            + "</spatial></coverage>");
                }
                writer.println("    </collection>");
                writer.println("  </registryObject>");
            }

            writer.println("</registryObjects>");
            writer.flush();
        } catch (Exception e) {
            log.error("Failed to generate RIF-CS feed: {}", e.getMessage(), e);
            PrintWriter writer = response.getWriter();
            response.setStatus(503);
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<error>" + escapeXml(e.getMessage()) + "</error>");
            writer.flush();
        }
    }

    @GetMapping(value = "/feed", produces = MediaType.APPLICATION_XML_VALUE)
    public void rssFeed(HttpServletResponse response) throws IOException {
        response.setContentType("application/xml;charset=UTF-8");

        String siteUrl = appProperties.getServerUrl();
        String orgName = appProperties.getSkin().getOrgNameLong();
        String biocacheUrl = appProperties.getBiocacheServicesUrl();

        List<DataResource> resources = dataResourceRepository.findAll();
        List<DataResource> available = resources.stream()
                .filter(dr -> "dataAvailable".equals(dr.getStatus()))
                .sorted(Comparator.comparing(
                        (DataResource dr) -> dr.getDataCurrency() != null ? dr.getDataCurrency() : dr.getDateCreated(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        PrintWriter writer = response.getWriter();
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.println("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\" xmlns:ipt=\"http://ipt.gbif.org/\">");
        writer.println("  <channel>");
        writer.println("    <title>" + escapeXml(orgName + " Collections RSS Feed") + "</title>");
        writer.println("    <link>" + escapeXml(siteUrl) + "</link>");
        writer.println("    <description>" + escapeXml(orgName + " data resources RSS feed") + "</description>");

        DateTimeFormatter rfc822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
                .withZone(ZoneId.systemDefault());

        for (DataResource dr : available) {
            LocalDateTime dateUpdated = dr.getDataCurrency() != null ? dr.getDataCurrency() : dr.getDateCreated();
            String downloadUrl = biocacheUrl + "/occurrences/index/download?sourceTypeId=0&reasonTypeId=9&file=data-resource-"
                    + dr.getUid() + "&q=data_resource_uid%3A" + dr.getUid();

            writer.println("    <item>");
            writer.println("      <title>" + escapeXml(dr.getName()) + "</title>");
            writer.println("      <guid>" + escapeXml(siteUrl + "/public/showDataResource/" + dr.getUid()) + "</guid>");
            writer.println("      <link>" + escapeXml(downloadUrl) + "</link>");
            if (dateUpdated != null) {
                writer.println("      <pubDate>" + rfc822.format(dateUpdated.atZone(ZoneId.systemDefault())) + "</pubDate>");
            }
            if (dr.getPubDescription() != null) {
                writer.println("      <description>" + escapeXml(dr.getPubDescription()) + "</description>");
            }
            writer.println("      <ipt:eml>" + escapeXml(siteUrl + "/eml/" + dr.getUid()) + "</ipt:eml>");
            writer.println("    </item>");
        }

        writer.println("  </channel>");
        writer.println("</rss>");
        writer.flush();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
