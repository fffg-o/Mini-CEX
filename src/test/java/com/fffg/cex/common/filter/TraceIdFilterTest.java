package com.fffg.cex.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Test
    void testDoFilterWithExistingTraceId() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn("custom-trace-id");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/health");

        // 捕获 chain.doFilter 内部的 traceId（此时 ThreadLocal 尚未被清理）
        final String[] capturedTraceId = new String[1];
        doAnswer(invocation -> {
            capturedTraceId[0] = TraceIdFilter.getCurrentTraceId();
            return null;
        }).when(chain).doFilter(request, response);

        TraceIdFilter filter = new TraceIdFilter();
        filter.doFilter(request, response, chain);

        assertEquals("custom-trace-id", capturedTraceId[0]);
        verify(response).setHeader("X-Trace-Id", "custom-trace-id");
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilterGeneratesTraceId() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/accounts");

        // 捕获 chain.doFilter 内部的 traceId
        final String[] capturedTraceId = new String[1];
        doAnswer(invocation -> {
            capturedTraceId[0] = TraceIdFilter.getCurrentTraceId();
            return null;
        }).when(chain).doFilter(request, response);

        TraceIdFilter filter = new TraceIdFilter();
        filter.doFilter(request, response, chain);

        assertNotNull(capturedTraceId[0]);
        assertEquals(16, capturedTraceId[0].length());
        verify(response).setHeader(eq("X-Trace-Id"), anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilterWithBlankTraceId() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn("   ");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");

        // 捕获 chain.doFilter 内部的 traceId
        final String[] capturedTraceId = new String[1];
        doAnswer(invocation -> {
            capturedTraceId[0] = TraceIdFilter.getCurrentTraceId();
            return null;
        }).when(chain).doFilter(request, response);

        TraceIdFilter filter = new TraceIdFilter();
        filter.doFilter(request, response, chain);

        assertNotNull(capturedTraceId[0]);
        assertEquals(16, capturedTraceId[0].length());
        verify(chain).doFilter(request, response);
    }

    @Test
    void testTraceIdClearedAfterRequest() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn("test-id");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/health");

        TraceIdFilter filter = new TraceIdFilter();
        filter.doFilter(request, response, chain);

        assertNull(TraceIdFilter.getCurrentTraceId(), "TraceId should be cleared after request completes");
    }
}
