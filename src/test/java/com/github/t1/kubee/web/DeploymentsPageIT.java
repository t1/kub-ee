package com.github.t1.kubee.web;

import com.github.t1.kubee.AbstractIT;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.testtools.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

import static com.github.t1.kubee.web.VersionCell.*;
import static com.github.t1.kubee.web.VersionMenuItem.*;
import static org.assertj.core.api.Assertions.*;

@RunWith(OrderedJUnitRunner.class)
public class DeploymentsPageIT extends AbstractIT {
    @ClassRule public static final WebDriverRule DRIVER = new WebDriverRule(new FirefoxDriver());

    private static DeploymentsPage page;

    @Before public void setupPage() {
        if (page == null)
            page = new DeploymentsPage(DRIVER, MASTER.baseUri().resolve("/api/deployments"));
    }


    @Test
    public void shouldGoToDeploymentsPage() throws Exception {
        page.navigateTo();

        page.assertOpen();
        assertThat(deployer()).is(closed);
        assertThat(dummy()).is(closed);
    }

    @Test
    public void shouldOpenDummyMenu() {
        dummy().clickToggle();

        assertThat(deployer()).is(closed);
        assertThat(dummy()).is(open);

        assertThat(dummy().menu())
                .hasSize(5)
                .has(versionMenuItem("minus", "1.2.1", "undeployed"), atIndex(0))
                .has(versionMenuItem("minus", "1.2.2", "undeployed"), atIndex(1))
                .has(versionMenuItem("ok-circle", "1.2.3", "deployed"), atIndex(2))
                .has(versionMenuItem(null, null, null), atIndex(3))
                .has(versionMenuItem("ban-circle", null, null), atIndex(4));
    }

    @Test
    public void shouldOpenDeployerMenu() {
        deployer().clickToggle();

        assertThat(deployer()).is(open);
        assertThat(dummy()).is(closed);

        assertThat(deployer().menu())
                .hasSize(5)
                .has(versionMenuItem("minus", "2.9.1", "undeployed"), atIndex(0))
                .has(versionMenuItem("ok-circle", "2.9.2", "deployed"), atIndex(1))
                .has(versionMenuItem("minus", "2.9.3", "undeployed"), atIndex(2))
                .has(versionMenuItem(null, null, null), atIndex(3))
                .has(versionMenuItem("ban-circle", null, null), atIndex(4));
        assertThat(dummy().menu()).describedAs("still").hasSize(5);
    }

    @Test
    public void shouldCloseMenus() {
        page.findCluster(CLUSTER_1).click();
        assertThat(deployer()).is(closed);
        assertThat(dummy()).is(closed);
    }

    @Test
    public void shouldDragDeployment() throws Exception {
        WebElement to = page.findDeployment(CLUSTER_2.node(PROD, 1), "dummy");
        DRIVER.buildAction()
              .clickAndHold(page.findDeployment(node11(), "dummy"))
              .moveToElement(to)
              .release(to)
              .build().perform();
    }

    private VersionCell dummy() { return page.findDeploymentCell(node11(), "dummy"); }

    private VersionCell deployer() { return page.findDeploymentCell(node11(), "deployer"); }

    private ClusterNode node11() { return CLUSTER_1.node(PROD, 1); }
}
