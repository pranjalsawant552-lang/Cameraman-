package com.cameraman.common.config;

import com.cameraman.common.security.JwtAuthenticationFilter;
import com.cameraman.common.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // Explicit allow-list, not a wildcard — see app.cors.allowed-origins in
    // application.yml for why this changed from allowedOriginPatterns("*").
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    private static final String[] PUBLIC_URLS = {
            "/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/info",
            "/public/**",
            "/webhooks/**",
            // The actual Razorpay webhook route is /payments/webhooks/razorpay, which
            // the /webhooks/** pattern above does NOT match (that only matches paths
            // starting with /webhooks/). Razorpay's own servers have no JWT to send, so
            // without this the webhook would 401 before ever reaching the controller —
            // exactly the kind of "permitAll in name only" gap already found and fixed
            // once for /settings/public.
            "/payments/webhooks/**",
            "/cms/public/**",
            // WebSocket handshake only — the actual STOMP CONNECT frame is authenticated
            // by StompAuthChannelInterceptor since a browser can't attach a bearer header
            // to the handshake request itself. See WebSocketConfig for details.
            "/ws/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_URLS).permitAll()
                .requestMatchers(HttpMethod.GET, "/cms/**").permitAll()
                // Signed, expiring file links (originals held in Google Drive). Not open access:
                // the token is an HMAC the app issued after authorising the caller, verified in
                // StorageFileController; anything invalid/expired is a 404.
                .requestMatchers(HttpMethod.GET, "/files/*").permitAll()
                // GET /settings/public and /settings/public-info are explicitly the
                // safe-subset, no-auth endpoints for the public marketing site (see
                // SettingsController) — every other /settings/** route still requires
                // authentication via the anyRequest() rule below.
                .requestMatchers(HttpMethod.GET, "/settings/public", "/settings/public-info").permitAll()
                // The public Packages page lists what is on offer (name, price, inclusions),
                // so the *active* list is readable without a login. Every other /packages
                // route — including GET /packages and /packages/{id} — stays authenticated.
                .requestMatchers(HttpMethod.GET, "/packages/active").permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .frameOptions(fo -> fo.sameOrigin())
                .contentTypeOptions(ct -> {})
                .xssProtection(xss -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(pp -> pp.policy("camera=(), microphone=(), geolocation=()"))
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Patterns (not plain origins) so dev entries like http://localhost:* work when Vite hops to another
        // port; exact origins such as https://app.example.com still match exactly.
        config.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With",
                "Accept", "Origin", "X-Device-ID", "X-Client-Version"
        ));
        config.setExposedHeaders(Arrays.asList("Authorization", "X-Total-Count", "X-Total-Pages"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
