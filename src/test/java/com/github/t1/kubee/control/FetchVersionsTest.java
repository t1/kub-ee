package com.github.t1.kubee.control;

import com.github.t1.kubee.model.Version;
import org.junit.Test;

import javax.ws.rs.*;
import java.net.UnknownHostException;
import java.util.List;

import static com.github.t1.kubee.model.VersionStatus.*;
import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FetchVersionsTest extends AbstractControllerTest {
    @Test
    public void shouldFetchNoVersion() throws Exception {
        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).isEmpty();
    }

    @Test
    public void shouldFetchOneVersion() throws Exception {
        List<String> versionStrings = asList("1.0.0", "1.0.1", "1.0.2");
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "foo-group", "foo-artifact"))
                .thenReturn(versionStrings);

        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).containsExactly(
                new Version("1.0.0", undeployed),
                new Version("1.0.1", deployed),
                new Version("1.0.2", undeployed)
        );
    }

    @Test
    public void shouldFetchNotFoundVersionDummy() throws Exception {
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "foo-group", "foo-artifact"))
                .thenThrow(new NotFoundException("foo not found"));

        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).containsExactly(new Version("1.0.1", deployed));
    }

    @Test
    public void shouldFetchUnknownHostVersionDummy() throws Exception {
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "foo-group", "foo-artifact"))
                .thenThrow(new ProcessingException(new UnknownHostException("dummy")));

        List<Version> versions = controller.fetchVersions(DEV01, DEPLOYMENT);

        assertThat(versions).containsExactly(new Version("1.0.1", deployed));
    }

    @Test
    public void shouldFailToFetchVersion() throws Exception {
        RuntimeException dummy = new RuntimeException("dummy");
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "foo-group", "foo-artifact")).thenThrow(dummy);

        Throwable throwable = catchThrowable(() -> controller.fetchVersions(DEV01, DEPLOYMENT));

        assertThat(throwable).isSameAs(dummy);
    }

    @Test
    public void shouldFailToFetchVersionFromProcessingException() throws Exception {
        ProcessingException dummy = new ProcessingException(new RuntimeException("dummy"));
        when(controller.deployer.fetchVersions(DEV01.deployerUri(), "foo-group", "foo-artifact")).thenThrow(dummy);

        Throwable throwable = catchThrowable(() -> controller.fetchVersions(DEV01, DEPLOYMENT));

        assertThat(throwable).isSameAs(dummy);
    }
}
