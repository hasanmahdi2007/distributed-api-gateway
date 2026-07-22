package com.distributed.job_finder.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(-1) // <-- Your idea! This forces it to run first, before the controller.
public class InternalGatewayFilter extends OncePerRequestFilter {

    private static final String INTERNAL_SECRET = "super-secret-internal-key-123!";
    private static final String SECRET_HEADER = "X-Internal-Secret";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String incomingSecret = request.getHeader(SECRET_HEADER);

        if (incomingSecret == null || !incomingSecret.equals(INTERNAL_SECRET)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Direct access forbidden. Route through Gateway.\"}");
            return; 
        }

        filterChain.doFilter(request, response);
    }
}