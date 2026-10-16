package dev.cloudguard;
import java.time.Duration;
import java.util.function.Supplier;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
@Service
public class Downstream {
 final CircuitBreaker breaker;
 final Retry retry;
 final RestClient client;
 public Downstream(RestClient.Builder builder, MeterRegistry metrics, @Value("${downstream.url}") String url) {
  var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(200); factory.setReadTimeout(300);
  client=builder.requestFactory(factory).baseUrl(url).build();
  breaker=CircuitBreaker.of("dependency",CircuitBreakerConfig.custom().slidingWindowSize(6).minimumNumberOfCalls(4).failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(2)).permittedNumberOfCallsInHalfOpenState(2).build());
  retry=Retry.of("dependency",RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(50)).retryExceptions(org.springframework.web.client.ResourceAccessException.class,org.springframework.web.client.HttpServerErrorException.class).build());
  breaker.getEventPublisher().onStateTransition(e->org.slf4j.LoggerFactory.getLogger(getClass()).warn("circuit_transition={}",e.getStateTransition()));
  metrics.gauge("cloudguard.circuit.open",breaker,b->b.getState()==CircuitBreaker.State.OPEN?1:0);
  retry.getEventPublisher().onRetry(e->metrics.counter("cloudguard.downstream.retries").increment());
 }
 public String fetch() { return execute(()->client.get().uri("/data").retrieve().body(String.class)); }
 String execute(Supplier<String> action) { return CircuitBreaker.decorateSupplier(breaker,Retry.decorateSupplier(retry,action)).get(); }
}
