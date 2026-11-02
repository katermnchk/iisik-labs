package by.bsuir.travel.gateway.config;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Model.*;
import graphql.*;
import graphql.execution.*;
import graphql.execution.instrumentation.*;
import graphql.execution.instrumentation.parameters.*;
import graphql.schema.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.graphql.server.WebGraphQlInterceptor;

@Configuration
public class SecurityConfig {
  @Bean
  WebGraphQlInterceptor identity(AuthClient auth) {
    return (request, chain) -> {
      String h = request.getHeaders().getFirst("Authorization");
      Map<String, Object> context = new HashMap<>();
      String cid = UUID.randomUUID().toString();
      context.put("correlationId", cid);
      if (h != null) {
        try {
          if (!h.startsWith("Bearer "))
            throw new DomainException("UNAUTHENTICATED", "Bearer token required");
          String t = h.substring(7);
          context.put("principal", auth.call("/auth/validate", Map.of(), t, Principal.class));
          context.put("token", t);
        } catch (DomainException e) {
          context.put("authError", e);
        }
      }
      request.configureExecutionInput((i, b) -> b.graphQLContext(context).build());
      return chain.next(request);
    };
  }

  @Bean
  Instrumentation security() {
    return new SimplePerformantInstrumentation() {
      @Override
      public DataFetcher<?> instrumentDataFetcher(
          DataFetcher<?> fetcher,
          InstrumentationFieldFetchParameters params,
          InstrumentationState state) {
        if (!params.getExecutionStepInfo().getPath().getParent().isRootPath()) return fetcher;
        return env -> {
          String field = env.getFieldDefinition().getName();
          boolean publicField = Set.of("register", "authenticate").contains(field);
          if (!publicField) {
            DomainException e = env.getGraphQlContext().get("authError");
            if (e != null) throw e;
            Principal p = env.getGraphQlContext().get("principal");
            if (p == null) throw new DomainException("UNAUTHENTICATED", "Authentication required");
          }
          org.slf4j.MDC.put("correlationId", env.getGraphQlContext().get("correlationId"));
          org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
              .info(
                  "graphql field={} correlationId={}",
                  field,
                  env.getGraphQlContext().get("correlationId"));
          try {
            return fetcher.get(env);
          } finally {
            org.slf4j.MDC.remove("correlationId");
          }
        };
      }
    };
  }
}
