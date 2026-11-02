package by.bsuir.travel.gateway.config;

import by.bsuir.travel.contracts.DomainException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

@Component
public class QueryErrors extends DataFetcherExceptionResolverAdapter {
  @Override
  protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
    if (ex instanceof BeanInstantiationException
        && ex.getCause() instanceof IllegalArgumentException) ex = ex.getCause();
    String code =
        ex instanceof DomainException d
            ? d.getCode()
            : ex instanceof IllegalArgumentException || ex instanceof BindException
                ? "BAD_REQUEST"
                : "INTERNAL_ERROR";
    if (code.equals("INTERNAL_ERROR"))
      LoggerFactory.getLogger(QueryErrors.class).error("GraphQL execution failed", ex);
    if (!code.equals("INTERNAL_ERROR"))
      LoggerFactory.getLogger(QueryErrors.class)
          .warn(
              "graphql rejected field={} code={} correlationId={}",
              env.getFieldDefinition().getName(),
              code,
              (Object) env.getGraphQlContext().get("correlationId"));
    String message =
        ex instanceof DomainException
            ? ex.getMessage()
            : code.equals("BAD_REQUEST") ? "Invalid input" : "Internal service error";
    return GraphqlErrorBuilder.newError(env)
        .message(message)
        .extensions(Map.of("code", code))
        .build();
  }
}
