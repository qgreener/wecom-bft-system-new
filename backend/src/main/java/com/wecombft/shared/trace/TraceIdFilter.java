package com.wecombft.shared.trace;

import java.io.IOException;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = normalize(request.getHeader(TraceIds.HEADER_NAME));
        TraceIds.set(traceId);
        MDC.put("trace_id", traceId);
        response.setHeader(TraceIds.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("trace_id");
            TraceIds.clear();
        }
    }

    private String normalize(String candidate) {
        if (candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return TraceIds.create();
    }
}
