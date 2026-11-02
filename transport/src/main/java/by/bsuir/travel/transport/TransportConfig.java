package by.bsuir.travel.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class TransportConfig {
  @Bean
  org.springframework.web.filter.OncePerRequestFilter internalHttpClosed() {
    return new org.springframework.web.filter.OncePerRequestFilter() {
      protected void doFilterInternal(
          jakarta.servlet.http.HttpServletRequest q,
          jakarta.servlet.http.HttpServletResponse s,
          jakarta.servlet.FilterChain c)
          throws java.io.IOException, jakarta.servlet.ServletException {
        if (q.getRequestURI().startsWith("/internal/")) {
          s.sendError(404);
          return;
        }
        c.doFilter(q, s);
      }
    };
  }

  @Bean(destroyMethod = "close")
  RpcClient rpcClient(ObjectMapper m, Environment e) {
    return new RpcClient(m, e.getRequiredProperty("SERVICE_KEY"));
  }
}
