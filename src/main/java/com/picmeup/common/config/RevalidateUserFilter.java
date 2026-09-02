package com.picmeup.common.config;

import com.picmeup.common.user.UserAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Re-checks the database on every request from a signed-in Cognito user.
 *
 * <p>Without this, authorities are captured at sign-in and cached in the session, so
 * revoking someone would not take effect until their session expired — up to twelve
 * hours of continued access after being disabled. Re-reading the {@code users} row keeps
 * the database authoritative, which is the whole premise of the design.
 *
 * <p>Costs one indexed lookup per request. The session-password admin is skipped: it has
 * no {@code users} row, and that mechanism disappears in Phase 6.
 */
@Component
public class RevalidateUserFilter extends OncePerRequestFilter {

    private final UserAccessService userAccess;
    private final JsonAuthenticationEntryPoint entryPoint;

    public RevalidateUserFilter(UserAccessService userAccess, JsonAuthenticationEntryPoint entryPoint) {
        this.userAccess = userAccess;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof OidcUser) {
            try {
                userAccess.requireStillActive(authentication.getName());
            } catch (AccessNotGrantedException denied) {
                // Drop the session rather than leaving a revoked person holding one that
                // fails on every request.
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                entryPoint.commence(request, response, denied);
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
