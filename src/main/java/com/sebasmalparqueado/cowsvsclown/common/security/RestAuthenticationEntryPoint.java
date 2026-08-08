package com.sebasmalparqueado.cowsvsclown.common.security;

import com.sebasmalparqueado.cowsvsclown.common.exceptions.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Qué se le responde a quien pide un recurso protegido <b>sin token</b> (o con
 * un token vencido, mal firmado o de otro emisor): <b>401 Unauthorized</b>.
 *
 * <h2>Por qué existe esta clase</h2>
 *
 * <p>Spring Security rechaza estas peticiones en su cadena de filtros, es decir
 * <b>antes</b> de que la petición llegue al DispatcherServlet. Por eso el
 * {@link GlobalExceptionHandler} nunca se entera: sin esta clase, el cliente
 * recibe la página de error por defecto de Spring, con otra forma de JSON
 * distinta a la del resto de la API.</p>
 *
 * <p>La solución es no escribir el JSON a mano acá, sino <b>reinyectar</b> la
 * excepción en el mismo mecanismo que usa el resto de la aplicación: el
 * {@code handlerExceptionResolver} de Spring MVC, que es el que termina llamando
 * a los {@code @ExceptionHandler} del {@link GlobalExceptionHandler}. Así el 401
 * sale con el mismo {@code ErrorResponse} (status, error, message, path,
 * timestamp) que un 404 o un 409, y hay un solo lugar donde se define el
 * formato de los errores.</p>
 *
 * @see RestAccessDeniedHandler el equivalente para el 403
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    public RestAuthenticationEntryPoint(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Cabecera exigida por el RFC 6750 para el esquema Bearer: le dice al
        // cliente CÓMO debería autenticarse, no solo que falló.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"cowsvsclown\"");

        // handler = null: los @ExceptionHandler de un @RestControllerAdvice se
        // resuelven por tipo de excepción, no por el controlador de destino, así
        // que no hace falta (ni se puede) saber a qué método iba la petición.
        if (resolver.resolveException(request, response, null, authException) == null) {
            // Red de seguridad: si por lo que sea no hubiera un handler capaz de
            // atender la excepción, igual respondemos 401 y no un 200 vacío.
            response.sendError(HttpStatus.UNAUTHORIZED.value());
        }
    }
}