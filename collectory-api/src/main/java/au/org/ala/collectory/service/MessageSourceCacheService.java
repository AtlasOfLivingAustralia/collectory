package au.org.ala.collectory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageSourceCacheService {

    private final MessageSource messageSource;

    @Cacheable("longTermCache")
    public Map<String, String> getMessagesMap(Locale locale) {
        if (locale == null) {
            locale = Locale.US;
        }

        Map<String, String> messagesMap = new HashMap<>();

        if (messageSource instanceof ResourceBundleMessageSource bundleSource) {
            try {
                Set<String> basenames = bundleSource.getBasenameSet();
                for (String basename : basenames) {
                    ResourceBundle bundle = ResourceBundle.getBundle(basename, locale);
                    for (String key : bundle.keySet()) {
                        messagesMap.put(key, bundle.getString(key));
                    }
                }
            } catch (Exception e) {
                log.warn("Could not load messages for locale {}: {}", locale, e.getMessage());
            }
        }

        return messagesMap;
    }
}
