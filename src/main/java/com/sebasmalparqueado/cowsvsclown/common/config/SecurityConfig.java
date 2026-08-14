package com.sebasmalparqueado.cowsvsclown.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Seguridad de la API.
 *
 * <p>Esta app no emite tokens ni guarda contraseñas: eso es trabajo de Keycloak.
 * Acá solo se valida el token que llega en la cabecera
 * {@code Authorization: Bearer ...} (firma, emisor y vencimiento) y se decide
 * qué puede hacer quien lo trae. Las URL del emisor y de sus llaves públicas
 * están en application.yml, bajo {@code spring.security.oauth2.resourceserver}.</p>
 *
 * <p>Los permisos son los mismos para los tres recursos (owners, cows, clowns):</p>
 * <ul>
 *   <li>GET      -> rol {@code user} o {@code admin}</li>
 *   <li>el resto -> solo {@code admin}</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Lo único que se puede ver sin token: la documentación, el healthcheck que
     * miran Docker y Render, y /error (si no está, un 401 se convierte en un 500
     * al intentar renderizar el error).
     */
    private static final String[] RUTAS_PUBLICAS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        return http
                // Sin sesión no hay cookie de sesión, y sin cookie no hay ataque
                // CSRF posible: cada petición se autentica sola con su token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("USER", "ADMIN")
                        // Todo lo que no sea GET bajo /api (POST, PUT, PATCH,
                        // DELETE) cae acá: escribir es cosa de admin.
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        // Regla de cierre. Sin ella, cualquier ruta que no esté
                        // en la lista de arriba (una nueva, /actuator/..., lo que
                        // sea) queda abierta sin que nadie se entere.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    /**
     * Traductor de roles. Keycloak los manda dentro del claim
     * {@code realm_access.roles} y en minúscula ("admin"); Spring los busca como
     * authorities con prefijo y en mayúscula ("ROLE_ADMIN"). Sin esta traducción
     * {@code hasRole(...)} nunca da verdadero y todo responde 403.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::rolesDelRealm);
        return converter;
    }

    private static Collection<GrantedAuthority> rolesDelRealm(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");

        // Un token sin realm_access es válido: es un usuario sin ningún rol
        // asignado. Se devuelve lista vacía y las reglas de acceso lo rechazan
        // solas con un 403, en vez de reventar con NullPointerException.
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(
                        "ROLE_" + rol.toString().toUpperCase(Locale.ROOT)))
                .toList();
    }
}
