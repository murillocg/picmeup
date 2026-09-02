package com.picmeup.common.config;

import com.picmeup.common.user.UserAccessService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs once at sign-in, after Cognito has proven the identity, to decide what the person
 * may do. Authorities come from our {@code users} table — the token only says who they are.
 *
 * <p>The tokens are not kept. All the application needs is the verified email, and the
 * database is re-checked on every subsequent request, so there is nothing to store or
 * refresh.
 */
@Component
public class DatabaseRoleOidcUserService extends OidcUserService {

    private final UserAccessService userAccess;

    public DatabaseRoleOidcUserService(UserAccessService userAccess) {
        this.userAccess = userAccess;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        var user = userAccess.resolveOnSignIn(
                oidcUser.getEmail(),
                Boolean.TRUE.equals(oidcUser.getEmailVerified()),
                oidcUser.getSubject(),
                oidcUser.getFullName());

        // "email" as the name attribute so Authentication.getName() returns the address
        // every other lookup keys on — the users table, photo attribution, assignments.
        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "email");
    }
}
