package com.irembo.notifications.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentCachingFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ContentCachingFilter filter;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Should cache POST request body")
    void shouldCachePostRequestBody() throws Exception {
        String requestBody = "{\"channel\":\"SMS\",\"recipient\":\"+1234567890\",\"message\":\"Test\"}";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestBody.getBytes());
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getInputStream()).thenReturn(new MockServletInputStream(inputStream));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(ContentCachingFilter.CachedBodyHttpServletRequest.class), eq(response));
    }

    @Test
    @DisplayName("Should cache PUT request body")
    void shouldCachePutRequestBody() throws Exception {
        String requestBody = "{\"id\":1,\"name\":\"Updated\"}";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestBody.getBytes());
        
        when(request.getMethod()).thenReturn("PUT");
        when(request.getInputStream()).thenReturn(new MockServletInputStream(inputStream));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(ContentCachingFilter.CachedBodyHttpServletRequest.class), eq(response));
    }

    @Test
    @DisplayName("Should cache PATCH request body")
    void shouldCachePatchRequestBody() throws Exception {
        String requestBody = "{\"status\":\"active\"}";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestBody.getBytes());
        
        when(request.getMethod()).thenReturn("PATCH");
        when(request.getInputStream()).thenReturn(new MockServletInputStream(inputStream));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(ContentCachingFilter.CachedBodyHttpServletRequest.class), eq(response));
    }

    @Test
    @DisplayName("Should not cache GET request")
    void shouldNotCacheGetRequest() throws Exception {
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), eq(response));
    }

    @Test
    @DisplayName("Should not cache DELETE request")
    void shouldNotCacheDeleteRequest() throws Exception {
        when(request.getMethod()).thenReturn("DELETE");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), eq(response));
    }

    @Test
    @DisplayName("Should pass through already cached request")
    void shouldPassThroughAlreadyCachedRequest() throws Exception {
        when(request.getInputStream()).thenReturn(new MockServletInputStream(new ByteArrayInputStream(new byte[0])));

        ContentCachingFilter.CachedBodyHttpServletRequest cachedRequest = 
            new ContentCachingFilter.CachedBodyHttpServletRequest(request);

        filter.doFilterInternal(cachedRequest, response, filterChain);

        verify(filterChain).doFilter(eq(cachedRequest), eq(response));
    }

    @Test
    @DisplayName("CachedBodyHttpServletRequest should return cached body as string")
    void cachedBodyHttpServletRequestShouldReturnCachedBodyAsString() throws Exception {
        String requestBody = "{\"test\":\"data\"}";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestBody.getBytes());
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getInputStream()).thenReturn(new MockServletInputStream(inputStream));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(argThat(req -> {
            if (req instanceof ContentCachingFilter.CachedBodyHttpServletRequest) {
                ContentCachingFilter.CachedBodyHttpServletRequest cached = 
                    (ContentCachingFilter.CachedBodyHttpServletRequest) req;
                String cachedBody = cached.getCachedBodyAsString();
                return cachedBody.equals(requestBody);
            }
            return false;
        }), eq(response));
    }

    @Test
    @DisplayName("CachedBodyHttpServletRequest should return cached body as bytes")
    void cachedBodyHttpServletRequestShouldReturnCachedBodyAsBytes() throws Exception {
        String requestBody = "{\"test\":\"data\"}";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestBody.getBytes());
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getInputStream()).thenReturn(new MockServletInputStream(inputStream));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(argThat(req -> {
            if (req instanceof ContentCachingFilter.CachedBodyHttpServletRequest) {
                ContentCachingFilter.CachedBodyHttpServletRequest cached = 
                    (ContentCachingFilter.CachedBodyHttpServletRequest) req;
                byte[] cachedBody = cached.getCachedBody();
                return new String(cachedBody).equals(requestBody);
            }
            return false;
        }), eq(response));
    }

    @Test
    @DisplayName("CachedBodyServletInputStream should read bytes correctly")
    void cachedBodyServletInputStreamShouldReadBytesCorrectly() throws Exception {
        String requestBody = "test data";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestBody.getBytes());
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getInputStream()).thenReturn(new MockServletInputStream(inputStream));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(argThat(req -> {
            if (req instanceof ContentCachingFilter.CachedBodyHttpServletRequest) {
                try {
                    ContentCachingFilter.CachedBodyHttpServletRequest cached = 
                        (ContentCachingFilter.CachedBodyHttpServletRequest) req;
                    jakarta.servlet.ServletInputStream stream = cached.getInputStream();
                    assertFalse(stream.isFinished());
                    assertTrue(stream.isReady());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        }), eq(response));
    }

    // Helper class for testing
    private static class MockServletInputStream extends jakarta.servlet.ServletInputStream {
        private final ByteArrayInputStream inputStream;

        public MockServletInputStream(ByteArrayInputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener listener) {
            // Not implemented for testing
        }
    }
}
