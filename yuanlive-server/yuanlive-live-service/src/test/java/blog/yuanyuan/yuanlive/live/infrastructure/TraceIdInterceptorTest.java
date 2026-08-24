package blog.yuanyuan.yuanlive.live.infrastructure;

import blog.yuanyuan.yuanlive.common.interceptor.TraceIdInterceptor;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceIdInterceptorTest {

    private static final String TRACE_ID = "traceId";

    @Test
    void reusesIncomingTraceIdAndReturnsItToTheCaller() {
        TraceIdInterceptor interceptor = new TraceIdInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TRACE_ID, "trace-from-gateway");

        interceptor.preHandle(request, response, new Object());

        assertEquals("trace-from-gateway", MDC.get(TRACE_ID));
        assertEquals("trace-from-gateway", response.getHeader(TRACE_ID));

        interceptor.afterCompletion(request, response, new Object(), null);
        assertNull(MDC.get(TRACE_ID));
    }

    @Test
    void createsTraceIdWhenTheCallerDoesNotProvideOne() {
        TraceIdInterceptor interceptor = new TraceIdInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertNotNull(response.getHeader(TRACE_ID));
        assertEquals(response.getHeader(TRACE_ID), MDC.get(TRACE_ID));

        interceptor.afterCompletion(request, response, new Object(), null);
    }
}
