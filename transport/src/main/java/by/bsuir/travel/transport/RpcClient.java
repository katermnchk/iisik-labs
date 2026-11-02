package by.bsuir.travel.transport;

import by.bsuir.travel.contracts.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import io.grpc.*;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.*;

public final class RpcClient implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(RpcClient.class);
  static final Metadata.Key<String> KEY =
      Metadata.Key.of("x-service-key", Metadata.ASCII_STRING_MARSHALLER);
  static final Metadata.Key<String> CODE =
      Metadata.Key.of("domain-code", Metadata.ASCII_STRING_MARSHALLER);
  static final Metadata.Key<String> CORRELATION =
      Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
  private final String key;
  private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

  public RpcClient(ObjectMapper m, String key) {
    if (key == null || key.length() < 32)
      throw new IllegalArgumentException("Service key must have 32 characters");
    this.key = key;
  }

  public <T> T proxy(String target, Class<T> api) {
    var bindings =
        RpcBindings.ALL.stream()
            .filter(b -> b.javaMethod().getDeclaringClass() == api)
            .collect(java.util.stream.Collectors.toMap(RpcBindings.Binding::javaMethod, b -> b));
    if (bindings.size() != api.getMethods().length)
      throw new IllegalArgumentException("Unsupported RPC contract: " + api.getName());
    return api.cast(
        Proxy.newProxyInstance(
            api.getClassLoader(),
            new Class<?>[] {api},
            (p, m, args) -> {
              if (m.getDeclaringClass() == Object.class)
                return switch (m.getName()) {
                  case "toString" -> "gRPC " + target + " " + api.getSimpleName();
                  case "hashCode" -> System.identityHashCode(p);
                  case "equals" -> p == args[0];
                  default -> null;
                };
              var channel =
                  channels.computeIfAbsent(
                      target,
                      t ->
                          ManagedChannelBuilder.forTarget(t).usePlaintext().disableRetry().build());
              var headers = new Metadata();
              headers.put(KEY, key);
              String cid =
                  Optional.ofNullable(MDC.get("correlationId"))
                      .orElseGet(() -> UUID.randomUUID().toString());
              headers.put(CORRELATION, cid);
              var binding = bindings.get(m);
              Message request;
              try {
                request = binding.request().apply(args);
              } catch (IllegalArgumentException e) {
                throw new DomainException("BAD_REQUEST", "Invalid RPC input");
              }
              boolean safe = binding.retryable();
              int attempts = safe ? 3 : 1;
              for (int n = 1; ; n++) {
                long start = System.nanoTime();
                try {
                  var authenticated =
                      ClientInterceptors.intercept(
                          channel, MetadataUtils.newAttachHeadersInterceptor(headers));
                  var result =
                      ClientCalls.blockingUnaryCall(
                          authenticated,
                          binding.rpc(),
                          CallOptions.DEFAULT.withDeadlineAfter(2000, TimeUnit.MILLISECONDS),
                          request);
                  log.info(
                      "grpc client target={} method={} correlationId={} attempt={} durationMs={}",
                      target,
                      m.getName(),
                      cid,
                      n,
                      (System.nanoTime() - start) / 1000000);
                  try {
                    return binding.result().apply(result);
                  } catch (RuntimeException invalid) {
                    throw new DomainException(
                        "INTERNAL_ERROR", "Invalid dependent service response");
                  }
                } catch (StatusRuntimeException e) {
                  var code = e.getStatus().getCode();
                  log.warn(
                      "grpc failure target={} method={} correlationId={} attempt={} status={}",
                      target,
                      m.getName(),
                      cid,
                      n,
                      code);
                  if (safe
                      && n < attempts
                      && (code == Status.Code.UNAVAILABLE
                          || code == Status.Code.DEADLINE_EXCEEDED)) {
                    try {
                      Thread.sleep(100L * n);
                    } catch (InterruptedException x) {
                      Thread.currentThread().interrupt();
                      throw new DomainException("SERVICE_UNAVAILABLE", "Operation interrupted");
                    }
                    continue;
                  }
                  String domain = e.getTrailers() == null ? null : e.getTrailers().get(CODE);
                  throw new DomainException(
                      domain == null ? domainCode(code) : domain,
                      domain == null
                          ? "Dependent service unavailable"
                          : e.getStatus().getDescription());
                }
              }
            }));
  }

  private static String domainCode(Status.Code status) {
    return switch (status) {
      case INVALID_ARGUMENT -> "BAD_REQUEST";
      case NOT_FOUND -> "NOT_FOUND";
      case UNAUTHENTICATED -> "UNAUTHENTICATED";
      case PERMISSION_DENIED -> "FORBIDDEN";
      case ABORTED, ALREADY_EXISTS -> "CONFLICT";
      case RESOURCE_EXHAUSTED -> "INSUFFICIENT_SEATS";
      case FAILED_PRECONDITION -> "INVALID_STATE";
      case INTERNAL, UNKNOWN, UNIMPLEMENTED, DATA_LOSS -> "INTERNAL_ERROR";
      default -> "SERVICE_UNAVAILABLE";
    };
  }

  public void close() {
    channels.values().forEach(ManagedChannel::shutdownNow);
  }
}
