package com.picmeup.common.config;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;
import java.util.Set;

@Controller
public class SpaForwardController implements ErrorController {

    /**
     * Extensions this site never serves. Vulnerability scanners probe thousands of these
     * looking for an exposed archive or config file — one such scan sent 28,000 requests
     * across 2,700 paths and exhausted the heap. Forwarding each to the single-page app
     * means rendering a whole document to answer a request that can only ever be a miss,
     * so they get a bare 404 instead.
     */
    private static final Set<String> PROBE_EXTENSIONS = Set.of(
            ".zip", ".tar", ".gz", ".tgz", ".rar", ".7z", ".bak", ".old", ".sql", ".db",
            ".env", ".ini", ".conf", ".cfg", ".log", ".php", ".asp", ".aspx", ".jsp",
            ".cgi", ".pl", ".sh", ".exe", ".dll");

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status != null && Integer.parseInt(status.toString()) == HttpStatus.NOT_FOUND.value()) {
            String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            if (path != null && !path.startsWith("/api/") && !path.startsWith("/actuator/")) {
                if (isProbe(path)) {
                    return ResponseEntity.notFound().build();
                }
                return new ModelAndView("forward:/index.html");
            }
        }
        return new ModelAndView("forward:/error-page");
    }

    private boolean isProbe(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) {
            lower = lower.substring(0, query);
        }
        final String candidate = lower;
        return PROBE_EXTENSIONS.stream().anyMatch(candidate::endsWith);
    }
}
