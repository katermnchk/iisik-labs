package by.bsuir.travel.transport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class InternalHttpClosedTest {
  @RestController
  static class InternalReadController {
    @GetMapping("/internal/v1/users")
    String users() {
      return "private user profiles";
    }

    @GetMapping("/actuator/health")
    String health() {
      return "UP";
    }
  }

  private MockMvc mvc() {
    return MockMvcBuilders.standaloneSetup(new InternalReadController())
        .addFilters(new TransportConfig().internalHttpClosed())
        .build();
  }

  @Test
  void normalInternalPathIsClosed() throws Exception {
    mvc().perform(get("/internal/v1/users")).andExpect(status().isNotFound());
  }

  @Test
  void matrixParameterCannotBypassInternalPathClosure() throws Exception {
    mvc().perform(get("/internal;x/v1/users")).andExpect(status().isNotFound());
  }

  @Test
  void encodedFirstSegmentCannotBypassInternalPathClosure() throws Exception {
    mvc().perform(get(URI.create("/%69nternal/v1/users"))).andExpect(status().isNotFound());
  }

  @Test
  void contextPathCannotBypassInternalPathClosure() throws Exception {
    mvc()
        .perform(get("/travel/internal/v1/users").contextPath("/travel"))
        .andExpect(status().isNotFound());
  }

  @Test
  void healthRemainsAvailable() throws Exception {
    mvc().perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
