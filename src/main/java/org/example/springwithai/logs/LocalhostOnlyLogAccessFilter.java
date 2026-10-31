package org.example.springwithai.logs;

import java.io.IOException;
import java.net.InetAddress;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stopgap until the app has authentication: the log file, the loggers endpoint and the {@code /logs} page answer
 * only requests made on this machine to a local name. Everything else gets 403, rendered by {@code /error} with the
 * usual {@code ApiError} body. {@link LogAccessConfig} registers it for those paths only.
 * <p>
 * Two checks, both required:
 * <ul>
 * <li>The socket's remote address is a loopback address. This stays safe only while
 * {@code server.forward-headers-strategy} is unset; with it, the container would take the address from
 * {@code X-Forwarded-For}, which any client can send. Behind a reverse proxy on the same host every request looks
 * local, so this filter gives no protection there.</li>
 * <li>The {@code Host} the client asked for is {@code localhost} or a loopback address. This stops DNS rebinding: a web
 * page whose domain is re-pointed at {@code 127.0.0.1} sends its requests from this machine, but with its own domain
 * as {@code Host}.</li>
 * </ul>
 * Turned off with {@code app.logs.localhost-only=false} once authentication protects these paths.
 */
class LocalhostOnlyLogAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LocalhostOnlyLogAccessFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String address = request.getRemoteAddr();
        String host = request.getServerName();  // from the Host header, without the port
        if (isLoopback(address) && isLocalHostName(host)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Refused {} {} from {} for host {}: log access is limited to localhost", request.getMethod(),
                    LogPaths.printable(request.getRequestURI()), address, LogPaths.printable(String.valueOf(host)));
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    /** Parses the address as an IP literal only, so there is never a DNS lookup; anything else is not loopback. */
    static boolean isLoopback(String address) {
        try {
            return InetAddress.ofLiteral(address).isLoopbackAddress();
        } catch (IllegalArgumentException | NullPointerException notAnIpLiteral) {
            return false;
        }
    }

    /** {@code localhost} or a loopback IP literal; an IPv6 literal may keep its brackets ({@code [::1]}). */
    static boolean isLocalHostName(String serverName) {
        if (serverName == null) {
            return false;
        }
        String name = (serverName.startsWith("[") && serverName.endsWith("]"))
                ? serverName.substring(1, serverName.length() - 1)
                : serverName;
        return name.equalsIgnoreCase("localhost") || isLoopback(name);
    }
}
