package com.stockflow.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, RequestBucket> ipBuckets = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS_PER_WINDOW = 30; // Max 30 auth requests
    private static final long WINDOW_MS = 60_000;          // per 1-minute window

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Rate limit sensitive auth endpoints: /api/v1/auth/login and /api/v1/auth/register
        if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/register")) {
            String clientIp = getClientIp(request);
            long now = System.currentTimeMillis();

            RequestBucket bucket = ipBuckets.compute(clientIp, (k, existing) -> {
                if (existing == null || now - existing.windowStart > WINDOW_MS) {
                    return new RequestBucket(now, new AtomicInteger(1));
                }
                existing.counter.incrementAndGet();
                return existing;
            });

            if (bucket.counter.get() > MAX_REQUESTS_PER_WINDOW) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.setHeader("Retry-After", "60");

                ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many requests from this IP. Please wait before attempting to authenticate again."
                );
                problem.setTitle("Rate Limit Exceeded");
                problem.setType(URI.create("https://stockflow.io/errors/rate-limit-exceeded"));
                problem.setProperty("timestamp", Instant.now().toString());

                response.getWriter().write(objectMapper.writeValueAsString(problem));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record RequestBucket(long windowStart, AtomicInteger counter) {}
}
