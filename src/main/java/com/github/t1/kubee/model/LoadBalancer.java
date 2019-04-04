package com.github.t1.kubee.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class LoadBalancer {
    String name;
    String method;
    @Singular List<String> servers;
}
