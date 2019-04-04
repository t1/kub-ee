package com.github.t1.kubee.web;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.testtools.AbstractPage;
import com.github.t1.testtools.WebDriverRule;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class DeploymentsPage extends AbstractPage<DeploymentsPage> {
    DeploymentsPage(WebDriverRule driver, URI uri) { super(driver, uri); }

    @Override public DeploymentsPageAsserts assertOpen() { return new DeploymentsPageAsserts(); }

    @FindBy(id = "deployables")
    WebElement deployablesTable;

    public VersionCell findDeploymentCell(ClusterNode node, String name) {
        return new VersionCell(this, findDeployment(node, name));
    }

    public WebElement findDeployment(ClusterNode node, String name) {
        return findDeployment(By.id(node.id() + ":" + name));
    }

    public WebElement findDeployment(By by) {
        List<WebElement> elements = deployablesTable.findElements(by);
        assertThat(elements).describedAs(description("find " + by)).hasSize(1);
        return elements.get(0);
    }

    public WebElement findCluster(Cluster cluster) {
        return deployablesTable.findElement(By.id("cluster:" + cluster.id()));
    }


    public class DeploymentsPageAsserts extends AbstractPageAsserts<DeploymentsPageAsserts> {
        @Override protected DeploymentsPageAsserts hasTitle() { return super.hasTitle("Kub-EE"); }
    }
}
