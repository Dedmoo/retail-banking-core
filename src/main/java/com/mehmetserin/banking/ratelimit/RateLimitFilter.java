package com.mehmetserin.banking.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-caller rate limit applied only to the transfer endpoint.
 * Keyed by authenticated username when available, falling back to the
 * remote address for unauthenticated requests.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PATH = "/api/transfers";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties.Bucket config;

    public RateLimitFilter(RateLimitProperties properties) {
        this.config = properties.getTransfer();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(resolveKey(request), key -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many transfer requests, try again later\"}");
        }
    }

    private boolean isProtected(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && PROTECTED_PATH.equals(request.getRequestURI());
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return "user:" + authentication.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.getCapacity())
                .refillGreedy(config.getCapacity(), Duration.ofMinutes(config.getRefillMinutes()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
