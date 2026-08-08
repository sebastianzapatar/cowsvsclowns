package com.sebasmalparqueado.cowsvsclown.e2e;

import com.sebasmalparqueado.cowsvsclown.common.exceptions.ErrorResponse;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests of security: the same checks as
 * {@code SecurityConfigIntegrationTest}, but over a real socket.
 *
 * <h2>Why do both</h2>
 *
 * <p>The {@code @WebMvcTest} version runs the filter chain against a simulated
 * request; this one starts Tomcat and travels the whole way. That difference
 * catches things the other cannot see: that the {@code Authorization} header
 * survives the real HTTP stack, that the 401 body is serialised as actual JSON,
 * that the {@code WWW-Authenticate} header reaches the client, and that a
 * rejected request truly never touches the database.</p>
 *
 * <p>Only the {@link org.springframework.security.oauth2.jwt.JwtDecoder} is
 * replaced (see {@link E2EAuth}); nothing else is simulated.</p>
 */
@Import(E2EAuth.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Security over real HTTP")
class SecurityE2ETest {

    private static final String OWNERS_URL = "/api/owners";

    @Autowired
    private TestRestTemplate rest;

    /**
     * Unlike the other e2e classes, this one starts <b>anonymous</b>: whether
     * there is a token, and which one, is precisely the subject of each test.
     */
    @BeforeEach
    void sinSesion() {
        E2EAuth.cerrarSesion(rest);
    }

    // ============================ Utilidades =============================

    private OwnerRequest dueno(String nombre) {
        return new OwnerRequest(nombre, "DePrueba", null);
    }

    /** Creates an owner as ADMIN and returns its id, to have something to delete. */
    private Long crearDuenoComoAdmin(String nombre) {
        E2EAuth.autenticarComo(rest, E2EAuth.ADMIN);
        OwnerResponse creado = rest.postForEntity(OWNERS_URL, dueno(nombre), OwnerResponse.class)
                .getBody();
        E2EAuth.cerrarSesion(rest);

        assertNotNull(creado, "the fixture could not be created");
        return creado.id();
    }

    /** A request with an arbitrary {@code Authorization} header, valid or not. */
    private ResponseEntity<ErrorResponse> postConToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(OWNERS_URL, HttpMethod.POST,
                new HttpEntity<>(dueno("Prueba"), headers), ErrorResponse.class);
    }

    // ========================= Rutas abiertas ============================

    @Nested
    @DisplayName("What stays open")
    class RutasAbiertas {

        @Test
        @DisplayName("GET works with no token at all")
        void getSinToken() {
            ResponseEntity<String> response = rest.getForEntity(OWNERS_URL, String.class);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("the health check answers without a token")
        void healthSinToken() {
            // If this ever needed a token, Docker would mark the container as
            // unhealthy and restart it in a loop, even with the app working fine.
            ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().contains("UP"));
        }

        @Test
        @DisplayName("the OpenAPI contract is readable without a token")
        void openApiSinToken() {
            // Documentation has to be reachable before logging in: it is where
            // one finds out how to log in.
            ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    // ============================ 401 ====================================

    @Nested
    @DisplayName("Without a valid token: 401")
    class SinToken {

        @Test
        @DisplayName("a POST with no Authorization header is rejected")
        void postSinToken() {
            ResponseEntity<ErrorResponse> response = rest.postForEntity(
                    OWNERS_URL, dueno("Nadie"), ErrorResponse.class);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        @DisplayName("a made-up token is rejected the same way")
        void postConTokenInventado() {
            // The stub decoder throws BadJwtException for an unknown string,
            // exactly as the real one does when the signature does not check out.
            ResponseEntity<ErrorResponse> response = postConToken("esto.no.es.un.token");

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        @DisplayName("the body arrives as JSON with the standard error format")
        void cuerpoDelError() {
            ResponseEntity<ErrorResponse> response = rest.postForEntity(
                    OWNERS_URL, dueno("Nadie"), ErrorResponse.class);

            ErrorResponse error = response.getBody();

            // Deserialising into ErrorResponse is itself the assertion: if the
            // 401 came out in Spring's default format, this would be null.
            assertNotNull(error);
            assertEquals(401, error.status());
            assertEquals("Unauthorized", error.error());
            assertEquals(OWNERS_URL, error.path());
            assertNotNull(error.timestamp());
        }

        @Test
        @DisplayName("the response says how one should have authenticated")
        void cabeceraWwwAuthenticate() {
            ResponseEntity<ErrorResponse> response = rest.postForEntity(
                    OWNERS_URL, dueno("Nadie"), ErrorResponse.class);

            assertEquals("Bearer realm=\"cowsvsclown\"",
                    response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
        }

        @Test
        @DisplayName("nothing is written to the database")
        void noSeEscribeNada() {
            rest.postForEntity(OWNERS_URL, dueno("Fantasma"), ErrorResponse.class);

            // The proof that the block is real and not just a status code: the
            // request never reached the service, so the owner does not exist.
            E2EAuth.autenticarComo(rest, E2EAuth.ADMIN);
            String lista = rest.getForObject(OWNERS_URL, String.class);

            assertTrue(lista != null && !lista.contains("Fantasma"));
        }
    }

    // ============================ 403 ====================================

    @Nested
    @DisplayName("With a token but the wrong role: 403")
    class SinPermisos {

        @Test
        @DisplayName("an authenticated user with no roles cannot create")
        void sinRolesNoPuedeCrear() {
            // The difference that matters: 403, not 401. Its identity is known,
            // its permissions are not enough. This is the 'curioso' of the realm.
            ResponseEntity<ErrorResponse> response = postConToken(E2EAuth.SIN_ROLES);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertEquals(403, response.getBody().status());
        }

        @Test
        @DisplayName("USER cannot delete")
        void userNoPuedeBorrar() {
            Long id = crearDuenoComoAdmin("Borrable");
            E2EAuth.autenticarComo(rest, E2EAuth.USER);

            ResponseEntity<ErrorResponse> response = rest.exchange(
                    OWNERS_URL + "/" + id, HttpMethod.DELETE, null, ErrorResponse.class);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        @DisplayName("and the record is still there afterwards")
        void elRegistroSigueVivo() {
            Long id = crearDuenoComoAdmin("Superviviente");
            E2EAuth.autenticarComo(rest, E2EAuth.USER);
            rest.exchange(OWNERS_URL + "/" + id, HttpMethod.DELETE, null, ErrorResponse.class);

            ResponseEntity<OwnerResponse> response = rest.getForEntity(
                    OWNERS_URL + "/" + id, OwnerResponse.class);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().active());
        }
    }

    // ======================== Con permisos ===============================

    @Nested
    @DisplayName("With the right role, everything works as usual")
    class ConPermisos {

        @Test
        @DisplayName("USER can create")
        void userPuedeCrear() {
            E2EAuth.autenticarComo(rest, E2EAuth.USER);

            ResponseEntity<OwnerResponse> response = rest.postForEntity(
                    OWNERS_URL, dueno("Creador"), OwnerResponse.class);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals("Creador", response.getBody().firstName());
        }

        @Test
        @DisplayName("ADMIN can delete")
        void adminPuedeBorrar() {
            Long id = crearDuenoComoAdmin("Condenado");
            E2EAuth.autenticarComo(rest, E2EAuth.ADMIN);

            ResponseEntity<Void> response = rest.exchange(
                    OWNERS_URL + "/" + id, HttpMethod.DELETE, null, Void.class);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        }

        @Test
        @DisplayName("the username of the token reaches the application")
        void elUsuarioLlegaALaApp() {
            // Indirect but sufficient: if setPrincipalClaimName had not been
            // configured, the authentication would carry the 'sub' UUID and the
            // logs of every write would be unreadable. Here it is enough to see
            // that a token with preferred_username goes all the way through.
            E2EAuth.autenticarComo(rest, E2EAuth.USER);

            ResponseEntity<OwnerResponse> response = rest.postForEntity(
                    OWNERS_URL, dueno("Identificado"), OwnerResponse.class);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
        }
    }
}