package com.sebasmalparqueado.cowsvsclown.e2e;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Support for authenticating the end-to-end tests <b>without a running
 * Keycloak</b>.
 *
 * <h2>The problem</h2>
 *
 * <p>The e2e tests start the whole application and hit it over a real socket.
 * With security in place, every {@code POST}, {@code PATCH} and {@code DELETE}
 * now needs an {@code Authorization: Bearer ...} header carrying a token signed
 * by Keycloak. But asking for a real token would mean starting a Keycloak
 * container for the test suite: slower, and impossible in CI as it stands.</p>
 *
 * <h2>The solution</h2>
 *
 * <p>Replace only the smallest possible piece: the {@link JwtDecoder}, which is
 * the object that opens the token and checks the signature. The stub below
 * accepts three known strings and, for each one, returns a decoded token with
 * the claims Keycloak would have produced.</p>
 *
 * <p>Everything after that step stays real: the filter chain,
 * {@code KeycloakRoleConverter} reading {@code realm_access.roles}, the rules in
 * {@code SecurityConfig}, and the 401/403 responses. What is taken on faith is
 * the cryptography — Spring Security's job, not this project's.</p>
 *
 * <p>Note the decoder is only replaced in the tests that explicitly
 * {@code @Import} this class. Everywhere else — the application, and any test
 * that does not import it — the real decoder is in use.</p>
 */
@TestConfiguration
public class E2EAuth {

    /** Token of a user with ADMIN and USER: can do everything, including DELETE. */
    public static final String ADMIN = "token-de-admin";

    /** Token of a user with USER only: can write, cannot delete records. */
    public static final String USER = "token-de-user";

    /** Token of an authenticated user with no roles: valid, but grants nothing. */
    public static final String SIN_ROLES = "token-sin-roles";

    /**
     * What each accepted token means. It mirrors the three users in
     * {@code keycloak/realm-cowsvsclown.json} (admin, user, curioso), so a test
     * that passes here describes a situation that can be reproduced by hand
     * against the real Keycloak.
     */
    private static final Map<String, List<String>> ROLES_POR_TOKEN = Map.of(
            ADMIN, List.of("ADMIN", "USER"),
            USER, List.of("USER"),
            SIN_ROLES, List.of());

    /**
     * Stub decoder. Any string that is not one of the three constants above is
     * rejected with {@link BadJwtException}, the same exception the real decoder
     * throws for a tampered token — which is what lets a test check that garbage
     * really does produce a 401.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            List<String> roles = ROLES_POR_TOKEN.get(token);
            if (roles == null) {
                throw new BadJwtException("Token de prueba desconocido: " + token);
            }
            Instant ahora = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .issuedAt(ahora)
                    .expiresAt(ahora.plus(5, ChronoUnit.MINUTES))
                    .subject("00000000-0000-0000-0000-000000000000")
                    .claim("preferred_username", nombreDe(token))
                    // The exact shape Keycloak uses. It matters: this is the
                    // claim KeycloakRoleConverter reads, and it is read for real.
                    .claim("realm_access", Map.of("roles", roles))
                    .build();
        };
    }

    private static String nombreDe(String token) {
        return token.replace("token-de-", "").replace("token-", "");
    }

    /**
     * Makes {@code rest} send the given token on <b>every</b> subsequent request.
     *
     * <p>An interceptor is used instead of adding the header call by call because
     * the e2e classes make dozens of requests, and threading a header through all
     * of them would bury what each test is actually about.</p>
     *
     * <p>Any previous token is removed first, so calling this twice in the same
     * test swaps identity instead of stacking two {@code Authorization}
     * headers.</p>
     */
    public static void autenticarComo(TestRestTemplate rest, String token) {
        List<ClientHttpRequestInterceptor> interceptors = rest.getRestTemplate().getInterceptors();
        interceptors.removeIf(BearerInterceptor.class::isInstance);
        interceptors.add(new BearerInterceptor(token));
    }

    /** Removes the token: the following requests travel anonymously. */
    public static void cerrarSesion(TestRestTemplate rest) {
        rest.getRestTemplate().getInterceptors().removeIf(BearerInterceptor.class::isInstance);
    }

    /** Adds {@code Authorization: Bearer <token>} to every outgoing request. */
    private record BearerInterceptor(String token) implements ClientHttpRequestInterceptor {

        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution)
                throws java.io.IOException {

            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return execution.execute(request, body);
        }
    }
}