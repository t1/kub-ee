package com.github.t1.kubee.entity;

public enum DeploymentStatus {
    running,
    /** not load balanced */
    unbalanced,
    stopped
}
