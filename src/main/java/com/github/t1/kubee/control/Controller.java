package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.gateway.deployer.DeployerGateway.BadDeployerGatewayException;
import com.github.t1.kubee.gateway.health.HealthGateway;
import com.github.t1.kubee.gateway.loadbalancer.LoadBalancerGateway;
import com.github.t1.kubee.model.*;
import com.github.t1.kubee.model.Audits.Audit;
import com.github.t1.kubee.model.Audits.Audit.Change;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static com.github.t1.kubee.model.VersionStatus.*;
import static com.github.t1.kubee.tools.http.ProblemDetail.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

@Slf4j
@Stateless
public class Controller {
    @SuppressWarnings("SpellCheckingInspection") private static final String UNKNOWN_HOST_SUFFIX
            = ": nodename nor servname provided, or not known";

    @Inject List<Cluster> clusters;
    @Inject LoadBalancerGateway loadBalancing;
    @Inject DeployerGateway deployer;
    @Inject HealthGateway healthGateway;


    public Stream<Cluster> clusters() { return clusters.stream(); }

    public Stream<LoadBalancer> loadBalancers(Stream<Stage> stages) {
        return stages.flatMap(stage -> loadBalancing.loadBalancers(stage));
    }

    public Stream<ReverseProxy> reverseProxies(Stream<Stage> stages) {
        return stages.flatMap(stage -> loadBalancing.reverseProxies(stage));
    }


    public Stream<Deployment> fetchDeploymentsOn(ClusterNode node) {
        log.debug("fetch deployments from {}:", node);
        return fetchDeployablesFrom(node)
                .peek(deployable -> log.debug("  - {}", deployable.getName()));
    }

    private Stream<Deployment> fetchDeployablesFrom(ClusterNode node) {
        try {
            return deployer.fetchDeployablesFrom(node);
        } catch (Exception e) {
            String error = errorString(e);
            log.debug("deployer not found on {}: {}", node, error);
            return Stream.of(Deployment
                    .builder()
                    .name("-")
                    .groupId("-")
                    .artifactId("-")
                    .type("-")
                    .version("-")
                    .error(error)
                    .node(node)
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
        if (out.startsWith(BadDeployerGatewayException.class.getName() + ": "))
            out = "bad deployer gateway";
        if (out.startsWith(UnknownHostException.class.getName() + ": "))
            out = "unknown host";
        if (out.equals("Connection refused (Connection refused)"))
            out = "connection refused";
        return out;
    }


    public List<Version> fetchVersions(ClusterNode node, Deployment deployment) {
        return doFetchVersions(node, deployment).stream().map(s -> toVersion(deployment, s)).collect(toList());
    }

    private List<String> doFetchVersions(ClusterNode node, Deployment deployment) {
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


    public void deploy(DeploymentId id, String versionAfter) {
        ClusterNode node = id.node(clusters());
        String name = id.deploymentName();
        deploy(node, name, versionAfter);
    }

    private void deploy(ClusterNode node, String name, String versionAfter) {
        String versionBefore = deployer.fetchVersion(name, node);

        boolean healthyBefore = healthGateway.fetch(node, name);
        if (!healthyBefore)
            log.info("{}@{} on {} is not healthy before deploy", name, versionBefore, node);

        try {
            if (versionAfter.equals(versionBefore)) {
                log.info("redeploy {} @ {} on {}", name, versionBefore, node);
                undeploy(node, name);
            } else {
                log.info("update {} on {} from {} to {}", name, node, versionBefore, versionAfter);
                loadBalancing.from(name, node.getStage()).removeTarget(node.uri());
            }

            Audits audits = deployer.deploy(node, name, versionAfter);
            checkAudits(audits, "deploy", name, versionAfter);

            boolean healthyAfter = healthGateway.fetch(node, name);
            if (!healthyAfter) {
                log.error("{}@{} on {} is not healthy after deploy", name, versionBefore, node);
                if (healthyBefore)
                    throw new RuntimeException(name + "@" + versionAfter + " on " + node
                            + " flipped from healthy to unhealthy");
            }
        } catch (RuntimeException e) {
            log.warn("rollback {} on {} to {} failed: {}", name, node, versionBefore, e.getMessage());
            deployer.undeploy(node, name);
            deployer.deploy(node, name, versionBefore);
            throw e;
        } finally {
            loadBalancing.to(name, node.getStage()).addTarget(node.uri());
        }
    }

    public void undeploy(DeploymentId id) {
        ClusterNode node = id.node(clusters());
        String name = id.deploymentName();
        undeploy(node, name);
    }

    private void undeploy(ClusterNode node, String name) {
        loadBalancing.from(name, node.getStage()).removeTarget(node.uri());
        Audits audits = deployer.undeploy(node, name);
        checkAudits(audits, "undeploy", name, null);
    }

    private void checkAudits(Audits audits, String operation, String name, String version) {
        String errorPrefix = "expected " + operation + " audit for " + name;

        Optional<Audit> audit = audits.findDeployment(name);
        if (!audit.isPresent())
            throw badRequest().detail(errorPrefix).exception();

        String actualOperation = audit.get().getOperation();
        List<String> expectedOperations = ("deploy".equals(operation))
                ? asList("add", "change") : singletonList("remove");
        if (!expectedOperations.contains(actualOperation))
            throw badRequest().detail(errorPrefix + " to be in " + expectedOperations + " but is a " + actualOperation)
                              .exception();

        Optional<Change> versionChange = audit.get().findChange("version");
        if (!versionChange.isPresent())
            throw badRequest().detail(errorPrefix + " to change version.").exception();

        String newVersion = versionChange.get().getNewValue();
        if (!Objects.equals(newVersion, version)) {
            String message = errorPrefix + " to change version to " + version + ", but changed to " + newVersion + ".";
            throw badRequest().detail(message).exception();
        }
        log.debug("audit {} change {} {}", operation, name, versionChange.get());
    }
}
