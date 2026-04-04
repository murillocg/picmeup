package com.picmeup.common.config;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Hashed assets (JS, CSS) — cache for 1 year (immutable, content-hash in filename)
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
    }

    @Bean
    public FilterRegistrationBean<Filter> noCacheIndexHtmlFilter() {
        var registration = new FilterRegistrationBean<Filter>();
        registration.setFilter((request, response, chain) -> {
            chain.doFilter(request, response);
            var httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
            String path = httpRequest.getRequestURI();
            if (path.equals("/") || path.equals("/index.html")) {
                var httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
                httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            }
        });
        registration.addUrlPatterns("/", "/index.html");
        return registration;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
