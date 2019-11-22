package com.github.t1.kubee.entity;

import com.github.t1.kubee.tools.yaml.YamlMapping;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Comparator;

/**
 * Multiple JVMs on one machine can be differentiated by the ports they serve
 */
@Value
@Builder(builderMethodName = "internal_builder")
public class Slot implements Comparable<Slot> {
    public static final int DEFAULT_HTTP_PORT = 80;
    public static final int DEFAULT_HTTPS_PORT = 443;

    public static final Slot DEFAULT_SLOT = Slot.named(null);

    /** The logical name of the slot; can be null */
    String name;

    /** The port number for http/https */
    int http, https;

    public static Slot named(String name) { return new Slot(name, DEFAULT_HTTP_PORT, DEFAULT_HTTPS_PORT); }

    public Slot withOffset(int offset) { return new Slot(name, http + offset, https + offset); }

    public Slot withHttp(int http) { return new Slot(name, http, https); }

    public Slot withHttps(int https) { return new Slot(name, http, https); }


    public static Slot from(String name, YamlMapping value) {
        SlotBuilder builder = internal_builder().name(name);
        value.get("http").ifPresent(node -> builder.http(node.asInt()));
        value.get("https").ifPresent(node -> builder.https(node.asInt()));
        return builder.build();
    }

    @Override public int compareTo(@NonNull Slot that) {
        return Comparator.comparing(Slot::getName)
            .thenComparing(Slot::getHttp)
            .thenComparing(Slot::getHttps)
            .compare(this, that);
    }
}
