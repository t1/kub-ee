package com.github.t1.kubee.model;

import lombok.Builder;
import lombok.Value;

import java.net.URI;

@Value
@Builder
public class ReverseProxy {
    URI from;
    int to;
}
