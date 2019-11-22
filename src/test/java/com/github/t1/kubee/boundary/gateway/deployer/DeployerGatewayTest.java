package com.github.t1.kubee.boundary.gateway.deployer;

import com.github.t1.kubee.entity.Audits;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.tools.http.YamlHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Form;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.PROD01;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DeployerGatewayTest {
    private static final String APP_NAME = "app-name";
    private static final String GROUP_ID = "com.github.t1";
    private static final String ARTIFACT_ID = "dummy";
    private static final URI HOST = URI.create("http://worker01:8080");
    private static final URI DEPLOYER_URI = HOST.resolve("/deployer");
    private static final URI VERSIONS_URI = HOST.resolve("/deployer/repository/versions?artifactId=" + ARTIFACT_ID + "&groupId=" + GROUP_ID);
    private static final List<String> VERSIONS = asList("1.0.1", "1.0.2", "1.0.3", "1.1.0", "2.0.0");
    private static final Deployment DEPLOYMENT = Deployment.builder()
        .node(PROD01).name(APP_NAME).groupId(GROUP_ID).artifactId(ARTIFACT_ID)
        .type("war").version("1.0.2").build();
    private static final Audits DEPLOY_AUDITS = Audits.parseYaml("" +
        "audits:\n" +
        "- !<deployable>\n" +
        "  operation: change\n" +
        "  changes:\n" +
        "  - name: checksum\n" +
        "    old-value: 52709cbc859e208dc8e540eb5c7047c316d9653f\n" +
        "    new-value: 9e29add9df1fa9540654c452dcbf0a2e47cc5330\n" +
        "  - name: version\n" +
        "    old-value: 1.0.1\n" +
        "    new-value: 1.0.3\n" +
        "  name: jolokia\n" +
        "processState: running");
    private static final Audits UNDEPLOY_AUDITS = Audits.parseYaml("" +
        "audits:\n" +
        "- !<deployable>\n" +
        "  operation: remove\n" +
        "  changes:\n" +
        "  - name: group-id\n" +
        "    old-value: " + GROUP_ID + "\n" +
        "  - name: artifact-id\n" +
        "    old-value: " + ARTIFACT_ID + "\n" +
        "  - name: version\n" +
        "    old-value: 1.0.1\n" +
        "  - name: type\n" +
        "    old-value: war\n" +
        "  - name: checksum\n" +
        "    old-value: 52709cbc859e208dc8e540eb5c7047c316d9653f\n" +
        "  name: jolokia\n" +
        "processState: running");

    @Mock YamlHttpClient yamlClient;

    @InjectMocks DeployerGateway gateway = new DeployerGateway();

    @AfterEach void noMore() { verifyNoMoreInteractions(yamlClient); }

    private DeployerResponse deployables() {
        DeployerResponse deployables = new DeployerResponse();
        deployables.setDeployables(new LinkedHashMap<>());
        Deployable deployable = Deployable.builder()
            .name(APP_NAME)
            .groupId(GROUP_ID)
            .artifactId(ARTIFACT_ID)
            .type("war")
            .version("1.0.2")
            .build();
        deployables.getDeployables().put(APP_NAME, deployable);
        return deployables;
    }

    @Test
    void shouldFetchVersion() {
        given(yamlClient.GET(DEPLOYER_URI, DeployerResponse.class)).willReturn(deployables());

        String version = gateway.fetchVersion(PROD01, APP_NAME);

        assertThat(version).isEqualTo("1.0.2");
    }

    @Test
    void shouldFetchVersions() {
        given(yamlClient.GET(VERSIONS_URI, List.class)).willReturn(VERSIONS);

        List<String> versions = gateway.fetchVersions(PROD01, GROUP_ID, ARTIFACT_ID);

        assertThat(versions).containsExactlyElementsOf(VERSIONS);
    }

    @Test
    void shouldFetchDeployables() {
        given(yamlClient.GET(DEPLOYER_URI, DeployerResponse.class)).willReturn(deployables());

        Stream<Deployment> deployments = gateway.fetchDeployables(PROD01);

        assertThat(deployments).containsExactly(DEPLOYMENT);
    }

    @Test
    void shouldDeploy() {
        given(yamlClient.POST(eq(DEPLOYER_URI), any(), any())).will(i -> {
            Form form = i.getArgument(1);
            assertThat(form.asMap()).containsExactly(entry(APP_NAME + ".version", singletonList("1.0.3")));
            return DEPLOY_AUDITS;
        });

        Audits audits = gateway.deploy(PROD01, APP_NAME, "1.0.3");

        assertThat(audits).isEqualTo(DEPLOY_AUDITS);
    }

    @Test
    void shouldUndeploy() {
        given(yamlClient.POST(eq(DEPLOYER_URI), any(), any())).will(i -> {
            Form form = i.getArgument(1);
            assertThat(form.asMap()).containsExactly(entry(APP_NAME + ".state", singletonList("undeployed")));
            return UNDEPLOY_AUDITS;
        });

        Audits audits = gateway.undeploy(PROD01, APP_NAME);

        assertThat(audits).isEqualTo(UNDEPLOY_AUDITS);
    }
}
