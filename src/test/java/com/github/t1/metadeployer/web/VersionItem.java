package com.github.t1.metadeployer.web;

import org.assertj.core.api.Condition;
import org.openqa.selenium.*;

import static com.github.t1.testtools.AbstractPage.*;
import static com.github.t1.testtools.AssertJHelpers.*;
import static java.util.Objects.*;

class VersionItem {
    private final WebElement element;
    private final WebElement icon;
    private final WebElement label;

    VersionItem(WebElement element) {
        this.element = element;
        this.icon = requireNonNull(this.element.findElement(By.cssSelector("span.version-icon")));
        this.label = requireNonNull(this.element.findElement(By.cssSelector("span.version")));
    }

    @Override public String toString() {
        return "VersionItem:" + label.getText()
                + "[icon:" + icon.getAttribute("class") + "]";
    }

    static Condition<VersionItem> versionItem(String iconName, String versionName, String status) {
        return xAllOf(
                map(cssClass("glyphicon", "glyphicon-" + iconName, "version-icon-" + status),
                        item -> item.icon, "icon"),
                map(text(versionName), item -> item.label, "label")
        );
    }
}
