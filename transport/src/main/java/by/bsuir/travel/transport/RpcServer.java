package by.bsuir.travel.transport;

import by.bsuir.travel.contracts.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import io.grpc.*;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.slf4j.*;
import org.springframework.context.SmartLifecycle;

/** Exposes only registered Java ports through their generated protobuf service methods. */
public final class RpcServer implements SmartLifecycle {
  private static final Logger log = LoggerFactory.getLogger(RpcServer.class);
  private static final Context.Key<String> CORRELATION = Context.key("travel-correlation");

  private record Handler(RpcBindings.Binding binding, Object target) {}

  private final Map<String, Handler> handlers = new LinkedHashMap<>();
  private final int port;
  private final String key;
  private Server server;

  public RpcServer(ObjectMapper unused, int port, String key) {
    if (key == null || key.length() < 32)
      throw new IllegalArgumentException("Service key must have 32 characters");
    this.port = port;
    this.key = key;
  }

  public RpcServer expose(Class<?> api, Object target) {
    if (!api.isInstance(target))
      throw new IllegalArgumentException("Target does not implement " + api.getName());
    var bindings =
        RpcBindings.ALL.stream().filter(b -> b.javaMethod().getDeclaringClass() == api).toList();
    if (bindings.size() != api.getMethods().length)
      throw new IllegalArgumentException("Unsupported RPC port " + api.getName());
    for (var binding : bindings) {
      if (handlers.putIfAbsent(binding.rpc().getFullMethodName(), new Handler(binding, target))
          != null)
        throw new IllegalArgumentException(
            "Duplicate RPC operation " + binding.rpc().getFullMethodName());
    }
    return this;
  }

  private void invoke(Handler handler, Message request, StreamObserver<Message> observer) {
    String cid = CORRELATION.get();
    MDC.put("correlationId", cid);
    var binding = handler.binding();
    try {
      if (Context.current().isCancelled()) {
        observer.onError(Status.CANCELLED.asRuntimeException());
        return;
      }
      Object[] args;
      try {
        args = binding.arguments().apply(request);
      } catch (RuntimeException invalid) {
        throw new IllegalArgumentException("Invalid protobuf arguments", invalid);
      }
      Object result = binding.javaMethod().invoke(handler.target(), args);
      Message response;
      try {
        response = binding.response().apply(result);
      } catch (RuntimeException invalid) {
        throw new IllegalStateException("Cannot encode RPC response", invalid);
      }
      observer.onNext(response);
      observer.onCompleted();
      log.info(
          "grpc server method={} correlationId={} success", binding.rpc().getFullMethodName(), cid);
    } catch (Exception raw) {
      Throwable error = raw instanceof InvocationTargetException ? raw.getCause() : raw;
      String code =
          error instanceof DomainException d
              ? d.getCode()
              : error instanceof IllegalArgumentException ? "BAD_REQUEST" : "INTERNAL_ERROR";
      var trailers = new Metadata();
      trailers.put(RpcClient.CODE, code);
      log.error(
          "grpc server method={} correlationId={} code={}",
          binding.rpc().getFullMethodName(),
          cid,
          code,
          error);
      observer.onError(
          status(code)
              .withDescription(
                  error instanceof DomainException
                      ? error.getMessage()
                      : code.equals("BAD_REQUEST")
                          ? "Invalid RPC arguments"
                          : "Service operation failed")
              .asRuntimeException(trailers));
    } finally {
      MDC.remove("correlationId");
    }
  }

  private static Status status(String code) {
    return switch (code) {
      case "BAD_REQUEST" -> Status.INVALID_ARGUMENT;
      case "NOT_FOUND" -> Status.NOT_FOUND;
      case "UNAUTHENTICATED" -> Status.UNAUTHENTICATED;
      case "FORBIDDEN" -> Status.PERMISSION_DENIED;
      case "CONFLICT" -> Status.ABORTED;
      case "INSUFFICIENT_SEATS" -> Status.RESOURCE_EXHAUSTED;
      case "SERVICE_UNAVAILABLE" -> Status.UNAVAILABLE;
      case "INTERNAL_ERROR" -> Status.INTERNAL;
      default -> Status.FAILED_PRECONDITION;
    };
  }

  public void start() {
    try {
      ServerInterceptor auth =
          new ServerInterceptor() {
            public <Q, S> ServerCall.Listener<Q> interceptCall(
                ServerCall<Q, S> call, Metadata headers, ServerCallHandler<Q, S> next) {
              String supplied = headers.get(RpcClient.KEY);
              if (supplied == null
                  || !MessageDigest.isEqual(
                      key.getBytes(StandardCharsets.UTF_8),
                      supplied.getBytes(StandardCharsets.UTF_8))) {
                call.close(Status.UNAUTHENTICATED, new Metadata());
                return new ServerCall.Listener<>() {};
              }
              String cid = headers.get(RpcClient.CORRELATION);
              if (cid == null || !cid.matches("[a-zA-Z0-9-]{1,64}"))
                cid = UUID.randomUUID().toString();
              return Contexts.interceptCall(
                  Context.current().withValue(CORRELATION, cid), call, headers, next);
            }
          };
      var definitions = new LinkedHashMap<String, ServerServiceDefinition.Builder>();
      for (var handler : handlers.values()) {
        var method = handler.binding().rpc();
        definitions
            .computeIfAbsent(method.getServiceName(), ServerServiceDefinition::builder)
            .addMethod(
                method,
                ServerCalls.asyncUnaryCall(
                    (request, observer) -> invoke(handler, request, observer)));
      }
      var builder = ServerBuilder.forPort(port);
      definitions
          .values()
          .forEach(
              definition ->
                  builder.addService(ServerInterceptors.intercept(definition.build(), auth)));
      server = builder.build().start();
      log.info("gRPC listening port={}", server.getPort());
    } catch (Exception e) {
      throw new IllegalStateException("gRPC start failed", e);
    }
  }

  int boundPort() {
    return server.getPort();
  }

  public void stop() {
    if (server != null) server.shutdownNow();
  }

  public boolean isRunning() {
    return server != null && !server.isShutdown();
  }
}
