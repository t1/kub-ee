package com.github.t1.kubee.lb.nginx;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.time.*;
import java.util.*;

import static java.time.Instant.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.Response.Status.Family.*;

public class LoadGenerator implements Runnable {
    public static void main(String[] args) {
        LoadGenerator generator = new LoadGenerator();
        System.out.println("warm up");
        generator.warmUp();
        Statistics statistics = new Statistics();
        System.out.println("start");
        Instant start = now();
        for (int i = 0; i < 300; i++) {
            generator.run();
            statistics.add(generator.time, generator.result);
            // System.out.println(generator.time.toMillis() + ": " + generator.result);
            if (i % 10 == 0)
                System.out.print(".");
            // Thread.sleep(3);
        }
        System.out.println("\ntotal: " + Duration.between(start, now()).toMillis());
        System.out.println(statistics);
    }

    private final Client client = ClientBuilder.newClient();
    private Duration time;
    private String result;

    private void warmUp() {
        for (int i = 0; i < 10; i++)
            run();
    }

    @Override public void run() {
        WebTarget target = client.target("http://ping?sleep=100");
        Instant start = now();
        this.result = fetch(target);
        this.time = Duration.between(start, now());
    }

    private String fetch(WebTarget target) {
        Response response = null;
        try {
            // response = completable(target.request().async().get()).get(10, MILLISECONDS);
            response = target.request("application/yaml").get();
            // System.out.println(response.getHeaderString("Content-Type"));
            if (response.getStatusInfo().getFamily() == SUCCESSFUL)
                return "success";
            // System.out.println(response.readEntity(String.class));
            return "failed " + response.getStatus() + " " + response.getStatusInfo();
        } catch (Exception e) {
            return "failed: " + e.getMessage();
        } finally {
            if (response != null)
                response.close();
        }
    }

    private static class Statistics {
        private int count;
        private long min = Integer.MAX_VALUE, avg = 0, max = 0;
        private Map<String, Integer> results = new HashMap<>();

        private void add(Duration time, String result) {
            ++count;
            long t = time.toMillis();
            min = Math.min(min, t);
            max = Math.max(max, t);
            results.merge(result, 1, (left, right) -> left + right);
        }

        @Override public String toString() {
            return ""
                    + "count: " + count + "\n"
                    + "min:   " + min + "\n"
                    + "avg:   " + avg + "\n"
                    + "max:   " + max + "\n"
                    + "results:\n"
                    + results.entrySet()
                             .stream()
                             .map(entry -> String.format("    %dx -> %s", entry.getValue(), entry.getKey()))
                             .collect(joining("\n"));
        }
    }
}
