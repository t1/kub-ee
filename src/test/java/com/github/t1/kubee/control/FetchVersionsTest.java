package com.github.t1.kubee.control;

import com.github.t1.kubee.entity.Version;
import org.junit.jupiter.api.Test;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import java.net.UnknownHostException;
import java.util.List;

import static com.github.t1.kubee.TestData.VERSIONS;
import static com.github.t1.kubee.TestData.VERSION_100;
import static com.github.t1.kubee.TestData.VERSION_101;
import static com.github.t1.kubee.TestData.VERSION_102;
import static com.github.t1.kubee.TestData.VERSION_103;
import static com.github.t1.kubee.entity.VersionStatus.deployed;
import static com.github.t1.kubee.entity.VersionStatus.undeployed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

class FetchVersionsTest extends AbstractControllerTest {
    @Test void shouldFetchNoVersion() {
        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).isEmpty();
    }

    @Test void shouldFetchOneVersion() {
        when(controller.deployer.fetchVersions(DEV01, "app-group", "app-artifact"))
            .thenReturn(VERSIONS);

        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).containsExactly(
            new Version(VERSION_100, undeployed),
            new Version(VERSION_101, deployed),
            new Version(VERSION_102, undeployed),
            new Version(VERSION_103, undeployed)
        );
    }

    @Test void shouldFetchNotFoundVersionDummy() {
        when(controller.deployer.fetchVersions(DEV01, "app-group", "app-artifact"))
            .thenThrow(new NotFoundException("app not found"));

        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).containsExactly(new Version(VERSION_101, deployed));
    }

    @Test void shouldFetchUnknownHostVersionDummy() {
        when(controller.deployer.fetchVersions(DEV01, "app-group", "app-artifact"))
            .thenThrow(new ProcessingException(new UnknownHostException("dummy")));

        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).containsExactly(new Version(VERSION_101, deployed));
    }

    @Test void shouldFailToFetchVersion() {
        RuntimeException dummy = new RuntimeException("dummy");
        when(controller.deployer.fetchVersions(DEV01, "app-group", "app-artifact")).thenThrow(dummy);

        Throwable throwable = catchThrowable(() -> controller.fetchVersions(DEV01, DEPLOYMENT));

        assertThat(throwable).isSameAs(dummy);
    }

    @Test void shouldFailToFetchVersionFromProcessingException() {
        ProcessingException dummy = new ProcessingException(new RuntimeException("dummy"));
        when(controller.deployer.fetchVersions(DEV01, "app-group", "app-artifact")).thenThrow(dummy);

        Throwable throwable = catchThrowable(() -> controller.fetchVersions(DEV01, DEPLOYMENT));

        assertThat(throwable).isSameAs(dummy);
    }
}
