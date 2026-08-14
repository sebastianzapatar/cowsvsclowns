package com.sebasmalparqueado.cowsvsclown.e2e;

import com.sebasmalparqueado.cowsvsclown.owner.dto.OwnerRequest;
import org.junit.jupiter.api.DisplayName;
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

/**
 * Comprueba las reglas de SecurityConfig contra la aplicación levantada de
 * verdad. Es la diferencia entre "la configuración compila" y "la configuración
 * hace lo que dice": los tres casos de abajo son los que fallarían si alguien
 * borra una regla o cambia hasAnyRole por hasRole sin querer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(E2EAuthConfig.class)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityE2ETest {

    @Autowired
    private TestRestTemplate rest;

    private static final String OWNERS_URL = "/api/owners";

    /** Petición con el token que se le indique, en vez del "admin" por defecto. */
    private <T> ResponseEntity<T> como(String token, HttpMethod metodo, Object cuerpo, Class<T> respuesta) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(OWNERS_URL, metodo, new HttpEntity<>(cuerpo, headers), respuesta);
    }

    @Test
    @DisplayName("GET /api/owners — 401: token inválido")
    void tokenInvalido_devuelve401() {
        ResponseEntity<String> response = como("caducado", HttpMethod.GET, null, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/owners — 200: el rol user puede leer")
    void rolUser_puedeLeer() {
        ResponseEntity<String> response = como("user", HttpMethod.GET, null, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("POST /api/owners — 403: el rol user no puede escribir")
    void rolUser_noPuedeEscribir() {
        OwnerRequest nuevo = new OwnerRequest("Sin", "Permiso", null);

        ResponseEntity<String> response = como("user", HttpMethod.POST, nuevo, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /v3/api-docs — 200: la documentación es pública")
    void documentacion_esPublica() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
