package au.org.ala.collectory

import org.springframework.web.servlet.support.RequestContextUtils

import java.util.regex.Pattern

class MessagesController {

    static final Pattern PROPERTIES_PATTERN = ~/^messages(_[a-zA-Z0-9]+)*\.properties$/

    def messageSource

    static defaultAction = "i18n"

    /**
     * Export raw i18n message properties as TEXT for use by JavaScript i18n library
     *
     * @param id - messageSource file name
     * @return
     */
    def i18n(String id) {
        if (!id || !(id ==~ PROPERTIES_PATTERN)) {
            render(status: 404)
            return
        }

        Locale locale = RequestContextUtils.getLocale(request) ?: request.locale

        if (id.startsWith("messages_")) {
            def match = (id =~ /^messages_(.+)\.properties$/)
            if (match) {
                List locBits = match[0][1].tokenize('_')
                if (locBits.size() >= 2) {
                    locale = new Locale(locBits[0], locBits[1])
                } else if (locBits.size() == 1) {
                    locale = new Locale(locBits[0])
                }
            }
        }

        def keys = messageSource.withTraits(MessagePropertiesTrait).getMessageKeys(locale)
        response.setHeader("Content-type", "text/plain; charset=UTF-8")
        def messages = keys.collect { "${it.key}=${it.value}" }
        render ( text: messages.sort().join("\n") )
    }
}
