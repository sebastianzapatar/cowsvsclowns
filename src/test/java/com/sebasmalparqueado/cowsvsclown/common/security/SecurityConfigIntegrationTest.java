package com.sebasmalparqueado.cowsvsclown.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebasmalparqueado.cowsvsclown.clowns.controller.ClownController;
import com.sebasmalparqueado.cowsvsclown.clowns.service.ClownService;
import com.sebasmalparqueado.cowsvsclown.common.config.SecurityConfig;
import com.sebasmalparqueado.cowsvsclown.cows.controller.CowController;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowRequest;
import com.sebasmalparqueado.cowsvsclown.cows.dto.CowResponse;
import com.sebasmalparqueado.cowsvsclown.cows.service.CowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests of {@link SecurityConfig}: <b>who can call what</b>.
 *
 * <p>This is the class that owns the security rules. The other controller test
 * classes deliberately switch the filters off ({@code addFilters = false}),
 * because their subject is the HTTP contract — status codes, JSON shape,
 * validation — and repeating the authorisation checks in each of them would
 * mean three places to update every time a rule changes.</p>
 *
 * <h2>How a token is faked</h2>
 *
 * <p>{@code jwt()} comes from {@code spring-security-test} and builds an
 * <b>already validated</b> authentication: it skips signature and expiry checks,
 * which is exactly what makes these tests runnable without Keycloak, offline and
 * in under a second.</p>
 *
 * <p>What is <i>not</i> skipped is the interesting half. The roles are not
 * handed over directly: a token is built with the real
 * {@code realm_access.roles} claim that Keycloak emits and the real
 * {@link KeycloakRoleConverter} is plugged in to read it. So each test really
 * exercises the chain "claim in the token → authority → rule", which is where
 * mistakes actually happen. The only thing taken on faith is the cryptography,
 * which belongs to Spring Security, not to this project.</p>
 *
 * <p>Note there is no {@code csrf()} anywhere: writes work without a CSRF token
 * because the API is stateless and disables that protection on purpose (the
 * reasoning is in {@link SecurityConfig}). If someone re-enables CSRF, every
 * write test here fails with 403 — which is the intended alarm.</p>
 */
@WebMvcTest({CowController.class, ClownController.class})
// @WebMvcTest only loads controllers and the web infrastructure, not arbitrary
// @Configuration classes. Without this import the chain under test would not
// exist and everything would answer 200.
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("Security rules")
class SecurityConfigIntegrationTest {

    private static final String COWS = "/api/cows";
    private static final String CLOWNS = "/api/clowns";

    private static final UUID COW_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLOWN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CowService cowService;

    @MockitoBean
    private ClownService clownService;

    /**
     * The real decoder downloads the realm's public keys over the network.
     * It is replaced by a mock that is never called: {@code jwt()} produces the
     * authentication after that step, so there is no token to decode.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ============================ Utilidades ============================

    /**
     * A token like the ones Keycloak issues, with the given realm roles.
     *
     * <p>The roles are placed in {@code realm_access.roles}, the same claim as
     * in production, and read back with the production converter.</p>
     */
    private static RequestPostProcessor withRoles(String... roles) {
        return jwt()
                .jwt(builder -> builder
                        .claim("preferred_username", "tester")
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRoleConverter("cowsvsclown-api"));
    }

    /** Token of an authenticated user with no roles (the 'curioso' of the realm). */
    private static RequestPostProcessor withoutRoles() {
        return withRoles();
    }

    private String cowBodyJson() throws Exception {
        return objectMapper.writeValueAsString(
                new CowRequest("Lola", 450, 12, 1L, null));
    }

    @BeforeEach
    void stubServices() {
        // The services are irrelevant here, but a POST that gets through has to
        // return something with an id: the controller builds the Location header
        // out of it and would fail with a 500, hiding the real result.
        CowResponse cow = new CowResponse(COW_ID, "Lola", 450, 12, true, null, List.of());
        when(cowService.create(any())).thenReturn(cow);
        when(cowService.update(any(), any())).thenReturn(cow);
    }

    // ======================== Rutas de lectura ==========================

    @Nested
    @DisplayName("GET (public)")
    class Reading {

        @Test
        @DisplayName("listing needs no token")
        void listWithoutToken() throws Exception {
            mockMvc.perform(get(COWS))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("fetching by id needs no token either")
        void byIdWithoutToken() throws Exception {
            mockMvc.perform(get(COWS + "/" + COW_ID))
                    .andExpect(status().isOk());
        }
    }

    // ===================== Sin token: 401 ===============================

    @Nested
    @DisplayName("Without a token, writing is rejected with 401")
    class WithoutToken {

        @Test
        @DisplayName("POST returns 401 and the service is never reached")
        void postWithoutToken() throws Exception {
            mockMvc.perform(post(COWS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cowBodyJson()))
                    .andExpect(status().isUnauthorized());

            // The important half of the assertion: the rejection happens in the
            // filter chain, before any business logic runs. If this ever fails,
            // the request reached the service and the block is only cosmetic.
            verify(cowService, never()).create(any());
        }

        @Test
        @DisplayName("PATCH returns 401")
        void patchWithoutToken() throws Exception {
            mockMvc.perform(patch(COWS + "/" + COW_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Lola\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE returns 401")
        void deleteWithoutToken() throws Exception {
            mockMvc.perform(delete(COWS + "/" + COW_ID))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("the 401 body has the same shape as every other API error")
        void errorBodyIsConsistent() throws Exception {
            mockMvc.perform(post(COWS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cowBodyJson()))
                    .andExpect(status().isUnauthorized())
                    // Same fields as a 404 or a 409. This is what
                    // RestAuthenticationEntryPoint buys: without it Spring would
                    // answer with its own error page, in a different format.
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.path").value(COWS))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    // Required by RFC 6750: tells the client how it should have
                    // authenticated, not just that it failed.
                    .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"cowsvsclown\""));
        }
    }

    // ============== Token válido pero sin permisos: 403 =================

    @Nested
    @DisplayName("With a token but the wrong role, the answer is 403")
    class WithoutPermission {

        @Test
        @DisplayName("a user with no roles cannot create")
        void withoutRolesCannotCreate() throws Exception {
            // 403 and not 401: the token is perfectly valid, so it is not a
            // question of identity but of permissions.
            mockMvc.perform(post(COWS)
                            .with(withoutRoles())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cowBodyJson()))
                    .andExpect(status().isForbidden());

            verify(cowService, never()).create(any());
        }

        @Test
        @DisplayName("USER cannot delete: DELETE is reserved for ADMIN")
        void userCannotDelete() throws Exception {
            mockMvc.perform(delete(COWS + "/" + COW_ID).with(withRoles("USER")))
                    .andExpect(status().isForbidden());

            verify(cowService, never()).softDelete(any());
        }

        @Test
        @DisplayName("the 403 body also follows the standard format")
        void errorBodyIsConsistent() throws Exception {
            mockMvc.perform(delete(COWS + "/" + COW_ID).with(withRoles("USER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    // ======================= Rol USER: escribe ==========================

    @Nested
    @DisplayName("USER can write but not delete")
    class UserRole {

        @Test
        @DisplayName("can create")
        void canCreate() throws Exception {
            mockMvc.perform(post(COWS)
                            .with(withRoles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cowBodyJson()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("can modify")
        void canModify() throws Exception {
            mockMvc.perform(patch(COWS + "/" + COW_ID)
                            .with(withRoles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Lola\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("can assign a cow to a clown")
        void canAssign() throws Exception {
            mockMvc.perform(post(CLOWNS + "/" + CLOWN_ID + "/cows/" + COW_ID)
                            .with(withRoles("USER")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("can unassign a cow, even though it is a DELETE")
        void canUnassign() throws Exception {
            // The exception that justifies having two DELETE rules: this one only
            // removes a row from the join table and is undone by assigning again,
            // so it does not deserve the same protection as deleting a record.
            // It also proves the rules are evaluated in order — if the generic
            // "DELETE /api/** -> ADMIN" came first, this would be a 403.
            mockMvc.perform(delete(CLOWNS + "/" + CLOWN_ID + "/cows/" + COW_ID)
                            .with(withRoles("USER")))
                    .andExpect(status().isOk());
        }
    }

    // ======================= Rol ADMIN: todo ============================

    @Nested
    @DisplayName("ADMIN can do everything")
    class AdminRole {

        @Test
        @DisplayName("can delete a cow")
        void canDeleteCow() throws Exception {
            mockMvc.perform(delete(COWS + "/" + COW_ID).with(withRoles("ADMIN")))
                    .andExpect(status().isNoContent());

            verify(cowService).softDelete(COW_ID);
        }

        @Test
        @DisplayName("can delete a clown")
        void canDeleteClown() throws Exception {
            mockMvc.perform(delete(CLOWNS + "/" + CLOWN_ID).with(withRoles("ADMIN")))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("can also create, without needing the USER role separately")
        void canAlsoCreate() throws Exception {
            // In Keycloak, ADMIN is a composite role that contains USER, so a real
            // token carries both. This checks the rule does not depend on that:
            // the write rules accept either of the two.
            mockMvc.perform(post(COWS)
                            .with(withRoles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cowBodyJson()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("a token with both roles works as well")
        void withBothRoles() throws Exception {
            // The realistic case: this is what an 'admin' token really looks like.
            mockMvc.perform(delete(COWS + "/" + COW_ID).with(withRoles("ADMIN", "USER")))
                    .andExpect(status().isNoContent());
        }
    }

    // ==================== Roles que no son los nuestros ==================

    @Test
    @DisplayName("a role from another realm application grants nothing here")
    void unrelatedRoleGrantsNothing() throws Exception {
        // A token can carry roles of other applications in the same realm.
        // Being 'contador' or 'supervisor' somewhere else must not open anything
        // here: only the roles this API declares are taken into account.
        mockMvc.perform(post(COWS)
                        .with(withRoles("contador", "supervisor"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cowBodyJson()))
                .andExpect(status().isForbidden());
    }
}