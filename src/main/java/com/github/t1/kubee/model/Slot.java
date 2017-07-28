package com.github.t1.kubee.model;

import com.github.t1.kubee.tools.yaml.YamlMapping;
import lombok.*;

import java.util.Comparator;

/**
 * Multiple JVMs on one machine can be differentiated by the ports they serve
 */
@Value
@Builder(builderMethodName = "internal_builder")
public class Slot implements Comparable<Slot> {
    public static final int DEFAULT_HTTP_PORT = 80;
    public static final int DEFAULT_HTTPS_PORT = 443;

    public static final Slot DEFAULT_SLOT = Slot.builder().build();

    /** The logical name of the slot */
    String name;

    /** The port number for http/https */
    int http, https;

    public static SlotBuilder builder() { return internal_builder().http(DEFAULT_HTTP_PORT).https(DEFAULT_HTTPS_PORT); }

    public static Slot from(String name, YamlMapping value) {
        SlotBuilder builder = builder().name(name);
        value.get("http").ifPresent(node -> builder.http(node.asInt()));
        value.get("https").ifPresent(node -> builder.https(node.asInt()));
        return builder.build();
    }

    @Override public int compareTo(Slot that) {
        return Comparator.comparing(Slot::getName)
                         .thenComparing(Slot::getHttp)
                         .thenComparing(Slot::getHttps)
                         .compare(this, that);
    }
}
