package dev.cloudguard;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"management.server.port=0","management.otlp.tracing.export.enabled=false"})
class ServiceTest {
 static volatile String traceHeader;static volatile int status=200;static volatile boolean slow=false;
 static final HttpServer target;
 static {try{target=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);target.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());target.createContext("/data",e->{try{traceHeader=e.getRequestHeaders().getFirst("traceparent");if(slow)Thread.sleep(1000);e.sendResponseHeaders(status,2);e.getResponseBody().write("ok".getBytes());}catch(Exception ignored){}finally{e.close();}});target.start();}catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("downstream.url",()->"http://127.0.0.1:"+target.getAddress().getPort());}
 @Autowired TestRestTemplate http;
 @Autowired Downstream downstream;
 @org.springframework.boot.test.web.server.LocalManagementPort int managementPort;
 @Test void healthAndMetrics(){for(String path:new String[]{"health/liveness","health/readiness","prometheus"})assertEquals(200,http.getForEntity("http://localhost:"+managementPort+"/actuator/"+path,String.class).getStatusCode().value());}
 @Test void propagatesTraceContext(){http.getForEntity("/api/check",String.class);assertNotNull(traceHeader);assertTrue(traceHeader.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));}

 @BeforeEach void reset(){status=200;slow=false;downstream.breaker.reset();}
 @AfterAll static void stop(){target.stop(0);}
 @Test void servesRealDownstream(){assertEquals(200,http.getForEntity("/api/check",String.class).getStatusCode().value());}
 @Test void propagatesFailureAndRecovers() throws Exception {status=503;for(int i=0;i<4;i++)assertEquals(503,http.getForEntity("/api/check",String.class).getStatusCode().value());assertEquals("OPEN",downstream.breaker.getState().name());status=200;Thread.sleep(2100);for(int i=0;i<2;i++)assertEquals(200,http.getForEntity("/api/check",String.class).getStatusCode().value());assertEquals("CLOSED",downstream.breaker.getState().name());}
 @Test void timeoutIsBounded(){slow=true;long start=System.nanoTime();assertEquals(503,http.getForEntity("/api/check",String.class).getStatusCode().value());assertTrue((System.nanoTime()-start)/1e6<1200,"two 300ms attempts plus backoff must fit budget");}
}
