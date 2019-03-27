package com.github.t1.kubee.web;

import lombok.Getter;
import org.assertj.core.api.Condition;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

import static com.github.t1.testtools.AbstractPage.cssClass;
import static com.github.t1.testtools.AbstractPage.isNull;
import static com.github.t1.testtools.AbstractPage.text;
import static com.github.t1.testtools.AssertJHelpers.map;
import static com.github.t1.testtools.AssertJHelpers.xAllOf;

@Getter
class VersionMenuItem {
    private final WebElement element;
    private final WebElement icon;
    private final WebElement label;
    private final String text;

    VersionMenuItem(WebElement element) {
        this.element = element;
        this.icon = optional(element, "span.version-icon");
        this.label = optional(element, "span.version");
        this.text = element.getText();
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
        return "VersionMenuItem:"
            + ((label == null) ? "" : "[label:" + label.getText() + "]")
            + ((text == null) ? "" : "[text:" + text + "]")
            + ((icon == null) ? "" : "[icon:" + icon.getAttribute("class") + "]");
    }

    static Condition<VersionMenuItem> versionMenuItem(String iconName, String versionName, String status) {
        return xAllOf(
            map((iconName == null)
                    ? isNull()
                    : (status == null)
                    ? cssClass("icon", "ion-md-" + iconName)
                    : cssClass("icon", "ion-md-" + iconName, "version-icon-" + status),
                item -> item.icon, "icon"),
            map((versionName == null)
                    ? isNull()
                    : text(versionName),
                item -> item.label, "label")
        );
    }

    void click() { element.click(); }
}
