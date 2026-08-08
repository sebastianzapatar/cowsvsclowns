package com.sebasmalparqueado.cowsvsclown.common.security;

import com.sebasmalparqueado.cowsvsclown.common.exceptions.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Qué se le responde a quien <b>sí está autenticado pero no tiene el rol</b> que
 * la ruta exige: <b>403 Forbidden</b>.
 *
 * <p>La diferencia con el 401 es la pregunta que se responde:</p>
 * <ul>
 *   <li><b>401 Unauthorized</b> — "no sé quién sos". Falta el token o no es
 *       válido. Lo maneja {@link RestAuthenticationEntryPoint}.</li>
 *   <li><b>403 Forbidden</b> — "sé quién sos, y no podés". El token es
 *       perfectamente válido, pero por ejemplo un usuario con rol USER intentó
 *       un DELETE, que está reservado a ADMIN.</li>
 * </ul>
 *
 * <p>El mecanismo es el mismo que en el 401: se delega en el
 * {@code handlerExceptionResolver} de Spring MVC para que el cuerpo lo arme el
 * {@link GlobalExceptionHandler} y todos los errores de la API tengan la misma
 * forma.</p>
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public RestAccessDeniedHandler(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        if (resolver.resolveException(request, response, null, accessDeniedException) == null) {
            response.sendError(HttpStatus.FORBIDDEN.value());
        }
    }
}
