package com.wecombft.infrastructure.security;

public final class AdminPrincipalContext {

    public static final String REQUEST_ATTRIBUTE = AdminPrincipalContext.class.getName() + ".principal";

    private static final ThreadLocal<AdminPrincipal> CURRENT = new ThreadLocal<>();

    private AdminPrincipalContext() {
    }

    public static void set(AdminPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AdminPrincipal currentOrNull() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
