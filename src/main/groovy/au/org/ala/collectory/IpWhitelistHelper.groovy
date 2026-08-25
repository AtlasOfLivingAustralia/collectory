package au.org.ala.collectory

import groovy.util.logging.Slf4j
import javax.servlet.http.HttpServletRequest
import java.net.InetAddress

/**
 * Helper class for IP address extraction, trusted proxy verification, and CIDR / whitelist matching.
 */
@Slf4j
class IpWhitelistHelper {

    public static final List<String> DEFAULT_TRUSTED_PROXIES = [
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1"
    ].asImmutable()

    /**
     * Resolves the true client IP address from the request.
     *
     * Security rule:
     * - X-Forwarded-For and X-Real-IP headers are ONLY trusted if request.remoteAddr
     *   originates from a trusted proxy (e.g. localhost reverse proxy or configured trusted proxies).
     * - If request.remoteAddr is NOT a trusted proxy, headers are ignored to prevent spoofing.
     * - When traversing X-Forwarded-For, it is evaluated right-to-left across the trusted proxy chain
     *   to find the first untrusted upstream IP.
     */
    static String extractClientIp(HttpServletRequest request, Object trustedProxies = DEFAULT_TRUSTED_PROXIES) {
        if (!request) return null

        String remoteAddr = request.remoteAddr?.trim()
        if (!remoteAddr) return null

        // If direct connection is NOT from a trusted proxy, do NOT trust any headers
        if (!isIpWhitelisted(remoteAddr, trustedProxies ?: DEFAULT_TRUSTED_PROXIES)) {
            return remoteAddr
        }

        // Direct connection IS from a trusted proxy; inspect X-Forwarded-For
        String xForwardedFor = request.getHeader("X-Forwarded-For")
        if (xForwardedFor && xForwardedFor.trim()) {
            List<String> rawChain = xForwardedFor.split(',').collect { it.trim() }.findAll { it }
            if (!rawChain.isEmpty()) {
                // Traverse right-to-left across the proxy chain
                for (int i = rawChain.size() - 1; i >= 0; i--) {
                    String ip = rawChain[i]
                    if (!isIpWhitelisted(ip, trustedProxies ?: DEFAULT_TRUSTED_PROXIES)) {
                        return ip
                    }
                }
                // If every IP in the chain is a trusted proxy, return the leftmost (original caller)
                return rawChain[0]
            }
        }

String xRealIp = request.getHeader("X-Real-IP")?.trim()
if (xRealIp) {
    String candidate = xRealIp.split(',')[0].trim()
    if (candidate && (candidate ==~ /^[0-9a-fA-F:.]+$/)) {
        return candidate
    }
}

        return remoteAddr
    }

    /**
     * Checks if a single client IP matches any entry in the whitelist.
     */
    static boolean isIpWhitelisted(String clientIp, Object whitelist) {
        if (!clientIp || !whitelist) return false
        return isIpWhitelisted([clientIp], whitelist)
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

        if (!(clientIp ==~ /^[0-9a-fA-F:.]+$/)) return false
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

