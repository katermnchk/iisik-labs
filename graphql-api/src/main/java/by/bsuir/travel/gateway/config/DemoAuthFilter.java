package by.bsuir.travel.gateway.config;

import by.bsuir.travel.contracts.Clients.UserClient;
import by.bsuir.travel.contracts.DomainException;
import by.bsuir.travel.contracts.Model.GetPrincipalRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DemoAuthFilter extends OncePerRequestFilter {
  private final UserClient client;
  private final ObjectMapper mapper;

  public DemoAuthFilter(UserClient client, ObjectMapper mapper) {
    this.client = client;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (!req.getRequestURI().equals("/graphql")) {
      chain.doFilter(req, res);
      return;
    }
    String h = req.getHeader("Authorization");
    try {
      if (h == null || !h.startsWith("Bearer "))
        throw new DomainException("UNAUTHENTICATED", "Bearer demo token is required");
      client.principal(new GetPrincipalRequest(h.substring(7)));
    } catch (DomainException e) {
      res.setStatus(e.getCode().equals("FORBIDDEN") ? 403 : 401);
      res.setContentType("application/json");
      mapper.writeValue(
          res.getOutputStream(),
          Map.of(
              "errors",
              List.of(
                  Map.of("message", e.getMessage(), "extensions", Map.of("code", e.getCode())))));
      return;
    }
    chain.doFilter(req, res);
  }
}
