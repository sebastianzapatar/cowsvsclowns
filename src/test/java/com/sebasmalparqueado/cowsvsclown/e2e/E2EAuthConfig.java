package com.sebasmalparqueado.cowsvsclown.e2e;

import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Login de mentira para los tests end to end.
 *
 * <p>Los E2E levantan la aplicación entera, así que las peticiones pasan por el
 * mismo filtro de seguridad que en producción. Lo que no hay es un Keycloak al
 * lado, y arrancar uno en cada build sería lentísimo. Se reemplazan entonces
 * dos piezas:</p>
 *
 * <ul>
 *   <li>El {@link JwtDecoder}: en vez de verificar la firma contra las llaves
 *       del realm, acepta los dos tokens de prueba ("admin" y "user") y arma un
 *       JWT con ese rol. Cualquier otro valor se rechaza, igual que rechazaría
 *       un token vencido o falsificado.</li>
 *   <li>El {@link org.springframework.boot.resttestclient.TestRestTemplate}: se
 *       le engancha un interceptor que manda "Authorization: Bearer admin" en
 *       toda petición que no traiga ya una cabecera propia. Así los tests que
 *       existían siguen funcionando sin tocarlos, y los que quieren probar otro
 *       rol solo tienen que poner la cabecera a mano.</li>
 * </ul>
 *
 * <p>Ojo: esto NO desactiva la seguridad. Las reglas de SecurityConfig se
 * siguen aplicando, y eso es justo lo que comprueba {@link SecurityE2ETest}.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class E2EAuthConfig {

    static final String ADMIN = "admin";
    static final String USER = "user";

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            if (!List.of(ADMIN, USER).contains(token)) {
                throw new BadJwtException("Token de prueba desconocido: " + token);
            }
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token)
                    .claim("realm_access", Map.of("roles", List.of(token)))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        };
    }

    @Bean
    RestTemplateCustomizer autenticarComoAdminPorDefecto() {
        return template -> template.getInterceptors().add((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(ADMIN);
            }
            return execution.execute(request, body);
        });
    }
}
