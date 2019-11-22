package com.github.t1.kubee.tools.cli;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Scanner;

import static java.util.concurrent.TimeUnit.SECONDS;

@RequiredArgsConstructor @ToString
public class Script {
    private final String commandline;
    private Path workingDirectory;

    public Script workingDirectory(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        return this;
    }

    public String run() {
        Result result = runWithoutCheck();
        check(result);
        return result.getOutput();
    }

    public Result runWithoutCheck() {
        Result result = Invoker.INSTANCE.invoke(workingDirectory, commandline);
        if (result == null)
            throw new RuntimeException("running '" + commandline + "' returned null... probably a mocking error");
        return result;
    }

    public void check(Result result) {
        if (result.getExitValue() != 0)
            throw new RuntimeException("'" + commandline + "' returned error " + result.getExitValue() + ":\n"
                + result.getOutput());
    }

    @Value public static class Result {
        int exitValue;
        String output;
    }

    /**
     * This class can be used to mock invocations from {@link Script}.
     */
    public static class Invoker {
        public static Invoker INSTANCE = new Invoker();

        public Result invoke(Path workingDirectory, String commandline) {
            ProcessBuilder builder = build(workingDirectory, commandline);
            Process process = run(commandline, builder);
            return new Result(process.exitValue(), read(process.getInputStream()));
        }

        private ProcessBuilder build(Path workingDirectory, String commandline) {
            ProcessBuilder builder = new ProcessBuilder(commandline.split(" ")).redirectErrorStream(true);
            if (workingDirectory != null)
                builder.directory(workingDirectory.toFile());
            return builder;
        }

        @SneakyThrows({IOException.class, InterruptedException.class})
        private Process run(String commandline, ProcessBuilder builder) {
            Process process = builder.start();
            boolean inTime = process.waitFor(10, SECONDS);
            if (!inTime)
                throw new RuntimeException("could not invoke `" + commandline + "` in time");
            return process;
        }

        private String read(InputStream inputStream) {
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\Z");
            return (scanner.hasNext()) ? scanner.next() : "";
        }
    }
}
