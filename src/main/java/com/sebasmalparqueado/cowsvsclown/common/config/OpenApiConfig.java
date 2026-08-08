package com.sebasmalparqueado.cowsvsclown.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Portada de la documentación de la API.
 *
 * <p>Springdoc arma solo el resto del OpenAPI leyendo los controllers y los DTO
 * (por eso hay {@code @Operation} y {@code @Schema} repartidos por el código).
 * Este bean solo agrega los datos generales, que no se pueden deducir.</p>
 *
 * <p>Dónde queda la documentación una vez que arranca la app:</p>
 * <ul>
 *   <li>Interfaz visual: <a href="http://localhost:8080/
 *
 *
 *   ">/swagger-ui.html</a></li>
 *   <li>JSON del contrato: <a href="http://localhost:8080/v3/api-docs">/v3/api-docs</a></li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    /** Nombre interno del esquema OAuth2; es la etiqueta que muestra Swagger. */
    private static final String ESQUEMA_OAUTH2 = "keycloak";

    /** Nombre interno del esquema "pegá el token a mano". */
    private static final String ESQUEMA_BEARER = "bearerAuth";

    /** Puerto real de la app, para que el servidor de ejemplo no quede fijo en 8080. */
    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * URL pública del realm, la misma que valida el resource server.
     *
     * <p>Tiene que ser la que ve <b>el navegador</b> ({@code localhost:8081}) y no
     * la de la red interna de Docker ({@code keycloak:8080}): quien va a abrir la
     * pantalla de login de Keycloak es el navegador de quien usa Swagger, no el
     * contenedor de la app.</p>
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public OpenAPI cowsVsClownsOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_OAUTH2, esquemaOAuth2())
                        .addSecuritySchemes(ESQUEMA_BEARER, esquemaBearer()))
                // Aplica los esquemas a TODAS las operaciones. No significa que
                // todas exijan token (los GET son públicos), significa que Swagger
                // adjunta la cabecera Authorization en todas cuando hay sesión
                // iniciada, que es lo cómodo al probar.
                .addSecurityItem(new SecurityRequirement()
                        .addList(ESQUEMA_OAUTH2)
                        .addList(ESQUEMA_BEARER))
                .info(new Info()
                        .title("Cows vs Clowns API")
                        .version("0.0.1-SNAPSHOT")
                        .description("""
                                API de ejemplo con las dos relaciones clásicas de JPA:

                                * **1 a N** — un dueño (`Owner`) tiene muchas vacas (`Cow`).
                                  La llave foránea `owner_id` vive en la tabla `cows`.
                                * **N a M** — un payaso (`Clown`) cuida muchas vacas y una
                                  vaca puede ser cuidada por varios payasos. Se resuelve con
                                  la tabla intermedia `clown_cow`.

                                Ningún registro se borra de verdad: todo es baja lógica
                                (`active = false`), salvo las filas de `clown_cow`, que solo
                                representan un vínculo.

                                ---

                                ### Autenticación

                                Consultar (`GET`) es público. Para **crear, modificar o borrar**
                                hace falta un token de Keycloak: botón **Authorize** 🔓 arriba
                                a la derecha.

                                | Operación | Rol necesario |
                                |---|---|
                                | `GET` | ninguno |
                                | `POST`, `PATCH`, `PUT` | `USER` o `ADMIN` |
                                | `DELETE` de un recurso | `ADMIN` |
                                | `DELETE` de un vínculo payaso–vaca | `USER` o `ADMIN` |

                                Usuarios de prueba del realm: `admin` / `admin123` (ADMIN + USER)
                                y `user` / `user123` (solo USER).
                                """)
                        .contact(new Contact()
                                .name("Sebastián Zapata")
                                .email("pansezapata@gmail.com"))
                        .license(new License().name("Uso académico")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Entorno local")));
    }

    /**
     * Esquema OAuth2 con <i>Authorization Code + PKCE</i>: el botón "Authorize"
     * te manda a la pantalla de login de Keycloak, te logueás ahí y Swagger
     * recibe el token solo. Es el flujo recomendado para aplicaciones que corren
     * en el navegador.
     *
     * <p>PKCE ("pixie") es lo que hace seguro este flujo en un cliente público
     * (uno sin client secret, como Swagger o cualquier SPA): el cliente inventa
     * un secreto de un solo uso, manda su hash al pedir el código y el original
     * al canjearlo. Sin PKCE, alguien que interceptara el código de la URL de
     * redirección podría canjearlo por un token.</p>
     *
     * <p>Se activa desde application.yml, con
     * {@code springdoc.swagger-ui.oauth.use-pkce-with-authorization-code-grant}.
     * Para que el redirect funcione, la URL
     * {@code http://localhost:8080/swagger-ui/oauth2-redirect.html} tiene que
     * estar entre los "Valid redirect URIs" del cliente en Keycloak: ya viene
     * registrada en {@code keycloak/realm-cowsvsclown.json}.</p>
     */
    private SecurityScheme esquemaOAuth2() {
        String base = issuerUri + "/protocol/openid-connect";

        return new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("Login contra Keycloak. Al autorizar se abre la pantalla "
                        + "de Keycloak y el token vuelve solo.")
                .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                                .authorizationUrl(base + "/auth")
                                .tokenUrl(base + "/token")
                                .refreshUrl(base + "/token")
                                .scopes(new Scopes()
                                        .addString("openid", "Identifica al usuario (obligatorio en OIDC)")
                                        .addString("profile", "Nombre de usuario y datos básicos")
                                        .addString("email", "Correo del usuario"))));
    }

    /**
     * Esquema alternativo: pegar el token a mano.
     *
     * <p>Sirve cuando el token se sacó por fuera del navegador, por ejemplo con
     * el {@code curl} al endpoint {@code /token} que está documentado en el
     * README. Es el plan B si el redirect de OAuth2 no funciona (típicamente
     * porque la redirect URI no coincide).</p>
     *
     * <p>Ojo: en el campo de Swagger va <b>solo el token</b>, sin escribir
     * "Bearer" delante; esa palabra la agrega Swagger.</p>
     */
    private SecurityScheme esquemaBearer() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Pegá acá el access_token que devuelve Keycloak "
                        + "(sin la palabra 'Bearer').");
    }
}
