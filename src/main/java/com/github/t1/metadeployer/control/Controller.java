package com.github.t1.metadeployer.control;

import com.github.t1.metadeployer.gateway.deployer.DeployerGateway;
import com.github.t1.metadeployer.gateway.loadbalancer.LoadBalancerGateway;
import com.github.t1.metadeployer.model.*;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.net.*;
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

    @Inject LoadBalancerGateway loadBalancing;
    @Inject DeployerGateway deployer;

    public List<LoadBalancer> getLoadBalancers() {
        return loadBalancing.getLoadBalancers();
    }

    public List<ReverseProxy> getReverseProxies() {
        return loadBalancing.getReverseProxies();
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


    public void deploy(URI uri, String deployableName, String version) {
        loadBalancing.removeFromLB(uri, deployableName);
        deployer.deploy(uri, deployableName, version);
        loadBalancing.addToLB(uri, deployableName);
    }

    public void undeploy(URI uri, String deployableName) {
        loadBalancing.removeFromLB(uri, deployableName);
        deployer.undeploy(uri, deployableName);
    }
}
