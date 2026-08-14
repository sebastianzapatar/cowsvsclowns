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
 *   <li>Interfaz visual: <a href="http://localhost:8080/swagger-ui.html">/swagger-ui.html</a></li>
 *   <li>JSON del contrato: <a href="http://localhost:8080/v3/api-docs">/v3/api-docs</a></li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    /** Nombre interno del esquema de seguridad; aparece en el botón Authorize. */
    private static final String ESQUEMA_KEYCLOAK = "keycloak";

    /** Puerto real de la app, para que el servidor de ejemplo no quede fijo en 8080. */
    @Value("${server.port:8080}")
    private String serverPort;

    /** Dirección de Keycloak tal como la ve el navegador (no la interna de Docker). */
    @Value("${keycloak.public-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Bean
    public OpenAPI cowsVsClownsOpenAPI() {
        String openidConnect = keycloakUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect";

        return new OpenAPI()
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
                                """)
                        .contact(new Contact()
                                .name("Sebastián Zapata")
                                .email("pansezapata@gmail.com"))
                        .license(new License().name("Uso académico")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Entorno local")))
                // Con esto Swagger muestra el botón "Authorize": abre el login de
                // Keycloak, vuelve con el token y lo manda en todas las llamadas.
                // Sin esto la documentación sigue viéndose, pero cada "Try it out"
                // responde 401 porque va sin cabecera Authorization.
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_KEYCLOAK, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("""
                                        Usuarios de prueba del realm: admin/admin123 (todo)
                                        y user/user123 (solo GET).""")
                                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                        .authorizationUrl(openidConnect + "/auth")
                                        .tokenUrl(openidConnect + "/token")
                                        .scopes(new Scopes())))))
                // Aplica el esquema a todos los endpoints. Los públicos (la propia
                // documentación) no están en el contrato, así que no molesta.
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_KEYCLOAK));
    }
}
