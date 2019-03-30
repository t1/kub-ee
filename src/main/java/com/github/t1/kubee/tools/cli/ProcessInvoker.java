package com.github.t1.kubee.tools.cli;

import lombok.SneakyThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

import static java.lang.String.join;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ProcessInvoker {
    public String invoke(String... args) { return invoke(null, args); }

    @SneakyThrows({IOException.class, InterruptedException.class})
    public String invoke(Path workingDirectory, String... args) {
        ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
        if (workingDirectory != null)
            builder.directory(workingDirectory.toFile());
        Process process = builder.start();
        boolean inTime = process.waitFor(10, SECONDS);
        if (!inTime)
            throw new RuntimeException("could not invoke `" + join(" ", args) + "` in time");
        String output = new Scanner(process.getInputStream()).useDelimiter("\\Z").next();
        if (process.exitValue() != 0)
            throw new RuntimeException(join(" ", args) + " returned error " + process.exitValue() + ":\n" + output);
        return output;
    }
}
