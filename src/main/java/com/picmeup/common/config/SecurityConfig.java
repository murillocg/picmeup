package com.picmeup.common.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.http.HttpStatus;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    /**
     * Whether cookies must carry the Secure flag. Not inferred from the request: behind
     * CloudFront the origin call is plain HTTP, so request.isSecure() is false even
     * though every viewer reaches the site over HTTPS. False locally, where dev runs
     * over http and a secure cookie would simply never be sent back.
     */
    @Value("${app.cookies.secure:false}")
    private boolean secureCookies;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                                   DatabaseRoleOidcUserService oidcUserService,
                                                   RevalidateUserFilter revalidateUserFilter,
                                                   JsonAuthenticationEntryPoint authenticationEntryPoint,
                                                   JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        // The SPA reads the XSRF-TOKEN cookie and echoes it back in the X-XSRF-TOKEN header,
        // so the token must be resolved eagerly and stored unmasked.
        var csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        var csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookies));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        // Login, plus the endpoints used by anonymous visitors and the processing
                        // lambda — none of them act on a browser session cookie.
                        .ignoringRequestMatchers(
                                "/api/auth/login",
                                "/api/internal/**",
                                "/api/orders/**",
                                "/api/events/*/passes/**",
                                "/api/events/*/search")
                )
                .cors(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/events/*/search").permitAll()
                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/error").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/check").permitAll()
                        // Photographers may upload to events they are assigned to; the
                        // assignment itself is checked in PhotoService, since a URL match
                        // cannot know who is assigned to what.
                        .requestMatchers(HttpMethod.POST, "/api/events/*/photos/**").hasAnyRole("ADMIN", "PHOTOGRAPHER")
                        .requestMatchers("/api/photographer/**").hasAnyRole("ADMIN", "PHOTOGRAPHER")
                        .requestMatchers(HttpMethod.POST, "/api/events/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/events/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/orders").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/passes").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpStatus.OK.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"authenticated\":false}");
                        })
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        // Cognito sign-in runs alongside the existing username/password login, which stays
        // until Phase 6 — that is the rollback path. Only wired when a client registration
        // exists, so dev and test need no Cognito at all.
        if (clientRegistrations.getIfAvailable() != null) {
            http
                    .oauth2Login(oauth2 -> oauth2
                            // Kept under /api because the Vite dev proxy forwards only that
                            // prefix; the defaults (/oauth2/authorization, /login/oauth2/code)
                            // would not reach the backend in local development.
                            .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/auth/authorize"))
                            .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/callback/*"))
                            .userInfoEndpoint(endpoint -> endpoint.oidcUserService(oidcUserService))
                            .defaultSuccessUrl("/", true)
                            // Renders AccessNotGrantedException as a 403 naming the address
                            // used — the "you signed in with the wrong account" case.
                            .failureHandler(authenticationEntryPoint::commence))
                    // Authority lives in the database, so it is re-read per request rather
                    // than trusted from the session for its whole lifetime.
                    .addFilterAfter(revalidateUserFilter, SecurityContextHolderFilter.class);
        }

        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        var admin = User.builder()
                .username(adminUsername)
                .password(adminPassword)
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
