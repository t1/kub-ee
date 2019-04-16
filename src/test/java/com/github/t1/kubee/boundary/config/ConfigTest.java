package com.github.t1.kubee.boundary.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.github.t1.kubee.boundary.config.Config.CONFIG_PROPERTY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ConfigTest {
    private static final String PATH = "dummy/path";

    @Mock org.eclipse.microprofile.config.Config mpConfig;
    @InjectMocks Config config = new Config();

    @BeforeEach void setUp() { assertThat(System.getProperty("jboss.server.config.dir")).isNull(); }

    @AfterEach void tearDown() { System.clearProperty("jboss.server.config.dir"); }

    private void givenMicroprofileConfig(String name, Optional<String> empty) {
        given(mpConfig.getOptionalValue(CONFIG_PROPERTY_PREFIX + name, String.class)).willReturn(empty);
    }

    @Test void shouldGetConfiguredDockerComposeDir() {
        givenMicroprofileConfig("dockerComposeDir", Optional.of(PATH));

        Path path = config.dockerComposeDir();

        assertThat(path).isEqualTo(Paths.get(PATH));
    }

    @Test void shouldGetNullDockerComposeDir() {
        givenMicroprofileConfig("dockerComposeDir", Optional.empty());

        Path path = config.dockerComposeDir();

        assertThat(path).isNull();
    }

    @Test void shouldGetConfiguredClusterConfigPath() {
        givenMicroprofileConfig("clusterConfigPath", Optional.of(PATH));

        Path path = config.clusterConfigPath();

        assertThat(path).isEqualTo(Paths.get(PATH));
    }

    @Test void shouldGetJBossClusterConfigPath() {
        System.setProperty("jboss.server.config.dir", PATH);
        givenMicroprofileConfig("clusterConfigPath", Optional.empty());

        Path path = config.clusterConfigPath();

        assertThat(path).isEqualTo(Paths.get(PATH).resolve("cluster-config.yaml"));
    }

    @Test void shouldGetUserDirClusterConfigPath() {
        givenMicroprofileConfig("clusterConfigPath", Optional.empty());

        Path path = config.clusterConfigPath();

        assertThat(path).isEqualTo(Paths.get(System.getProperty("user.dir")).resolve("cluster-config.yaml"));
    }
}
