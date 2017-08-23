package io.airbrake.javabrake;

import static org.junit.Assert.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import org.junit.Test;
import java.io.IOException;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;

public class NotifierTest {
  @Rule public WireMockRule wireMockRule = new WireMockRule();

  Notifier notifier = new Notifier(0, "");
  Throwable exc = new IOException("hello from Java");

  @Test
  public void testBuildNotice() {
    Notice notice = this.notifier.buildNotice(this.exc);

    assertEquals(notice.errors.size(), 1);
    NoticeError err = notice.errors.get(0);
    assertEquals("java.io.IOException", err.type);
    assertEquals("hello from Java", err.message);

    NoticeStackRecord record = err.backtrace.get(0);
    assertEquals("<init>", record.function);
    assertEquals("test/io/airbrake/javabrake/NotifierTest.class", record.file);
    assertEquals(15, record.line);

    String hostname = (String) notice.context.get("hostname");
    assertTrue(hostname != "");
  }

  @Test
  public void testFilterData() {
    this.notifier.addFilter(
        (Notice notice) -> {
          notice.setContext("environment", "test");
          return notice;
        });

    Notice notice = this.notifier.reportSync(this.exc);

    String env = (String) notice.context.get("environment");
    assertEquals("test", env);
  }

  @Test
  public void testFilterNull() {
    this.notifier.addFilter(
        (Notice notice) -> {
          return null;
        });

    Notice notice = this.notifier.reportSync(this.exc);
    assertNull(notice);
  }

  @Test
  public void testRateLimit() {
    String apiURL = "/api/v3/projects/0/notices";

    notifier.setHost("http://localhost:8080");
    long utime = System.currentTimeMillis() / 1000L;
    stubFor(
        post(urlEqualTo(apiURL))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("X-RateLimit-Reset", Long.toString(utime + 1000))
                    .withBody("{}")));

    for (int i = 0; i < 2; i++) {
      Notice notice = notifier.reportSync(this.exc);
      assertNotNull(notice.exception);
      assertEquals("java.io.IOException: IP is rate limited", notice.exception.toString());
    }

    verify(1, postRequestedFor(urlEqualTo(apiURL)));
  }
}
