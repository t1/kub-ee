package com.github.t1.metadeployer.model;

import lombok.*;

import java.net.URI;

@Value
@Builder
public class ReverseProxy {
    URI from, to;
}
