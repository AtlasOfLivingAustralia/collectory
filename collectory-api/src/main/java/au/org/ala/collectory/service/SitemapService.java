package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.domain.Institution;
import au.org.ala.collectory.repository.CollectionRepository;
import au.org.ala.collectory.repository.DataProviderRepository;
import au.org.ala.collectory.repository.DataResourceRepository;
import au.org.ala.collectory.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "collectory.sitemapEnabled", havingValue = "true", matchIfMissing = true)
public class SitemapService {

    private final AppProperties appProperties;
    private final CollectionRepository collectionRepository;
    private final InstitutionRepository institutionRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;

    private static final String URLSET_HEADER =
            "<?xml version='1.0' encoding='UTF-8'?>"
                    + "<urlset xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                    + "xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9 "
                    + "http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd\" "
                    + "xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">";
    private static final String URLSET_FOOTER = "</urlset>";
    private static final int MAX_URLS = 50_000;
    private static final long MAX_SIZE = 9L * 1024 * 1024;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private File currentFile;
    private int fileCount;
    private int countUrls;
    private FileWriter fw;

    /**
     * Builds the sitemap daily. Initial delay of 1 hour, then every 24 hours.
     */
    @Scheduled(fixedDelay = 86_400_000L, initialDelay = 3_600_000L)
    public void build() throws Exception {
        log.info("Starting sitemap build");
        fileCount = 0;
        initWriter();
        buildSitemap();
        closeWriter();
        buildSitemapIndex();
        log.info("Sitemap build complete");
    }

    private void buildSitemapIndex() throws IOException {
        String sitemapDir = appProperties.getSitemapDir();
        fw = new FileWriter(sitemapDir + "/sitemap.xml");
        fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");

        for (int i = 0; i < fileCount; i++) {
            File tmpFile = new File(sitemapDir + "/sitemap" + i + ".xml.tmp");
            File newFile = new File(sitemapDir + "/sitemap" + i + ".xml");
            if (newFile.exists()) {
                newFile.delete();
            }
            tmpFile.renameTo(newFile);

            fw.write("<sitemap><loc>" + appProperties.getServerUrl() + "/sitemap" + i + ".xml</loc>");
            fw.write("<lastmod>" + LocalDateTime.now().format(DATE_FORMAT) + "</lastmod></sitemap>");
        }

        fw.write("</sitemapindex>");
        fw.flush();
        fw.close();
    }

    private void initWriter() throws IOException {
        currentFile = new File(appProperties.getSitemapDir() + "/sitemap" + fileCount + ".xml.tmp");
        fw = new FileWriter(currentFile);
        fw.write(URLSET_HEADER);
        countUrls = 0;
        fileCount++;
    }

    private void closeWriter() throws IOException {
        fw.write(URLSET_FOOTER);
        fw.flush();
        fw.close();
    }

    private void writeUrl(LocalDateTime lastUpdated, String changefreq, String encodedUrl) throws IOException {
        if (countUrls >= MAX_URLS || currentFile.length() >= MAX_SIZE) {
            closeWriter();
            initWriter();
        }

        fw.write("<url>");
        fw.write("<loc>" + encodedUrl + "</loc>");
        if (lastUpdated != null) {
            fw.write("<lastmod>" + lastUpdated.format(DATE_FORMAT) + "</lastmod>");
        }
        fw.write("<changefreq>" + changefreq + "</changefreq>");
        fw.write("</url>");
        fw.flush();
        countUrls++;
    }

    /**
     * Iterates over all public entities (collections, institutions, data providers,
     * public data resources) and writes their URLs to sitemap files.
     */
    @Transactional(readOnly = true)
    public void buildSitemap() throws IOException {
        String baseUrl = appProperties.getServerUrl();

        for (au.org.ala.collectory.domain.Collection c : collectionRepository.findAll()) {
            writeUrl(c.getLastUpdated(), "weekly", baseUrl + "/public/show/" + c.getUid());
        }

        for (Institution i : institutionRepository.findAll()) {
            writeUrl(i.getLastUpdated(), "weekly", baseUrl + "/public/show/" + i.getUid());
        }

        for (DataProvider dp : dataProviderRepository.findAll()) {
            writeUrl(dp.getLastUpdated(), "weekly", baseUrl + "/public/show/" + dp.getUid());
        }

        for (DataResource dr : dataResourceRepository.findAllByIsPrivateFalse()) {
            writeUrl(dr.getLastUpdated(), "weekly", baseUrl + "/public/show/" + dr.getUid());
        }
    }
}
