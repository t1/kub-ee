package com.github.t1.kubee.web;

import org.assertj.core.api.Condition;
import org.openqa.selenium.*;

import java.util.List;

import static com.github.t1.testtools.AbstractPage.*;
import static com.github.t1.testtools.AssertJHelpers.*;

class VersionMenuItem {
    private final WebElement icon;
    private final WebElement label;

    VersionMenuItem(WebElement element) {
        this.icon = optional(element, "span.version-icon");
        this.label = optional(element, "span.version");
    }

    private WebElement optional(WebElement parent, String selector) {
        List<WebElement> elements = parent.findElements(By.cssSelector(selector));
        if (elements.size() == 0)
            return null;
        if (elements.size() > 1)
            throw new IllegalStateException("found " + elements.size() + " elements by " + selector);
        return elements.get(0);
    }

    @Override public String toString() {
        return "VersionMenuItem:" + ((label == null) ? null : label.getText())
                + "[icon:" + ((icon == null) ? null : icon.getAttribute("class")) + "]";
    }

    static Condition<VersionMenuItem> versionMenuItem(String iconName, String versionName, String status) {
        return xAllOf(
                map((iconName == null)
                                ? isNull()
                                : (status == null)
                                        ? cssClass("glyphicon", "glyphicon-" + iconName)
                                        : cssClass("glyphicon", "glyphicon-" + iconName, "version-icon-" + status),
                        item -> item.icon, "icon"),
                map((versionName == null)
                                ? isNull()
                                : text(versionName),
                        item -> item.label, "label")
        );
    }
}
