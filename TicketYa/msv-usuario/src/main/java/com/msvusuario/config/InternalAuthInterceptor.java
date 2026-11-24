package com.msvusuario.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;

public class InternalAuthInterceptor implements RequestInterceptor {
    private final ServiceTokenProvider serviceTokenProvider;

    public InternalAuthInterceptor(ServiceTokenProvider serviceTokenProvider) {
        this.serviceTokenProvider = serviceTokenProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.removeHeader(HttpHeaders.AUTHORIZATION);
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenProvider.token());
        String correlacion = org.slf4j.MDC.get("correlationId");
        if (correlacion != null) {
            template.header("X-Correlation-Id", correlacion);
        }
    }
}
