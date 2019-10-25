package com.github.t1.kubee.boundary.gateway.deployer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deployable {
    @JsonProperty
    private String name;
    @JsonProperty("group-id")
    private String groupId;
    @JsonProperty("artifact-id")
    private String artifactId;
    @JsonProperty
    private String type;
    @JsonProperty
    private String version;
    @JsonProperty
    private String error;
}
