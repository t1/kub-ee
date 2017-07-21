package com.github.t1.metadeployer.model;

import lombok.*;

import java.util.List;

@Value
@Builder
public class LoadBalancer {
    String name;
    String method;
    @Singular List<String> servers;
}
