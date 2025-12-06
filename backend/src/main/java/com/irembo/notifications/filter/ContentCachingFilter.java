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

@Component
@Order(0)
public class ContentCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (request instanceof CachedBodyHttpServletRequest) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || 
            "PUT".equalsIgnoreCase(method) || 
            "PATCH".equalsIgnoreCase(method)) {
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
            filterChain.doFilter(wrappedRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    public static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
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

        public byte[] getCachedBody() {
            return this.cachedBody;
        }

        public String getCachedBodyAsString() {
            return new String(this.cachedBody, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

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
