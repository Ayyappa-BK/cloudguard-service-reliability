package dev.cloudguard;
import java.util.Map;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class Api {
 private final Downstream downstream;
 private final MeterRegistry metrics;
 public Api(Downstream downstream,MeterRegistry metrics){this.downstream=downstream;this.metrics=metrics;for(String outcome:new String[]{"success","error"}){metrics.counter("cloudguard.requests","outcome",outcome);metrics.timer("cloudguard.request.latency","outcome",outcome);}}
 @GetMapping("/api/check") public ResponseEntity<Map<String,String>> check(){
  long start=System.nanoTime();String outcome="error";
  try {String value=downstream.fetch();outcome="success";return ResponseEntity.ok(Map.of("status","ok","dependency",value));}
  catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("dependency_failed type={}",e.getClass().getSimpleName());return ResponseEntity.status(503).body(Map.of("status","unavailable"));}
  finally {metrics.counter("cloudguard.requests","outcome",outcome).increment();metrics.timer("cloudguard.request.latency","outcome",outcome).record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS);}
 }
}
