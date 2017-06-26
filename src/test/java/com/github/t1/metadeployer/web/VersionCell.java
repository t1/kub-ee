package com.github.t1.metadeployer.web;

import org.assertj.core.api.Condition;
import org.openqa.selenium.*;

import java.util.List;

import static com.github.t1.testtools.AbstractPage.*;
import static com.github.t1.testtools.AssertJHelpers.*;
import static java.util.Objects.*;
import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.*;

class VersionCell {
    private final WebElement deployment;
    private final WebElement dropdown;
    private final WebElement toggle;
    private final WebElement menu;

    VersionCell(WebElement deployment) {
        this.deployment = deployment;
        this.dropdown = requireNonNull(this.deployment.findElement(By.className("dropdown")));
        this.toggle = requireNonNull(this.dropdown.findElement(By.className("dropdown-toggle")));
        this.menu = requireNonNull(this.dropdown.findElement(By.className("versions-menu")));
    }

    @Override public String toString() {
        return "VersionCell:" + deployment.getAttribute("id") + getCssClasses(dropdown);
    }

    void clickToggle() {
        this.toggle.click();
    }

    List<VersionItem> menu() {
        WebElement ul = this.menu.findElement(By.tagName("ul"));
        assertThat(ul).has(cssClass("list-unstyled"));

        return ul.findElements(By.tagName("li")).stream()
                 .map(VersionItem::new)
                 .collect(toList());
    }

    static final Condition<VersionCell> open = xAllOf(
            map(cssClass("open"), cell -> cell.dropdown, "dropdown"),
            map(displayed, cell -> cell.menu, "menu"));

    static final Condition<VersionCell> closed = not(open);
}
