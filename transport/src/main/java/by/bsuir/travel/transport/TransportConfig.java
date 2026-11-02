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
        // Use the same decoded, matrix-parameter-free segments as Spring MVC routing.
        // Raw URI prefixes miss encoded names, matrix parameters and servlet context paths.
        boolean internal =
            org.springframework.web.util.ServletRequestPathUtils.parseAndCache(q)
                .pathWithinApplication()
                .elements()
                .stream()
                .filter(org.springframework.http.server.PathContainer.PathSegment.class::isInstance)
                .map(org.springframework.http.server.PathContainer.PathSegment.class::cast)
                .map(org.springframework.http.server.PathContainer.PathSegment::valueToMatch)
                .findFirst()
                .filter("internal"::equals)
                .isPresent();
        if (internal) {
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
