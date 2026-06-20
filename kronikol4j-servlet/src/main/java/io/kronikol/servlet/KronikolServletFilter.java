package io.kronikol.servlet;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Realises Layer 1 of identity resolution (plan §3.2/§7): for an incoming request carrying the
 * test-identity headers, opens a {@link TestIdentityScope} for the duration of request handling so
 * that any tracking the system-under-test performs on the request thread attributes to that test.
 *
 * <p>The scope is always closed in a {@code finally} (mandatory clearing, §3.2). Register in any
 * servlet container / Spring Boot app (a {@code FilterRegistrationBean} or {@code @Component}).
 */
public final class KronikolServletFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        TestInfo identity = null;
        if (request instanceof HttpServletRequest http) {
            identity = ServletIdentity.fromHeaders(http::getHeader);
        }

        if (identity == null) {
            chain.doFilter(request, response);
            return;
        }

        try (var scope = TestIdentityScope.begin(identity.name(), identity.id())) {
            chain.doFilter(request, response);
        }
    }
}
