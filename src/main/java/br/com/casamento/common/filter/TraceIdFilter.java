package br.com.casamento.common.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.io.IOException;
import java.util.UUID;

@Provider
@Priority(1)
public class TraceIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** Google Cloud Run trace header format: projects/PROJECT/traces/TRACE_ID */
    private static final String CLOUD_TRACE_HEADER = "X-Cloud-Trace-Context";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String traceId = extractOrGenerate(requestContext);
        MDC.put(TRACE_ID_KEY, traceId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String traceId = (String) MDC.get(TRACE_ID_KEY);
        if (traceId != null) {
            responseContext.getHeaders().add(TRACE_ID_HEADER, traceId);
        }
        MDC.remove(TRACE_ID_KEY);
    }

    private String extractOrGenerate(ContainerRequestContext ctx) {
        String cloudTrace = ctx.getHeaderString(CLOUD_TRACE_HEADER);
        if (cloudTrace != null && !cloudTrace.isBlank()) {
            // Format: TRACE_ID/SPAN_ID;o=TRACE_TRUE
            return cloudTrace.split("/")[0];
        }
        String existing = ctx.getHeaderString(TRACE_ID_HEADER);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return UUID.randomUUID().toString();
    }
}
