package com.github.t1.metadeployer.web;

import com.github.t1.testtools.*;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
public class DeploymentsPage extends AbstractPage<DeploymentsPage> {
    DeploymentsPage(WebDriverRule driver, URI uri) { super(driver, uri); }

    @Override public DeploymentsPageAsserts assertOpen() { return new DeploymentsPageAsserts(); }

    public class DeploymentsPageAsserts extends AbstractPageAsserts<DeploymentsPageAsserts> {
        @Override protected DeploymentsPageAsserts hasTitle() { return super.hasTitle("Meta-Deployer"); }
    }
}
