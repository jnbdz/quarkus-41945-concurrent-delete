package org.acme.hibernate.orm.panache;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Deletes many fruits at the same time with deleteById, as in quarkusio/quarkus#41945.
 */
@QuarkusTest
public class ConcurrentDeleteTest {

    static final int ROUNDS = 5;
    static final int PER_ROUND = 200;
    static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @TestHTTPResource("fruits")
    URL fruits;

    @Test
    public void concurrentDeleteById() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).executor(pool).connectTimeout(Duration.ofSeconds(10)).build();
        Map<String, Integer> statuses = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        int hung = 0;
        for (int round = 0; round < ROUNDS; round++) {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < PER_ROUND; i++) {
                HttpResponse<String> r = client.send(HttpRequest.newBuilder(fruits.toURI())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"f-" + round + "-" + i + "\"}")).build(),
                        HttpResponse.BodyHandlers.ofString());
                Matcher m = ID.matcher(r.body());
                assertThat(m.find()).as("create returned %s %s", r.statusCode(), r.body()).isTrue();
                ids.add(Long.valueOf(m.group(1)));
            }
            List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
            for (Long id : ids) {
                calls.add(client.sendAsync(HttpRequest.newBuilder(URI.create(fruits + "/" + id)).DELETE()
                        .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString()));
            }
            for (CompletableFuture<HttpResponse<String>> c : calls) {
                try {
                    HttpResponse<String> r = c.get(40, TimeUnit.SECONDS);
                    statuses.merge(String.valueOf(r.statusCode()), 1, Integer::sum);
                    if (r.statusCode() != 204 && errors.size() < 5) {
                        errors.add(r.statusCode() + " " + r.body());
                    }
                } catch (java.util.concurrent.TimeoutException e) {
                    hung++;
                    statuses.merge("no-answer-40s", 1, Integer::sum);
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    String k = cause instanceof java.net.http.HttpTimeoutException ? "request-timeout-30s" : "error/" + cause.getClass().getSimpleName();
                    hung++;
                    statuses.merge(k, 1, Integer::sum);
                    if (errors.size() < 5) {
                        errors.add(k + ": " + cause.getMessage());
                    }
                }
            }
        }
        pool.shutdownNow();
        System.out.println("RESULT41945 statuses=" + statuses + " hung=" + hung + " firstErrors=" + errors);
        assertThat(statuses).as("first errors: %s", errors).containsOnlyKeys("204");
    }
}
