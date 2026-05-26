package github.kaloyanov5.merkantil.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the client IP for an inbound request, honoring X-Forwarded-For
 * only when the immediate caller (request.getRemoteAddr()) is in the
 * configured trusted-proxy allowlist. With the default empty allowlist the
 * extractor returns the raw remote address and ignores forwarded headers,
 * so an attacker cannot spoof their IP by setting X-Forwarded-For.
 *
 * Configure the allowlist in application.yml under {@code app.trusted-proxies}
 * as a comma-separated list of proxy IPs once the deployment sits behind a
 * load balancer or reverse proxy.
 */
@Component
public class ClientIpExtractor {

    @Value("${app.trusted-proxies:}")
    private List<String> trustedProxies;

    public String extract(HttpServletRequest request) {
        if (request == null) return null;
        String remote = request.getRemoteAddr();
        if (remote == null) return null;

        if (trustedProxies != null && !trustedProxies.isEmpty() && trustedProxies.contains(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remote;
    }
}
