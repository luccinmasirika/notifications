package com.irembo.notifications.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Filter to wrap requests with a custom wrapper that caches the request body.
 * This allows multiple reads of the request body without consuming the stream.
 * 
 * This filter must run BEFORE SignatureValidationFilter to ensure the body can be read
 * multiple times (once for signature validation, once for Spring's @RequestBody).
 */
@Component
@Order(0) // Run before all other filters (APIKeyAuthFilter is Order 1, SignatureValidationFilter is Order 2)
public class ContentCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Only wrap requests that have a body (POST, PUT, PATCH)
        if (request instanceof CachedBodyHttpServletRequest) {
            // Already wrapped, continue
            filterChain.doFilter(request, response);
            return;
        }

        // Only wrap requests that might have a body
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || 
            "PUT".equalsIgnoreCase(method) || 
            "PATCH".equalsIgnoreCase(method)) {
            // Wrap the request to allow multiple reads of the body
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
            filterChain.doFilter(wrappedRequest, response);
        } else {
            // For GET, DELETE, etc., no need to wrap
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Custom HttpServletRequestWrapper that caches the request body.
     * This allows the body to be read multiple times.
     */
    public static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            // Read and cache the body immediately
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CachedBodyServletInputStream(this.cachedBody);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
            return new BufferedReader(new InputStreamReader(byteArrayInputStream));
        }

        /**
         * Get the cached body as a byte array.
         */
        public byte[] getCachedBody() {
            return this.cachedBody;
        }

        /**
         * Get the cached body as a string.
         */
        public String getCachedBodyAsString() {
            return new String(this.cachedBody, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Custom ServletInputStream that reads from a cached byte array.
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream buffer;

        public CachedBodyServletInputStream(byte[] contents) {
            this.buffer = new ByteArrayInputStream(contents);
        }

        @Override
        public int read() throws IOException {
            return buffer.read();
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("ReadListener is not supported");
        }
    }
}
