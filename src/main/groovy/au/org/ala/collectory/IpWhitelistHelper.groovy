package au.org.ala.collectory

import groovy.util.logging.Slf4j
import javax.servlet.http.HttpServletRequest
import java.net.InetAddress

/**
 * Helper class for IP address extraction and CIDR / whitelist matching.
 */
@Slf4j
class IpWhitelistHelper {

    /**
     * Extracts candidate client IP addresses from the request, checking headers
     * (X-Forwarded-For, X-Real-IP) and request.remoteAddr.
     */
    static List<String> extractClientIps(HttpServletRequest request) {
        if (!request) return []
        Set<String> ips = new LinkedHashSet<>()

        String xForwardedFor = request.getHeader("X-Forwarded-For")
        if (xForwardedFor) {
            xForwardedFor.split(',').each {
                def trimmed = it.trim()
                if (trimmed) ips.add(trimmed)
            }
        }

        String xRealIp = request.getHeader("X-Real-IP")
        if (xRealIp && xRealIp.trim()) {
            ips.add(xRealIp.trim())
        }

        if (request.remoteAddr && request.remoteAddr.trim()) {
            ips.add(request.remoteAddr.trim())
        }

        return ips.toList()
    }

    /**
     * Checks if any of the candidate client IPs match any entry in the whitelist.
     * Whitelist can be a Collection, Object[] / String[], or comma-separated String.
     */
    static boolean isIpWhitelisted(List<String> clientIps, Object whitelist) {
        if (!clientIps || !whitelist) return false

        List<String> entries = []
        if (whitelist instanceof java.util.Collection) {
            entries = ((java.util.Collection) whitelist).collect { it?.toString()?.trim() }.findAll { it }
        } else if (whitelist instanceof java.util.Map) {
            entries = ((java.util.Map) whitelist).values().collect { it?.toString()?.trim() }.findAll { it }
        } else if (whitelist instanceof Object[]) {
            entries = ((Object[]) whitelist).collect { it?.toString()?.trim() }.findAll { it }
        } else if (whitelist instanceof String) {
            entries = ((String) whitelist).split(',').collect { it.trim() }.findAll { it }
        } else {
            entries = [whitelist.toString().trim()].findAll { it }
        }

        if (entries.isEmpty()) return false

        for (String clientIp : clientIps) {
            for (String entry : entries) {
                if (matches(clientIp, entry)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Checks if a single client IP matches a single whitelist entry (IP, CIDR, or hostname).
     */
    static boolean matches(String clientIp, String whitelistEntry) {
        if (!clientIp || !whitelistEntry) return false

        clientIp = clientIp.trim()
        whitelistEntry = whitelistEntry.trim()

        if (clientIp.equalsIgnoreCase(whitelistEntry)) {
            return true
        }

        try {
            if (whitelistEntry.contains('/')) {
                return matchesCidr(clientIp, whitelistEntry)
            } else {
                InetAddress clientAddr = InetAddress.getByName(clientIp)
                InetAddress whitelistAddr = InetAddress.getByName(whitelistEntry)
                return clientAddr.equals(whitelistAddr)
            }
        } catch (Exception e) {
            log.debug("Error comparing IP {} against whitelist entry {}: {}", clientIp, whitelistEntry, e.message)
            return false
        }
    }

    private static boolean matchesCidr(String clientIp, String cidr) {
        String[] parts = cidr.split('/')
        if (parts.length != 2) return false

        String subnetStr = parts[0].trim()
        int prefixLen
        try {
            prefixLen = Integer.parseInt(parts[1].trim())
        } catch (NumberFormatException e) {
            return false
        }

        InetAddress clientAddr = InetAddress.getByName(clientIp)
        InetAddress subnetAddr = InetAddress.getByName(subnetStr)

        byte[] clientBytes = clientAddr.getAddress()
        byte[] subnetBytes = subnetAddr.getAddress()

        if (clientBytes.length != subnetBytes.length) {
            return false
        }

        int maxBits = clientBytes.length * 8
        if (prefixLen < 0 || prefixLen > maxBits) {
            return false
        }

        int fullBytes = prefixLen / 8
        int remainingBits = prefixLen % 8

        for (int i = 0; i < fullBytes; i++) {
            if (clientBytes[i] != subnetBytes[i]) {
                return false
            }
        }

        if (remainingBits > 0 && fullBytes < clientBytes.length) {
            int mask = (0xFF << (8 - remainingBits)) & 0xFF
            if ((clientBytes[fullBytes] & mask) != (subnetBytes[fullBytes] & mask)) {
                return false
            }
        }

        return true
    }
}
