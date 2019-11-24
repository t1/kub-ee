package com.github.t1.kubee.boundary.gateway.ingress;

import com.github.t1.kubee.entity.Stage;

import java.util.function.Function;

public class IngressFactory {
    public static Function<Stage, Ingress> BUILDER = NginxIngress::new;

    public static Ingress ingress(Stage stage) { return BUILDER.apply(stage); }
}
