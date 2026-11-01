package com.binitech.fiscal.config;

import com.binitech.fiscal.application.ports.outbound.AuthenticationPort;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain security(HttpSecurity http, AuthenticationPort authentication)
      throws Exception {
    return http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health/**", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/fiscal/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e -> e.authenticationEntryPoint((request, response, ex) -> response.sendError(401)))
        .addFilterBefore(
            new SessionAuthenticationFilter(authentication),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${cors.allowed-origins}") String origins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(
        Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    config.setExposedHeaders(List.of("Content-Disposition"));
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
