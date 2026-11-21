package com.universidad.msvsectoerasiento.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class InternalAuthInterceptor implements RequestInterceptor {
    private final ServiceTokenProvider tokens;

    public InternalAuthInterceptor(ServiceTokenProvider tokens) {
        this.tokens = tokens;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.removeHeader(HttpHeaders.AUTHORIZATION);
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
    }
}
