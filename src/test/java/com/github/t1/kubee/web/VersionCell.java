package com.github.t1.kubee.web;

import com.github.t1.testtools.AbstractPage;
import lombok.extern.java.Log;
import org.assertj.core.api.Condition;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

import static com.github.t1.testtools.AbstractPage.cssClass;
import static com.github.t1.testtools.AbstractPage.displayed;
import static com.github.t1.testtools.AbstractPage.getCssClasses;
import static com.github.t1.testtools.AssertJHelpers.map;
import static com.github.t1.testtools.AssertJHelpers.xAllOf;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.not;

@Log
class VersionCell {
    private final AbstractPage<?> page;
    private final WebElement deployment;
    private final WebElement dropdown;
    private final WebElement toggle;
    private final WebElement menu;

    VersionCell(AbstractPage<?> page, WebElement deployment) {
        this.page = page;
        this.deployment = deployment;
        this.dropdown = requireNonNull(this.deployment.findElement(By.className("dropdown")));
        this.toggle = requireNonNull(this.dropdown.findElement(By.className("dropdown-toggle")));
        this.menu = requireNonNull(this.dropdown.findElement(By.className("versions-menu")));
    }

    @Override public String toString() {
        return "VersionCell:" + deployment.getAttribute("id") + getCssClasses(dropdown);
    }

    void clickToggle() { this.toggle.click(); }

    List<VersionMenuItem> menu() {
        WebElement ul = this.menu.findElement(By.tagName("ul"));
        assertThat(ul).has(cssClass("list-unstyled"));

        return ul.findElements(By.tagName("li")).stream()
                 .map(VersionMenuItem::new)
                 .collect(toList());
    }

    static final Condition<VersionCell> open = xAllOf(
            map(cssClass("open"), cell -> cell.dropdown, "dropdown"),
            map(displayed, cell -> cell.menu, "menu"));

    static final Condition<VersionCell> closed = not(open);

    VersionMenuItem undeployMenuItem() {
        return menu().stream()
                     .filter(item -> item.getText().equals("undeploy"))
                     .findFirst()
                     .orElseThrow(RuntimeException::new);
    }

    void waitToBeUndeployed() {
        page.webDriverWait()
            .withMessage("waiting for " + this + " to be undeployed")
            .until(driver -> {
                log.info("- " + deployment.getText());
                return deployment.getText().equals(" - ");
            });
    }
}
