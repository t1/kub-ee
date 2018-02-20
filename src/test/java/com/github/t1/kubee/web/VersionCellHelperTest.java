package com.github.t1.kubee.web;

import com.github.t1.kubee.model.VersionStatus;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.openqa.selenium.*;

import java.util.List;

import static com.github.t1.kubee.model.VersionStatus.*;
import static com.github.t1.kubee.web.VersionCell.*;
import static com.github.t1.kubee.web.VersionMenuItem.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VersionCellHelperTest {
    private static final String BY_CLASS_PREFIX = "By.className: ";
    private static final String BY_TAG_PREFIX = "By.tagName: ";
    private static final String BY_CSS_SELECTOR = "By.cssSelector: ";

    @Test
    public void dummy() {
        WebElement deployerElement = mockDeploymentElement("deployer");
        WebElement dummyElement = mockDeploymentElement("dummy");

        VersionCell deployer = new VersionCell(null, deployerElement);
        VersionCell dummy = new VersionCell(null, dummyElement);
        assertThat(deployer).is(closed);
        assertThat(dummy).is(closed);

        dummy.clickToggle();

        // assertThat(dummy).is(open);
        assertThat(deployer).is(closed);

        assertThat(dummy.menu())
                .hasSize(3)
                .has(versionMenuItem("minus", "1.3.3", "undeployed"), atIndex(0))
                .has(versionMenuItem("ok-circle", "1.3.4", "deployed"), atIndex(1))
                .has(versionMenuItem("refresh", "1.3.5", "undeploying"), atIndex(2));


        deployer.clickToggle();

        // assertThat(deployer).is(open);
        assertThat(dummy).is(closed);

        assertThat(deployer.menu())
                .hasSize(3)
                .has(versionMenuItem("minus", "1.3.3", "undeployed"), atIndex(0))
                .has(versionMenuItem("ok-circle", "1.3.4", "deployed"), atIndex(1))
                .has(versionMenuItem("refresh", "1.3.5", "undeploying"), atIndex(2));
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
        when(li.findElements(argThat(byCssSelector("span.version-icon")))).thenReturn(singletonList(icon));
        WebElement label = mockWebElement(null, null);
        when(li.findElements(argThat(byCssSelector("span.version")))).thenReturn(singletonList(label));
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
