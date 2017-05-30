package com.github.t1.metadeployer.model;

import com.github.t1.metadeployer.tools.yaml.YamlMapping;
import lombok.*;

@Value
@Builder
public class Slot {
    public static final int DEFAULT_HTTP_PORT = 80;
    public static final int DEFAULT_HTTPS_PORT = 443;

    public static final Slot DEFAULT_SLOT = Slot.builder().http(DEFAULT_HTTP_PORT).https(DEFAULT_HTTPS_PORT).build();

    String name;
    int http, https;

    public static Slot from(String name, YamlMapping value) {
        return builder()
                .name(name)
                .http(value.get("http").asIntOr(DEFAULT_HTTP_PORT))
                .https(value.get("https").asIntOr(DEFAULT_HTTPS_PORT))
                .build();
    }
}
