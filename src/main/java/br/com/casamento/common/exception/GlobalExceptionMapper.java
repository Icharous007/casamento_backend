package br.com.casamento.common.exception;

import br.com.casamento.common.dto.ErrorResponse;
import br.com.casamento.common.filter.TraceIdFilter;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        String traceId = (String) MDC.get(TraceIdFilter.TRACE_ID_KEY);

        if (exception instanceof AppException appEx) {
            return Response.status(appEx.getStatus())
                    .entity(new ErrorResponse(appEx.getCode(), appEx.getMessage(), traceId))
                    .build();
        }

        if (exception instanceof ConstraintViolationException cve) {
            Map<String, Object> details = new HashMap<>();
            Map<String, String> violations = cve.getConstraintViolations().stream()
                    .collect(Collectors.toMap(
                            v -> v.getPropertyPath().toString(),
                            v -> v.getMessage(),
                            (a, b) -> a
                    ));
            details.put("violations", violations);
            return Response.status(422)
                    .entity(new ErrorResponse("VALIDATION_ERROR", "Dados inválidos", details, traceId))
                    .build();
        }

        if (exception instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            String code = mapHttpStatusToCode(status);
            return Response.status(status)
                    .entity(new ErrorResponse(code, wae.getMessage(), traceId))
                    .build();
        }

        LOG.errorf(exception, "Unhandled exception [traceId=%s]", traceId);
        return Response.status(500)
                .entity(new ErrorResponse("INTERNAL_ERROR", "Erro interno do servidor", traceId))
                .build();
    }

    private String mapHttpStatusToCode(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 410 -> "GONE";
            case 429 -> "RATE_LIMIT_EXCEEDED";
            default -> "HTTP_ERROR";
        };
    }
}
