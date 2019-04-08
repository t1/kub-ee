package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.boundary.gateway.health.HealthGateway;
import com.github.t1.kubee.entity.Audits;
import com.github.t1.kubee.entity.Audits.Audit;
import com.github.t1.kubee.entity.Audits.Audit.Change;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.entity.DeploymentId;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.LoadBalancer;
import com.github.t1.kubee.entity.ReverseProxy;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.entity.Version;
import com.github.t1.kubee.tools.http.WebApplicationApplicationException;
import com.github.t1.kubee.tools.http.YamlHttpClient.BadGatewayException;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static com.github.t1.kubee.boundary.gateway.ingress.Ingress.ingress;
import static com.github.t1.kubee.entity.VersionStatus.deployed;
import static com.github.t1.kubee.entity.VersionStatus.undeployed;
import static com.github.t1.kubee.tools.http.ProblemDetail.badRequest;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@Slf4j
@Stateless
public class Controller {
    @SuppressWarnings("SpellCheckingInspection") private static final String UNKNOWN_HOST_SUFFIX
        = ": nodename nor servname provided, or not known";

    @Inject List<Cluster> clusters;
    @Inject DeployerGateway deployer;
    @Inject HealthGateway healthGateway;


    public Stream<Cluster> clusters() { return clusters.stream(); }

    public Stream<LoadBalancer> loadBalancers(Stream<Stage> stages) {
        return stages.flatMap(stage ->
            ingress(stage).loadBalancers().map(config -> LoadBalancer.builder()
                .name(config.name())
                .method(config.method())
                .servers(config.endpoints().map(Endpoint::toString).collect(toList()))
                .build()));
    }

    public Stream<ReverseProxy> reverseProxies(Stream<Stage> stages) {
        return stages.flatMap(stage ->
            ingress(stage).reverseProxies().map(config -> ReverseProxy.builder()
                .from(URI.create("http://" + config.name() + ":" + config.listen()))
                .to(config.getPort())
                .build()));
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
            log.debug("GET from deployer on {} threw: {}", node, error);
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
            || out.startsWith(WebApplicationApplicationException.class.getName() + ": ")
            || out.startsWith(RuntimeException.class.getName() + ": "))
            out = out.substring(out.indexOf(": ") + 2);
        if (out.endsWith(UNKNOWN_HOST_SUFFIX))
            out = out.substring(0, out.length() - UNKNOWN_HOST_SUFFIX.length());
        if (out.startsWith(NotFoundException.class.getName() + ": "))
            out = "deployer not found";
        if (out.startsWith(BadGatewayException.class.getName() + ": "))
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
            } else if (versionBefore != null) {
                log.info("update {} on {} from {} to {}", name, node, versionBefore, versionAfter);
                ingress(node.getStage()).removeFromLoadBalancer(name, node);
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
            // TODO this log is wrong
            log.warn("rollback {} on {} to {} failed: {}", name, node, versionBefore, e.getMessage());
            deployer.undeploy(node, name);
            deployer.deploy(node, name, versionBefore);
            throw e;
        } finally {
            // TODO don't add when the deploy failed!
            ingress(node.getStage()).addToLoadBalancerFor(name, node);
        }
    }

    public void undeploy(DeploymentId id) {
        ClusterNode node = id.node(clusters());
        String name = id.deploymentName();
        undeploy(node, name);
    }

    private void undeploy(ClusterNode node, String name) {
        ingress(node.getStage()).removeFromLoadBalancer(name, node);
        Audits audits = deployer.undeploy(node, name);
        checkAudits(audits, "undeploy", name, null);
    }

    private void checkAudits(Audits audits, String operation, String name, String version) {
        String errorPrefix = "expected " + operation + " audit for " + name;

        Audit audit = audits.findDeployment(name)
            .orElseThrow(() -> badRequest().detail(errorPrefix).exception());

        String actualOperation = audit.getOperation();
        List<String> expectedOperations = ("deploy".equals(operation))
            ? asList("add", "change") : singletonList("remove");
        if (!expectedOperations.contains(actualOperation))
            throw badRequest().detail(errorPrefix + " to be in " + expectedOperations + " but is a " + actualOperation).exception();

        Change versionChange = audit.findChange("version")
            .orElseThrow(() -> badRequest().detail(errorPrefix + " to change version.").exception());

        String newVersion = versionChange.getNewValue();
        if (!Objects.equals(newVersion, version))
            throw badRequest().detail(errorPrefix + " to change version to " + version + ", but changed to " + newVersion + ".").exception();
        log.debug("audit {} change {} {}", operation, name, versionChange);
    }
}
