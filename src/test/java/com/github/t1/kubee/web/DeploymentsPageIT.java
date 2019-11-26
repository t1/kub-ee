package com.github.t1.kubee.web;

import com.github.t1.kubee.AbstractIT;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.testtools.OrderedJUnitRunner;
import com.github.t1.testtools.WebDriverRule;
import lombok.extern.java.Log;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

import static com.github.t1.kubee.web.VersionCell.closed;
import static com.github.t1.kubee.web.VersionCell.open;
import static com.github.t1.kubee.web.VersionMenuItem.versionMenuItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;

@Ignore
@Log
@RunWith(OrderedJUnitRunner.class)
public class DeploymentsPageIT extends AbstractIT {
    @ClassRule public static final WebDriverRule DRIVER = new WebDriverRule(new FirefoxDriver());

    private static DeploymentsPage page;

    @Before public void setupPage() {
        if (page == null)
            page = new DeploymentsPage(DRIVER, MASTER.baseUri().resolve("/api/deployments"));
    }


    private VersionCell dummyCell() { return page.findDeploymentCell(node11(), "dummy"); }

    private VersionCell deployerCell() { return page.findDeploymentCell(node11(), "deployer"); }

    private ClusterNode node11() { return CLUSTER_1.node("PROD", 1); }


    @Test
    public void shouldGoToDeploymentsPage() {
        page.navigateTo();

        page.assertOpen();
        assertThat(deployerCell()).is(closed);
        assertThat(dummyCell()).is(closed);
    }

    @Test
    public void shouldOpenDummyMenu() {
        dummyCell().clickToggle();

        assertThat(deployerCell()).is(closed);
        assertThat(dummyCell()).is(open);

        assertThat(dummyCell().menu())
            .hasSize(5)
            .has(versionMenuItem("minus", "1.2.1", "undeployed"), atIndex(0))
            .has(versionMenuItem("minus", "1.2.2", "undeployed"), atIndex(1))
            .has(versionMenuItem("ok-circle", "1.2.3", "deployed"), atIndex(2))
            .has(versionMenuItem(null, null, null), atIndex(3))
            .has(versionMenuItem("ban-circle", null, null), atIndex(4));
    }

    @Test
    public void shouldOpenDeployerMenu() {
        deployerCell().clickToggle();

        assertThat(deployerCell()).is(open);
        assertThat(dummyCell()).is(closed);

        assertThat(deployerCell().menu())
            .hasSize(5)
            .has(versionMenuItem("minus", "2.9.1", "undeployed"), atIndex(0))
            .has(versionMenuItem("ok-circle", "2.9.2", "deployed"), atIndex(1))
            .has(versionMenuItem("minus", "2.9.3", "undeployed"), atIndex(2))
            .has(versionMenuItem(null, null, null), atIndex(3))
            .has(versionMenuItem("ban-circle", null, null), atIndex(4));
        assertThat(dummyCell().menu()).describedAs("still").hasSize(5);
    }

    @Test
    public void shouldCloseMenus() {
        page.findCluster(CLUSTER_1).click();
        assertThat(deployerCell()).is(closed);
        assertThat(dummyCell()).is(closed);
    }

    @Test
    public void shouldUndeployDummyAppFrom2() {
        VersionCell dummy2 = page.findDeploymentCell(CLUSTER_2.node("PROD", 1), "dummy");
        dummy2.clickToggle();

        dummy2.undeployMenuItem().click();

        // dummy2.waitToBeUndeployed();
    }

    @Test
    public void shouldDragDeployment() {
        WebElement to = page.findDeployment(CLUSTER_2.node("PROD", 1), "dummy");
        DRIVER.buildAction()
            .clickAndHold(page.findDeployment(node11(), "dummy"))
            .moveToElement(to)
            .release(to)
            .build().perform();
    }
}
