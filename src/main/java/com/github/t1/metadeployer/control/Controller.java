package com.github.t1.metadeployer.control;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import com.github.t1.metadeployer.model.*;
import com.github.t1.nginx.NginxConfig;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.*;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.net.*;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static com.github.t1.metadeployer.model.VersionStatus.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

@Slf4j
@Stateless
public class Controller {
    private static final String UNKNOWN_HOST_SUFFIX = ": nodename nor servname provided, or not known";

    @Inject DeployerGateway deployer;

    public NginxConfig readNginxConfig() {
        return NginxConfig.readFrom(Paths.get("/usr/local/etc/nginx/nginx.conf").toUri());
    }

    private List<Deployable> fetchDeployablesFrom(ClusterNode node) {
        URI uri = node.deployerUri();
        try {
            return deployer.fetchDeployablesOn(uri);
        } catch (Exception e) {
            String error = errorString(e);
            log.debug("deployer not found on {}: {}: {}", node, uri, error);
            return singletonList(Deployable
                    .builder()
                    .name("-")
                    .groupId("-")
                    .artifactId("-")
                    .type("-")
                    .version("-")
                    .error(error)
                    .build());
        }
    }

    private static String errorString(Throwable e) {
        while (e.getCause() != null)
            e = e.getCause();
        String out = e.toString();
        while (out.startsWith(ExecutionException.class.getName() + ": ")
                || out.startsWith(ConnectException.class.getName() + ": ")
                || out.startsWith(RuntimeException.class.getName() + ": "))
            out = out.substring(out.indexOf(": ") + 2);
        if (out.endsWith(UNKNOWN_HOST_SUFFIX))
            out = out.substring(0, out.length() - UNKNOWN_HOST_SUFFIX.length());
        if (out.startsWith(NotFoundException.class.getName() + ": "))
            out = "deployer not found";
        if (out.startsWith(UnknownHostException.class.getName() + ": "))
            out = "unknown host";
        if (out.equals("Connection refused (Connection refused)"))
            out = "connection refused";
        return out;
    }

    public List<Version> fetchVersions(ClusterNode node, Deployment deployment) {
        return doFetch(node, deployment).stream().map(s -> toVersion(deployment, s)).collect(toList());
    }

    private List<String> doFetch(ClusterNode node, Deployment deployment) {
        String groupId = deployment.getGroupId();
        String artifactId = deployment.getArtifactId();
        try {
            return deployer.fetchVersions(node.deployerUri(), groupId, artifactId);
        } catch (NotFoundException e) {
            log.info("no versions found for {}:{} on {}", groupId, artifactId, node);
            return singletonList(deployment.getVersion());
        } catch (ProcessingException e) {
            if (e.getCause() instanceof UnknownHostException) {
                log.info("host not found: {} ({}:{})", node, groupId, artifactId);
                return singletonList(deployment.getVersion());
            }
            throw e;
        }
    }


    private Version toVersion(Deployment deployment, String version) {
        return new Version(version, version.equals(deployment.getVersion()) ? deployed : undeployed);
    }

    @Asynchronous
    public void startVersionDeploy(URI uri, String deployableName, String version) {
        deployer.startVersionDeploy(uri, deployableName, version);
    }

    public Stream<Deployment> fetchDeploymentsOn(ClusterNode node) {
        log.debug("fetch deployables from {}:", node);
        return fetchDeployablesFrom(node)
                .stream()
                .peek(deployable -> log.debug("  - {}", deployable.getName()))
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
}
