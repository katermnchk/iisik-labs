package by.bsuir.travel.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import by.bsuir.travel.contracts.DomainException;
import by.bsuir.travel.contracts.Identity.AuthenticateInput;
import by.bsuir.travel.transport.RpcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

class AuthInputTest {
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);

  AuthService service() {
    return new AuthService(
        jdbc,
        mock(StringRedisTemplate.class),
        new RpcClient(new ObjectMapper(), "01234567890123456789012345678901"),
        new MockEnvironment().withProperty("JWT_SECRET", "01234567890123456789012345678901"),
        mock(PlatformTransactionManager.class));
  }

  @Test
  void nullLoginIsAControlledAuthenticationFailure() {
    var error = assertThrows(DomainException.class, () -> service().login(null));
    assertEquals("UNAUTHENTICATED", error.getCode());
    verifyNoInteractions(jdbc);
  }

  @Test
  void oversizedUtf8PasswordIsRejectedBeforeDatabaseAndBcrypt() {
    var error =
        assertThrows(
            DomainException.class,
            () -> service().login(new AuthenticateInput("a@b.co", "я".repeat(37))));
    assertEquals("UNAUTHENTICATED", error.getCode());
    verifyNoInteractions(jdbc);
  }

  @Test
  void nullRegistrationIsAControlledValidationFailure() {
    var error = assertThrows(DomainException.class, () -> service().register(null));
    assertEquals("BAD_REQUEST", error.getCode());
    verifyNoInteractions(jdbc);
  }
}
