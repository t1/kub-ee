package com.github.t1.metadeployer.model;

import lombok.*;

import java.net.URI;

@Value
@Builder
public class LoadBalancer {
    URI from, to;
}
