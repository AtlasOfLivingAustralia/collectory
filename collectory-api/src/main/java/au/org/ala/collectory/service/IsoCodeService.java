package au.org.ala.collectory.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class IsoCodeService {

    private final Map<String, String> isoCodesMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (String country : Locale.getISOCountries()) {
            Locale locale = new Locale("", country);
            isoCodesMap.put(locale.getISO3Country().toUpperCase(), country);
        }
        log.info("Loaded {} ISO country codes", isoCodesMap.size());
    }

    public String iso3CountryCodeToIso2CountryCode(String iso3CountryCode) {
        return isoCodesMap.get(iso3CountryCode.toUpperCase());
    }

    public String iso2CountryCodeToIso3CountryCode(String iso2CountryCode) {
        Locale locale = new Locale("", iso2CountryCode);
        return locale.getISO3Country();
    }

    public String iso2CountryCodeToCountryName(String iso2CountryCode) {
        Locale locale = new Locale("", iso2CountryCode);
        return locale.getDisplayCountry();
    }

    public String iso3CountryCodeToCountryName(String iso3CountryCode) {
        String iso2 = isoCodesMap.get(iso3CountryCode.toUpperCase());
        if (iso2 == null) return null;
        Locale locale = new Locale("", iso2);
        return locale.getDisplayCountry();
    }

    public boolean isIso2Code(String countryCode) {
        return isoCodesMap.containsValue(countryCode.toUpperCase());
    }
}
