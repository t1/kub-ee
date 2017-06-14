package com.github.t1.metadeployer.web;

import com.github.t1.testtools.*;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.pagefactory.ByAll;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class DeploymentsPage extends AbstractPage<DeploymentsPage> {
    DeploymentsPage(WebDriverRule driver, URI uri) { super(driver, uri); }

    @Override public DeploymentsPageAsserts assertOpen() { return new DeploymentsPageAsserts(); }

    @FindBy(id = "deployables")
    WebElement deployablesTable;

    public WebElement findDeployment(By by) {
        by = new ByAll(By.className("deployable"), by);
        List<WebElement> elements = deployablesTable.findElements(by);
        assertThat(elements).describedAs(description("find " + by)).hasSize(1);
        return elements.get(0);
    }

    public class DeploymentsPageAsserts extends AbstractPageAsserts<DeploymentsPageAsserts> {
        @Override protected DeploymentsPageAsserts hasTitle() { return super.hasTitle("Meta-Deployer"); }
    }
}
