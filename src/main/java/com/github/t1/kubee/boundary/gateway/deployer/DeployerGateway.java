package com.github.t1.kubee.boundary.gateway.deployer;

import com.github.t1.kubee.entity.Audits;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.tools.http.YamlHttpClient;
import com.github.t1.log.Logged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.github.t1.log.LogLevel.INFO;
import static java.util.stream.Collectors.toList;

@Logged(level = INFO)
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class DeployerGateway {
    private static final int MAX_RETRIES = 10;

    private final YamlHttpClient client;

    public String fetchVersion(ClusterNode node, String deployableName) {
        return fetchDeploymentsFrom(node.deployerUri())
            .stream()
            .filter(deployable -> deployable.getName().equals(deployableName))
            .findAny()
            .map(Deployable::getVersion)
            .orElse(null);
    }

    public List<String> fetchVersions(ClusterNode node, String groupId, String artifactId) {
        URI uri = UriBuilder.fromUri(node.deployerUri())
            .path("/repository/versions")
            .queryParam("artifactId", artifactId)
            .queryParam("groupId", groupId)
            .build();
        log.debug("GET versions from {}", uri);
        //noinspection unchecked
        return (List<String>) client.GET(uri, List.class);
    }

    public Stream<Deployment> fetchDeployables(ClusterNode node) {
        return fetchDeploymentsFrom(node.deployerUri())
            .stream()
            .map(deployable ->
                Deployment.builder()
                    .node(node)
                    .name(deployable.getName())
                    .groupId(orUnknown(deployable.getGroupId()))
                    .artifactId(orUnknown(deployable.getArtifactId()))
                    .version(orUnknown(deployable.getVersion()))
                    .type(orUnknown(deployable.getType()))
                    .error(deployable.getError())
                    .build());
    }

    private String orUnknown(String value) { return (value == null || value.isEmpty()) ? "unknown" : value; }

    List<Deployable> fetchDeploymentsFrom(URI uri) {
        log.debug("GET deployments from {}", uri);
        DeployerResponse deployerResponse = client.GET(uri, DeployerResponse.class);
        return toDeployableList(deployerResponse);
    }

    private List<Deployable> toDeployableList(DeployerResponse deployerResponse) {
        return deployerResponse
            .getDeployables()
            .entrySet()
            .stream()
            .map(this::flatten)
            .collect(toList());
    }


    private Deployable flatten(Map.Entry<String, Deployable> entry) {
        Deployable deployable = entry.getValue();
        deployable.setName(entry.getKey());
        return deployable;
    }


    public Audits deploy(ClusterNode node, String deploymentName, String version) {
        return postDeployer(node, deploymentName, "version", version);
    }

    public Audits undeploy(ClusterNode node, String deploymentName) {
        return postDeployer(node, deploymentName, "state", "undeployed");
    }

    private Audits postDeployer(ClusterNode node, String deploymentName, String key, String value) {
        URI uri = node.deployerUri();
        String parameterName = deploymentName + "." + key;
        log.debug("POST {}={} to {}", parameterName, value, uri);
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return client.POST(uri, new Form(parameterName, value), Audits::parseYaml);
            } catch (ProcessingException e) {
                if (e.getCause().getClass().getName().equals("org.apache.http.NoHttpResponseException")
                    && e.getCause().getMessage().endsWith(" failed to respond")) {
                    log.warn(e.getCause().getMessage() + ". retry " + (i + 1) + " of " + (MAX_RETRIES - 1));
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("failed " + MAX_RETRIES + " times");
    }
}
