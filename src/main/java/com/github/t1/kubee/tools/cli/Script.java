package com.github.t1.kubee.tools.cli;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

import static java.util.concurrent.TimeUnit.SECONDS;

@RequiredArgsConstructor
public class Script {
    private final String commandline;
    private Path workingDirectory;

    public Script workingDirectory(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        return this;
    }

    public Result run() {
        Result result = runWithoutStatusCheck();
        if (result.getExitValue() != 0)
            throw new RuntimeException(commandline + " returned error " + result.getExitValue() + ":\n"
                + result.getOutput());
        return result;
    }

    public Result runWithoutStatusCheck() {
        Result result = Invoker.INSTANCE.invoke(workingDirectory, commandline);
        if (result == null)
            throw new RuntimeException("run() of '" + commandline + "' returned null... probably a mocking error");
        return result;
    }

    @Value
    public static class Result {
        int exitValue;
        String output;
    }

    /**
     * This class can be used to mock invocations from {@link Script}.
     */
    public static class Invoker {
        public static Invoker INSTANCE = new Invoker();

        @SneakyThrows({IOException.class, InterruptedException.class})
        public Result invoke(Path workingDirectory, String commandline) {
            ProcessBuilder builder = new ProcessBuilder(commandline.split(" ")).redirectErrorStream(true);
            if (workingDirectory != null)
                builder.directory(workingDirectory.toFile());
            Process process = builder.start();
            boolean inTime = process.waitFor(10, SECONDS);
            if (!inTime)
                throw new RuntimeException("could not invoke `" + commandline + "` in time");
            Scanner scanner = new Scanner(process.getInputStream()).useDelimiter("\\Z");
            String output = (scanner.hasNext()) ? scanner.next() : "";
            return new Result(process.exitValue(), output);
        }
    }
}
