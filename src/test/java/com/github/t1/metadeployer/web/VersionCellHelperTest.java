package com.github.t1.metadeployer.web;

import com.github.t1.metadeployer.boundary.Boundary.VersionStatus;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.openqa.selenium.*;

import java.util.List;

import static com.github.t1.metadeployer.boundary.Boundary.VersionStatus.*;
import static com.github.t1.metadeployer.web.VersionCell.*;
import static com.github.t1.metadeployer.web.VersionItem.*;
import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VersionCellHelperTest {
    private static final String BY_CLASS_PREFIX = "By.className: ";
    private static final String BY_TAG_PREFIX = "By.tagName: ";
    private static final String BY_CSS_SELECTOR = "By.cssSelector: ";

    @Test
    public void dummy() throws Exception {
        WebElement deployerElement = mockDeploymentElement("deployer");
        WebElement dummyElement = mockDeploymentElement("dummy");

        VersionCell deployer = new VersionCell(deployerElement);
        VersionCell dummy = new VersionCell(dummyElement);
        assertThat(deployer).is(closed);
        assertThat(dummy).is(closed);

        dummy.clickToggle();

        // assertThat(dummy).is(open);
        assertThat(deployer).is(closed);

        assertThat(dummy.menu())
                .hasSize(3)
                .has(versionItem("minus", "1.3.3", "undeployed"), atIndex(0))
                .has(versionItem("ok-circle", "1.3.4", "deployed"), atIndex(1))
                .has(versionItem("refresh", "1.3.5", "undeploying"), atIndex(2));


        deployer.clickToggle();

        // assertThat(deployer).is(open);
        assertThat(dummy).is(closed);

        assertThat(deployer.menu())
                .hasSize(3)
                .has(versionItem("minus", "1.3.3", "undeployed"), atIndex(0))
                .has(versionItem("ok-circle", "1.3.4", "deployed"), atIndex(1))
                .has(versionItem("refresh", "1.3.5", "undeploying"), atIndex(2));
    }

    private WebElement mockDeploymentElement(String id) {
        WebElement deployment = mockWebElement(id, null);

        WebElement dropdown = mockWebElement(null, "dropdown");
        when(deployment.findElement(argThat(byClassName("dropdown")))).thenReturn(dropdown);

        WebElement dropdownToggle = mockWebElement(null, "dropdown-toggle");
        when(dropdown.findElement(argThat(byClassName("dropdown-toggle")))).thenReturn(dropdownToggle);

        WebElement dropdownMenu = mockWebElement(null, "dropdown-menu versions-menu");
        when(dropdown.findElement(argThat(byClassName("versions-menu")))).thenReturn(dropdownMenu);

        WebElement ul = mockWebElement(null, "list-unstyled");
        when(dropdownMenu.findElement(argThat(byTagName("ul")))).thenReturn(ul);

        List<WebElement> items = asList(
                mockVersionItem("minus", undeployed, "1.3.3"),
                mockVersionItem("ok-circle", deployed, "1.3.4"),
                mockVersionItem("refresh", undeploying, "1.3.5"));
        when(ul.findElements(argThat(byTagName("li")))).thenReturn(items);

        return deployment;
    }

    private WebElement mockVersionItem(String iconName, VersionStatus status, String versionName) {
        WebElement li = mockWebElement(null, null);
        WebElement icon = mockWebElement(null, "glyphicon glyphicon-" + iconName +
                " version-icon version-icon-" + status);
        when(li.findElement(argThat(byCssSelector("span.version-icon")))).thenReturn(icon);
        WebElement label = mockWebElement(null, null);
        when(li.findElement(argThat(byCssSelector("span.version")))).thenReturn(label);
        when(label.getText()).thenReturn(versionName);
        return li;
    }

    private ArgumentMatcher<By> byClassName(String className) {
        return argument -> argument != null && argument.toString().equals(BY_CLASS_PREFIX + className);
    }

    private ArgumentMatcher<By> byTagName(String tagName) {
        return argument -> argument != null && argument.toString().equals(BY_TAG_PREFIX + tagName);
    }

    private ArgumentMatcher<By> byCssSelector(String selector) {
        return argument -> argument != null && argument.toString().equals(BY_CSS_SELECTOR + selector);
    }

    private WebElement mockWebElement(String id, String classNames) {
        WebElement mock = mock(WebElement.class);
        when(mock.getAttribute("id")).thenReturn(id);
        when(mock.getAttribute("class")).thenReturn(classNames);
        return mock;
    }
}
