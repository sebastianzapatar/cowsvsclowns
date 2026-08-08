package com.sebasmalparqueado.cowsvsclown.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Traduce los roles que vienen dentro del JWT de Keycloak a los
 * {@link GrantedAuthority} que entiende Spring Security.
 *
 * <h2>Por qué hace falta esta clase</h2>
 *
 * <p>Spring Security, por defecto, NO sabe leer roles de Keycloak. Su conversor
 * de fábrica ({@link JwtGrantedAuthoritiesConverter}) solo mira el claim
 * {@code scope} / {@code scp} y genera autoridades del estilo
 * {@code SCOPE_profile}, {@code SCOPE_email}. Eso alcanza para OAuth2 "puro",
 * pero los roles de Keycloak viven en otro lado del token y con otra forma.</p>
 *
 * <p>Un access token de Keycloak, decodificado, se ve así (recortado):</p>
 *
 * <pre>{@code
 * {
 *   "iss": "http://localhost:8081/realms/cowsvsclown",
 *   "sub": "3f2a...",
 *   "preferred_username": "admin",
 *   "realm_access": {
 *     "roles": ["ADMIN", "USER", "offline_access"]        <-- roles del REALM
 *   },
 *   "resource_access": {
 *     "cowsvsclown-api": {
 *       "roles": ["reportes"]                             <-- roles del CLIENTE
 *     }
 *   },
 *   "scope": "openid profile email"
 * }
 * }</pre>
 *
 * <p>Sin este conversor, un usuario con el rol ADMIN llegaría a la aplicación
 * autenticado pero <b>sin ninguna autoridad</b>, y cualquier regla
 * {@code hasRole("ADMIN")} lo rechazaría con 403. El síntoma clásico es
 * "el token es válido pero siempre me da 403".</p>
 *
 * <h2>Qué produce</h2>
 *
 * <p>De cada rol {@code X} genera la autoridad {@code ROLE_X}. El prefijo
 * {@code ROLE_} no es decorativo: {@code hasRole("ADMIN")} se traduce
 * internamente a "¿tiene la autoridad {@code ROLE_ADMIN}?". Si no se pone el
 * prefijo hay que usar {@code hasAuthority("ADMIN")} en su lugar; acá se eligió
 * el prefijo porque es la convención de Spring y permite escribir las reglas de
 * {@code SecurityConfig} con {@code hasRole}.</p>
 *
 * <p>Los nombres se pasan a mayúsculas para que un rol creado en Keycloak como
 * {@code admin} funcione igual que uno creado como {@code ADMIN}.</p>
 *
 * <p>Además de los roles, conserva las autoridades {@code SCOPE_*} del conversor
 * estándar, por si en el futuro se quiere autorizar por scope y no por rol.</p>
 *
 * <p>Nota: Keycloak agrega roles propios que no creamos nosotros
 * ({@code offline_access}, {@code uma_authorization},
 * {@code default-roles-cowsvsclown}). No se filtran: son inofensivos, ninguna
 * regla los menciona, y filtrarlos escondería información real del token.</p>
 *
 * <p>Alternativa sin código: Spring Boot 4 permite mapear el claim por
 * propiedades, con
 * {@code spring.security.oauth2.resourceserver.jwt.authorities-claim-expressions}.
 * Se dejó la clase explícita porque además junta roles de realm y de cliente y
 * es lo que se puede leer y testear (ver {@code KeycloakRoleConverterTest}).</p>
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /** Claim donde Keycloak guarda los roles del realm (los globales). */
    private static final String REALM_ACCESS = "realm_access";

    /** Claim donde guarda los roles definidos dentro de cada cliente. */
    private static final String RESOURCE_ACCESS = "resource_access";

    /** Dentro de los dos claims anteriores, la lista siempre se llama "roles". */
    private static final String ROLES = "roles";

    /** Prefijo que Spring Security espera para que hasRole(...) funcione. */
    private static final String ROLE_PREFIX = "ROLE_";

    /** Conversor de fábrica: aporta las autoridades SCOPE_*. */
    private final JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

    /**
     * clientId del que se leen los roles de {@code resource_access}.
     * Es el mismo que se configura en {@code keycloak.client-id}.
     */
    private final String clientId;

    public KeycloakRoleConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // LinkedHashSet: sin duplicados (un rol puede estar en el realm y en el
        // cliente a la vez) y conservando el orden, que hace más legible el log.
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopesConverter.convert(jwt));

        realmRoles(jwt).forEach(role -> authorities.add(toAuthority(role)));
        clientRoles(jwt).forEach(role -> authorities.add(toAuthority(role)));

        return authorities;
    }

    /** Roles del realm: {@code realm_access.roles}. */
    private List<String> realmRoles(Jwt jwt) {
        return rolesOf(jwt.getClaimAsMap(REALM_ACCESS));
    }

    /**
     * Roles del cliente: {@code resource_access.<clientId>.roles}.
     *
     * <p>Solo se leen los del cliente configurado. Los de otros clientes del
     * mismo realm no aplican a esta API, aunque viajen en el token.</p>
     */
    private List<String> clientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS);
        if (resourceAccess == null) {
            return List.of();
        }
        // El valor de cada cliente es a su vez un mapa {"roles": [...]}.
        return resourceAccess.get(clientId) instanceof Map<?, ?> client
                ? rolesOf(client)
                : List.of();
    }

    /**
     * Saca la lista "roles" de un mapa del token.
     *
     * <p>Todo está lleno de comprobaciones porque el JWT es una estructura
     * externa: si el claim falta o llega con otra forma, esto tiene que devolver
     * una lista vacía, no reventar con una excepción en mitad de un filtro.</p>
     */
    private List<String> rolesOf(Map<?, ?> claim) {
        if (claim == null || !(claim.get(ROLES) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(role -> !role.isBlank())
                .toList();
    }

    /** "ADMIN" -> ROLE_ADMIN. Locale.ROOT evita sorpresas con locales como el turco. */
    private GrantedAuthority toAuthority(String role) {
        return new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase(Locale.ROOT));
    }
}