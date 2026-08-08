package com.sebasmalparqueado.cowsvsclown.common.config;

import com.sebasmalparqueado.cowsvsclown.common.security.KeycloakRoleConverter;
import com.sebasmalparqueado.cowsvsclown.common.security.RestAccessDeniedHandler;
import com.sebasmalparqueado.cowsvsclown.common.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Configuración de seguridad: quién puede llamar a qué.
 *
 * <h2>El modelo en una frase</h2>
 *
 * <p>La API es un <b>resource server</b>: no tiene login, ni formulario, ni
 * tabla de usuarios, ni contraseñas. De eso se encarga Keycloak. Acá solo se
 * recibe un <i>access token</i> (un JWT) en la cabecera
 * {@code Authorization: Bearer &lt;token&gt;}, se verifica que sea auténtico y se
 * decide, según los roles que trae adentro, si la operación se permite.</p>
 *
 * <h2>El recorrido completo de una petición</h2>
 *
 * <pre>
 *  1. El usuario se loguea en Keycloak (usuario y contraseña) y recibe un JWT.
 *
 *  2. Llama a la API mandando ese JWT:
 *        POST /api/cows
 *        Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
 *
 *  3. Spring Security valida el token, SIN preguntarle nada a Keycloak en cada
 *     petición. Le alcanza con la llave pública del realm, que descarga una vez
 *     de la URL configurada en jwk-set-uri y deja cacheada. Comprueba:
 *        - que la FIRMA corresponda a esa llave  -> el token no fue fabricado
 *        - que no esté vencido  (claim exp)
 *        - que lo haya emitido el realm esperado (claim iss = issuer-uri)
 *
 *  4. {@link KeycloakRoleConverter} saca los roles del token y los convierte en
 *     autoridades ROLE_ADMIN / ROLE_USER.
 *
 *  5. Se aplican las reglas de authorizeHttpRequests de más abajo:
 *        pasa      -> el controlador se ejecuta normalmente
 *        sin token -> 401 ({@link RestAuthenticationEntryPoint})
 *        sin rol   -> 403 ({@link RestAccessDeniedHandler})
 * </pre>
 *
 * <p>El punto 3 es la razón de usar JWT: la API no depende de que Keycloak esté
 * disponible para atender cada petición, y no hay una llamada de red extra por
 * request. El precio es que un token sigue siendo válido hasta que vence, aunque
 * al usuario lo hayan deshabilitado mientras tanto; por eso los tokens de acceso
 * son de vida corta (5 minutos en el realm de este proyecto).</p>
 *
 * <h2>Tabla de permisos</h2>
 *
 * <pre>
 *  MÉTODO   RUTA                            QUIÉN PUEDE
 *  ------   ------------------------------  --------------------------------
 *  GET      /api/**                         cualquiera, sin token
 *  POST     /api/**                         USER o ADMIN
 *  PATCH    /api/**                         USER o ADMIN
 *  PUT      /api/**                         USER o ADMIN
 *  DELETE   /api/clowns/{id}/cows/{cowId}   USER o ADMIN  (quita un vínculo)
 *  DELETE   /api/**                         solo ADMIN    (baja lógica)
 *  GET      /swagger-ui/**, /v3/api-docs/** cualquiera
 *  GET      /actuator/health                cualquiera
 * </pre>
 *
 * <p>La lectura queda abierta a propósito: lo que se pidió proteger son las
 * operaciones que <b>modifican</b> datos. Si algún día hay que cerrar también la
 * consulta, se borra la línea del GET y {@code anyRequest().authenticated()} se
 * encarga del resto.</p>
 *
 * <p>Los dos DELETE están separados porque no son la misma operación: quitarle
 * una vaca a un payaso solo rompe un vínculo de la tabla intermedia y se
 * deshace volviendo a asignarla, mientras que borrar una vaca, un payaso o un
 * dueño da de baja el registro entero. Por eso lo segundo pide ADMIN.</p>
 */
@Configuration
@EnableWebSecurity
// Habilita @PreAuthorize / @PostAuthorize por si en el futuro hace falta una
// regla que dependa de los datos y no solo de la URL (por ejemplo "solo el
// dueño puede editar SUS vacas"), algo que no se puede expresar acá.
// Hoy no se usa: las reglas viven todas en este archivo, en un solo lugar.
@EnableMethodSecurity
public class SecurityConfig {

    /** Rutas de Swagger y del contrato OpenAPI: documentación, siempre abierta. */
    private static final String[] RUTAS_PUBLICAS_DOCUMENTACION = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    /**
     * Rutas de infraestructura abiertas.
     *
     * <p>{@code /actuator/health} lo consulta el healthcheck del compose y
     * cualquier PaaS para saber si la app está viva: si pidiera token, el
     * contenedor se marcaría como caído. Solo devuelve {@code {"status":"UP"}},
     * sin detalles (ver {@code management.endpoint.health.show-details} en
     * application.yml).</p>
     *
     * <p>{@code /error} es la ruta interna a la que Spring reenvía cuando algo
     * falla. Si estuviera protegida, un error dentro de una petición sin token
     * se convertiría en un 401 sobre otro 401, y el cliente recibiría una
     * respuesta vacía en vez del mensaje real.</p>
     */
    private static final String[] RUTAS_PUBLICAS_INFRAESTRUCTURA = {
            "/actuator/health",
            "/actuator/health/**",
            "/error"
    };

    /** Roles que pueden escribir. Se repiten en varias reglas, así que se nombran una vez. */
    private static final String ROL_USUARIO = "USER";
    private static final String ROL_ADMIN = "ADMIN";

    /**
     * clientId registrado en Keycloak. Se usa para leer los roles específicos de
     * este cliente dentro del token ({@code resource_access.<clientId>.roles}).
     */
    @Value("${keycloak.client-id}")
    private String clientId;

    /**
     * La cadena de filtros por la que pasa toda petición antes de llegar a un
     * controlador.
     *
     * @param resolver el resolvedor de excepciones de Spring MVC. Se inyecta por
     *                 nombre con {@code @Qualifier} porque hay varios beans de
     *                 ese tipo y necesitamos justo el compuesto, que es el que
     *                 conoce al {@code GlobalExceptionHandler}.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {

        AuthenticationEntryPoint sinToken = new RestAuthenticationEntryPoint(resolver);
        AccessDeniedHandler sinPermiso = new RestAccessDeniedHandler(resolver);

        http
                // ------------------------------------------------------------
                // CSRF: desactivado, y no es un atajo
                // ------------------------------------------------------------
                // El ataque CSRF consiste en que otro sitio haga que TU navegador
                // mande una petición aprovechando una cookie de sesión que viaja
                // sola. Acá no hay cookies ni sesión: la credencial es un token
                // que el cliente tiene que poner a mano en una cabecera, y ningún
                // sitio ajeno puede leerlo ni hacer que se envíe. Sin ese vector,
                // la protección CSRF no aporta nada y solo rompería los POST.
                .csrf(AbstractHttpConfigurer::disable)

                // Sin login por formulario ni httpBasic: las dos formas de
                // autenticación que Spring habilitaría por su cuenta. La única
                // credencial aceptada es el Bearer token.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // ------------------------------------------------------------
                // Sin estado
                // ------------------------------------------------------------
                // STATELESS = no se crea HttpSession ni se guarda nada del usuario
                // entre peticiones. Cada request se autentica sola con su token.
                // Esto es lo que permite correr varias instancias de la API detrás
                // de un balanceador sin necesidad de sesiones compartidas.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ------------------------------------------------------------
                // Las reglas
                // ------------------------------------------------------------
                // IMPORTANTE: se evalúan EN ORDEN y gana la primera que coincide.
                // Por eso lo específico va antes que lo genérico: si el
                // "DELETE /api/**" estuviera primero, la regla del vínculo
                // payaso-vaca nunca se alcanzaría.
                .authorizeHttpRequests(auth -> auth

                        // -------- Abierto a todo el mundo --------
                        .requestMatchers(RUTAS_PUBLICAS_DOCUMENTACION).permitAll()
                        .requestMatchers(RUTAS_PUBLICAS_INFRAESTRUCTURA).permitAll()

                        // Consultar es público. Para cerrarlo, borrar esta línea.
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()

                        // Necesario para que el navegador pueda hacer el
                        // "preflight" de CORS: manda un OPTIONS sin cabecera
                        // Authorization, así que si pidiera token fallaría antes
                        // de intentar la petición real.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // -------- Vínculos payaso <-> vaca (relación N a M) --------
                        // Va antes que la regla general de DELETE.
                        .requestMatchers(HttpMethod.POST, "/api/clowns/*/cows/*")
                        .hasAnyRole(ROL_USUARIO, ROL_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/clowns/*/cows/*")
                        .hasAnyRole(ROL_USUARIO, ROL_ADMIN)

                        // -------- Crear y modificar: USER o ADMIN --------
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole(ROL_USUARIO, ROL_ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyRole(ROL_USUARIO, ROL_ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole(ROL_USUARIO, ROL_ADMIN)

                        // -------- Dar de baja un registro: solo ADMIN --------
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole(ROL_ADMIN)

                        // -------- Todo lo demás --------
                        // Cualquier ruta que no esté nombrada arriba exige, como
                        // mínimo, un token válido. Es el "cerrado por defecto":
                        // si mañana se agrega un endpoint nuevo y nadie toca este
                        // archivo, queda protegido, no abierto.
                        .anyRequest().authenticated())

                // ------------------------------------------------------------
                // Validación del token
                // ------------------------------------------------------------
                // El decodificador (quién firma, dónde están las llaves) NO se
                // construye acá: lo arma Spring Boot solo a partir de las
                // propiedades spring.security.oauth2.resourceserver.jwt.* de
                // application.yml. Lo único que hay que aportar es cómo leer los
                // roles, porque el formato de Keycloak no es el estándar.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // Token ausente, vencido, mal firmado o de otro emisor.
                        .authenticationEntryPoint(sinToken)
                        .accessDeniedHandler(sinPermiso))

                // Los mismos manejadores para los rechazos que ocurren fuera del
                // filtro de Bearer (por ejemplo una petición sin cabecera
                // Authorization, que nunca llega a ese filtro).
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(sinToken)
                        .accessDeniedHandler(sinPermiso));

        return http.build();
    }

    /**
     * Une las dos cosas que Spring saca del token: <b>quién</b> es el usuario y
     * <b>qué</b> puede hacer.
     *
     * <ul>
     *   <li>Los roles los traduce {@link KeycloakRoleConverter}.</li>
     *   <li>El nombre se toma de {@code preferred_username} en vez del claim
     *       {@code sub} que se usa por defecto. Sin esto,
     *       {@code authentication.getName()} devolvería un UUID como
     *       {@code 8f14e45f-ceea-...} y los logs serían ilegibles; con esto
     *       devuelve {@code admin}.</li>
     * </ul>
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter(clientId));
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }
}