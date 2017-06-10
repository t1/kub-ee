package com.github.t1.metadeployer.web;

import com.github.t1.testtools.*;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
public class ApplicationsPage extends AbstractPage<ApplicationsPage> {
    ApplicationsPage(WebDriverRule driver, URI uri) { super(driver, uri); }

    @Override public ApplicationsPageAsserts assertOpen() { return new ApplicationsPageAsserts(); }

    public class ApplicationsPageAsserts extends AbstractPageAsserts<ApplicationsPageAsserts> {
        @Override protected ApplicationsPageAsserts hasTitle() { return super.hasTitle("Meta-Deployer"); }
    }
}
