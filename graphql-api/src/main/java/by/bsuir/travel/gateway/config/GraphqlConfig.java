package by.bsuir.travel.gateway.config;

import by.bsuir.travel.contracts.Clients.UserClient;
import by.bsuir.travel.contracts.Model.*;
import graphql.language.*;
import graphql.scalars.ExtendedScalars;
import graphql.schema.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.WebGraphQlInterceptor;

@Configuration
public class GraphqlConfig {
  @Bean
  Clock clock() {
    return Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);
  }

  @Bean
  RuntimeWiringConfigurer scalars() {
    GraphQLScalarType longType =
        GraphQLScalarType.newScalar()
            .name("Long")
            .description("Signed 64 bit integer serialized as a decimal string")
            .coercing(
                new Coercing<Long, String>() {
                  private Long convert(Object input) {
                    try {
                      if (input instanceof Number n)
                        return new java.math.BigDecimal(n.toString()).longValueExact();
                      return Long.parseLong(input.toString());
                    } catch (Exception e) {
                      throw new IllegalArgumentException("Expected 64 bit integer");
                    }
                  }

                  @Override
                  public String serialize(Object input) {
                    try {
                      return convert(input).toString();
                    } catch (Exception e) {
                      throw new CoercingSerializeException("Invalid Long");
                    }
                  }

                  @Override
                  public Long parseValue(Object input) {
                    try {
                      return convert(input);
                    } catch (Exception e) {
                      throw new CoercingParseValueException("Invalid Long");
                    }
                  }

                  @Override
                  public Long parseLiteral(Object input) {
                    try {
                      if (input instanceof StringValue s) return convert(s.getValue());
                      if (input instanceof IntValue i) return convert(i.getValue());
                    } catch (IllegalArgumentException e) {
                      throw new CoercingParseLiteralException("Invalid Long literal");
                    }
                    throw new CoercingParseLiteralException("Expected integer or decimal string");
                  }
                })
            .build();
    return builder ->
        builder.scalar(ExtendedScalars.DateTime).scalar(ExtendedScalars.Date).scalar(longType);
  }

  @Bean
  WebGraphQlInterceptor principalContext(UserClient client) {
    return (request, chain) -> {
      String header = request.getHeaders().getFirst("Authorization");
      Principal p =
          client.principal(new GetPrincipalRequest(header == null ? null : header.substring(7)));
      request.configureExecutionInput(
          (input, builder) -> builder.graphQLContext(Map.of("principal", p)).build());
      return chain.next(request);
    };
  }
}
