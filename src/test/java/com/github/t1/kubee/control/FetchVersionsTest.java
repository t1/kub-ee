package com.github.t1.kubee.control;

import com.github.t1.kubee.entity.Version;
import org.junit.jupiter.api.Test;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import java.net.UnknownHostException;
import java.util.List;

import static com.github.t1.kubee.entity.VersionStatus.deployed;
import static com.github.t1.kubee.entity.VersionStatus.undeployed;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

class FetchVersionsTest extends AbstractControllerTest {
    @Test void shouldFetchNoVersion() {
        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).isEmpty();
    }

    @Test void shouldFetchOneVersion() {
        List<String> versionStrings = asList("1.0.0", "1.0.1", "1.0.2");
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "app-group", "app-artifact"))
                .thenReturn(versionStrings);

        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).containsExactly(
                new Version("1.0.0", undeployed),
                new Version("1.0.1", deployed),
                new Version("1.0.2", undeployed)
        );
    }

    @Test void shouldFetchNotFoundVersionDummy() {
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "app-group", "app-artifact"))
                .thenThrow(new NotFoundException("app not found"));

        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).containsExactly(new Version("1.0.1", deployed));
    }

    @Test void shouldFetchUnknownHostVersionDummy() {
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "app-group", "app-artifact"))
                .thenThrow(new ProcessingException(new UnknownHostException("dummy")));

        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).containsExactly(new Version("1.0.1", deployed));
    }

    @Test void shouldFailToFetchVersion() {
        RuntimeException dummy = new RuntimeException("dummy");
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "app-group", "app-artifact")).thenThrow(dummy);

        Throwable throwable = catchThrowable(() -> controller.fetchVersions(DEV01, DEPLOYMENT));

        assertThat(throwable).isSameAs(dummy);
    }

    @Test void shouldFailToFetchVersionFromProcessingException() {
        ProcessingException dummy = new ProcessingException(new RuntimeException("dummy"));
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "app-group", "app-artifact")).thenThrow(dummy);

        Throwable throwable = catchThrowable(() -> controller.fetchVersions(DEV01, DEPLOYMENT));

        assertThat(throwable).isSameAs(dummy);
    }
}
