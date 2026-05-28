package com.wecombft.infrastructure.security;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminSecurityInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION = "Authorization";

    private final AdminSessionService adminSessionService;

    public AdminSecurityInterceptor(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicAdminAuthPath(request)) {
            return true;
        }

        AdminPrincipal principal = authenticate(request, handler);
        request.setAttribute(AdminPrincipalContext.REQUEST_ATTRIBUTE, principal);
        AdminPrincipalContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception exception
    ) {
        AdminPrincipalContext.clear();
    }

    private boolean isPublicAdminAuthPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "/api/admin/auth/test-login".equals(path)
            || "/api/admin/auth/wecom-login".equals(path)
            || "/api/admin/auth/wecom-oauth/start-redirect".equals(path)
            || "/api/admin/auth/wecom-oauth/callback".equals(path)
            || "/api/admin/auth/wecom-oauth/qr-config".equals(path);
    }

    private AdminPrincipal authenticate(HttpServletRequest request, Object handler) {
        String authorization = request.getHeader(AUTHORIZATION);
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return adminSessionService.require(authorization);
        }

        RequirePermission permission = findAnnotation(handlerMethod, RequirePermission.class);
        RequireAnyPermission anyPermission = findAnnotation(handlerMethod, RequireAnyPermission.class);
        AdminPrincipal principal;
        if (permission == null) {
            principal = adminSessionService.require(authorization);
        } else {
            principal = adminSessionService.requirePermission(authorization, permission.value());
        }
        if (anyPermission != null) {
            adminSessionService.requireAnyPermission(principal, java.util.List.of(anyPermission.value()));
        }
        return principal;
    }

    private <T extends java.lang.annotation.Annotation> T findAnnotation(
        HandlerMethod handlerMethod,
        Class<T> annotationType
    ) {
        T annotation = handlerMethod.getMethodAnnotation(annotationType);
        return annotation == null ? handlerMethod.getBeanType().getAnnotation(annotationType) : annotation;
    }
}
