package com.github.t1.kubee.model;

import lombok.*;

@Data
@AllArgsConstructor
public class Version {
    private String name;
    private VersionStatus status;
}
