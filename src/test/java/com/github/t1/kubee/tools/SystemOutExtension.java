package com.github.t1.kubee.tools;

import com.github.t1.kubee.boundary.cli.config.ClusterConfigService;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Consumer;

public class SystemOutExtension implements BeforeEachCallback, AfterEachCallback {
    public Integer status;
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private Consumer<Integer> oldExit;
    private PrintStream oldSystemErr;
    private PrintStream oldSystemOut;

    public String out() { return out.toString(); }

    public String err() { return err.toString(); }

    @Override public void beforeEach(ExtensionContext context) {
        oldSystemErr = System.err;
        System.setErr(new PrintStream(err));
        oldSystemOut = System.out;
        System.setOut(new PrintStream(out));
        oldExit = ClusterConfigService.exit;
        ClusterConfigService.exit = status -> this.status = status;
    }

    @Override public void afterEach(ExtensionContext context) {
        System.setErr(oldSystemErr);
        System.setOut(oldSystemOut);
        ClusterConfigService.exit = oldExit;
    }
}
