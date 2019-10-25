package com.github.t1.kubee.boundary.gateway.deployer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class DeployerResponse {
    @JsonProperty
    private Map<String, Deployable> deployables;
}
