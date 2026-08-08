package com.sebasmalparqueado.cowsvsclown.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link KeycloakRoleConverter}, the piece that decides
 * <b>what a user can do</b> once the token has been proven authentic.
 *
 * <p>Nothing here talks to Keycloak or to Spring: a {@link Jwt} is just a
 * decoded token, so it can be built by hand with exactly the claims each case
 * needs. That is the point of testing this class on its own — every branch of
 * "the claim is missing / has the wrong shape / is empty" is one line of setup
 * instead of a Keycloak configured to misbehave.</p>
 *
 * <p>Those malformed cases are not paranoia. The JWT is external input: it is
 * built by another system and can change shape between Keycloak versions or
 * when someone edits a client's mappers. If the converter threw on an
 * unexpected claim, the failure would happen inside a servlet filter, where
 * there is no {@code @ExceptionHandler} to turn it into a decent response —
 * every request would end in a 500.</p>
 */
@DisplayName("KeycloakRoleConverter")
class KeycloakRoleConverterTest {

    /** Same clientId as the application: it decides which client roles are read. */
    private static final String CLIENT_ID = "cowsvsclown-api";

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter(CLIENT_ID);

    // =========================== Helpers ================================

    /** Minimal decoded token; each test adds the claims it cares about. */
    private Jwt tokenWith(Consumer<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "RS256")
                .claim("preferred_username", "tester");
        claims.accept(builder);
        return builder.build();
    }

    /** Token carrying realm roles, which is the usual Keycloak case. */
    private Jwt tokenWithRealmRoles(String... roles) {
        return tokenWith(builder ->
                builder.claim("realm_access", Map.of("roles", List.of(roles))));
    }

    /** The authority names, which is what {@code hasRole(...)} ends up comparing. */
    private List<String> authorityNames(Jwt jwt) {
        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    // ======================== Roles del realm ===========================

    @Nested
    @DisplayName("realm roles (realm_access.roles)")
    class RealmRoles {

        @Test
        @DisplayName("turns each role into ROLE_<name>")
        void addsRolePrefix() {
            List<String> authorities = authorityNames(tokenWithRealmRoles("ADMIN", "USER"));

            // The ROLE_ prefix is not cosmetic: hasRole("ADMIN") literally looks
            // for the authority ROLE_ADMIN. Without it every rule returns 403.
            assertTrue(authorities.contains("ROLE_ADMIN"));
            assertTrue(authorities.contains("ROLE_USER"));
        }

        @Test
        @DisplayName("uppercases the name, so a role created as 'admin' also works")
        void uppercasesTheName() {
            assertTrue(authorityNames(tokenWithRealmRoles("admin")).contains("ROLE_ADMIN"));
        }

        @Test
        @DisplayName("keeps Keycloak's own roles instead of filtering them")
        void keepsKeycloakInternalRoles() {
            // offline_access and uma_authorization are added by Keycloak, not by
            // us. They are harmless (no rule mentions them) and hiding them would
            // mean the authorities no longer reflect the real token.
            List<String> authorities = authorityNames(
                    tokenWithRealmRoles("USER", "offline_access", "uma_authorization"));

            assertEquals(3, authorities.size());
            assertTrue(authorities.contains("ROLE_OFFLINE_ACCESS"));
        }
    }

    // ======================== Roles del cliente =========================

    @Nested
    @DisplayName("client roles (resource_access.<clientId>.roles)")
    class ClientRoles {

        @Test
        @DisplayName("reads the roles of the configured client")
        void readsConfiguredClientRoles() {
            Jwt jwt = tokenWith(builder -> builder.claim("resource_access",
                    Map.of(CLIENT_ID, Map.of("roles", List.of("reportes")))));

            assertTrue(authorityNames(jwt).contains("ROLE_REPORTES"));
        }

        @Test
        @DisplayName("ignores the roles of other clients in the same realm")
        void ignoresOtherClients() {
            // A realm usually serves several applications, and the token carries
            // the roles of all of them. Being an admin of the billing app must
            // not make you an admin here.
            Jwt jwt = tokenWith(builder -> builder.claim("resource_access",
                    Map.of("otra-api", Map.of("roles", List.of("ADMIN")))));

            assertTrue(authorityNames(jwt).isEmpty());
        }

        @Test
        @DisplayName("merges realm and client roles without duplicating them")
        void mergesBothSources() {
            Jwt jwt = tokenWith(builder -> {
                builder.claim("realm_access", Map.of("roles", List.of("USER")));
                builder.claim("resource_access",
                        Map.of(CLIENT_ID, Map.of("roles", List.of("USER", "reportes"))));
            });

            List<String> authorities = authorityNames(jwt);

            // USER appears in both places and must yield a single authority.
            assertEquals(2, authorities.size());
            assertTrue(authorities.contains("ROLE_USER"));
            assertTrue(authorities.contains("ROLE_REPORTES"));
        }
    }

    // ============================= Scopes ===============================

    @Test
    @DisplayName("also keeps the SCOPE_* authorities of the standard converter")
    void keepsScopes() {
        Jwt jwt = tokenWith(builder -> {
            builder.claim("scope", "openid profile");
            builder.claim("realm_access", Map.of("roles", List.of("USER")));
        });

        List<String> authorities = authorityNames(jwt);

        // Scopes answer a different question than roles: the role says what the
        // *user* is allowed to do, the scope says how much of that the *client*
        // application was authorised to use on their behalf. Nothing authorises
        // by scope today, but throwing the information away would close the door.
        assertTrue(authorities.contains("SCOPE_openid"));
        assertTrue(authorities.contains("SCOPE_profile"));
        assertTrue(authorities.contains("ROLE_USER"));
    }

    // =================== Tokens raros o incompletos =====================

    @Nested
    @DisplayName("tokens with missing or malformed claims")
    class MalformedTokens {

        @Test
        @DisplayName("a token with no roles yields no authorities, and does not fail")
        void tokenWithoutRoles() {
            // This is the 'curioso' user of the realm: authenticated (so no 401)
            // but with no permissions (so 403 on any write).
            assertTrue(authorityNames(tokenWith(builder -> { })).isEmpty());
        }

        @Test
        @DisplayName("realm_access without the 'roles' key does not fail")
        void realmAccessWithoutRolesKey() {
            Jwt jwt = tokenWith(builder ->
                    builder.claim("realm_access", Map.of("otra-cosa", "valor")));

            assertTrue(authorityNames(jwt).isEmpty());
        }

        @Test
        @DisplayName("'roles' with an unexpected type does not fail")
        void rolesWithWrongType() {
            // Not a list but a string: the shape of the claim changed, or a
            // mapper is misconfigured. Better no authorities than a 500.
            Jwt jwt = tokenWith(builder ->
                    builder.claim("realm_access", Map.of("roles", "ADMIN")));

            assertTrue(authorityNames(jwt).isEmpty());
        }

        @Test
        @DisplayName("non-string entries inside the list are skipped")
        void rolesWithNonStringEntries() {
            Jwt jwt = tokenWith(builder ->
                    builder.claim("realm_access", Map.of("roles", List.of("USER", 42))));

            List<String> authorities = authorityNames(jwt);

            assertEquals(List.of("ROLE_USER"), authorities);
        }

        @Test
        @DisplayName("empty or blank role names are discarded")
        void blankRoles() {
            Jwt jwt = tokenWith(builder ->
                    builder.claim("realm_access", Map.of("roles", List.of("USER", "  "))));

            // Without the filter this would produce the authority "ROLE_  ",
            // which matches nothing and only pollutes the logs.
            assertFalse(authorityNames(jwt).contains("ROLE_  "));
            assertEquals(1, authorityNames(jwt).size());
        }

        @Test
        @DisplayName("resource_access pointing to something that is not a map does not fail")
        void resourceAccessWithWrongShape() {
            Jwt jwt = tokenWith(builder ->
                    builder.claim("resource_access", Map.of(CLIENT_ID, "no soy un mapa")));

            assertTrue(authorityNames(jwt).isEmpty());
        }
    }
}