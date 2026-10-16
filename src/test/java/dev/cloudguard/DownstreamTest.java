package dev.cloudguard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.web.client.*;
import java.util.concurrent.atomic.AtomicInteger;
class DownstreamTest {
 Downstream subject(){return new Downstream(RestClient.builder(),new SimpleMeterRegistry(),"http://localhost:1");}
 @Test void retriesTransientFailures(){var d=subject();var n=new AtomicInteger();assertEquals("ok",d.execute(()->{if(n.incrementAndGet()==1)throw new ResourceAccessException("timeout");return "ok";}));assertEquals(2,n.get());}
 @Test void doesNotRetryProgrammingErrors(){var d=subject();var n=new AtomicInteger();assertThrows(IllegalArgumentException.class,()->d.execute(()->{n.incrementAndGet();throw new IllegalArgumentException();}));assertEquals(1,n.get());}
 @Test void opensThenRecovers() throws Exception {var d=subject();for(int i=0;i<4;i++)assertThrows(RuntimeException.class,()->d.execute(()->{throw new ResourceAccessException("down");}));assertEquals("OPEN",d.breaker.getState().name());assertThrows(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,()->d.execute(()->"ok"));Thread.sleep(2100);assertEquals("ok",d.execute(()->"ok"));assertEquals("ok",d.execute(()->"ok"));assertEquals("CLOSED",d.breaker.getState().name());}
}
