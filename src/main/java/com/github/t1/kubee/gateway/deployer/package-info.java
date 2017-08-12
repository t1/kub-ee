@DependsUpon(packagesOf = {
        com.github.t1.kubee.model.Deployment.class,
        com.github.t1.kubee.tools.http.ProblemDetail.class,

        com.fasterxml.jackson.core.JsonGenerator.class,
        com.fasterxml.jackson.core.type.TypeReference.class,
        com.fasterxml.jackson.databind.ObjectMapper.class,
        com.fasterxml.jackson.dataformat.yaml.YAMLFactory.class,
}) package com.github.t1.kubee.gateway.deployer;

import com.github.t1.testtools.DependsUpon;
