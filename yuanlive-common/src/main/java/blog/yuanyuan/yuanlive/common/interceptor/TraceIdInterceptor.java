package blog.yuanyuan.yuanlive.common.interceptor;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class TraceIdInterceptor implements HandlerInterceptor {
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 尝试从 Header 中获取 (实现跨服务透传)
        // 如果网关传过来了 traceId，我们就沿用；如果没传，我们就生成新的
        String traceId = request.getHeader(TRACE_ID_KEY);

        if (StrUtil.isEmpty(traceId)) {
            traceId = IdUtil.fastSimpleUUID();
        }

        response.setHeader(TRACE_ID_KEY, traceId);

        // 2. 放入 MDC
        MDC.put(TRACE_ID_KEY, traceId);
        log.info("========================================== Start ==========================================");

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 3. 请求处理完成（包括异常处理完毕）后，清除 MDC
        // 这是最安全的地方，比 AOP 的 finally 更晚执行
        log.info("=========================================== End ===========================================");
        MDC.remove(TRACE_ID_KEY);
    }
}
