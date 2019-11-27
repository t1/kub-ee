package com.github.t1.kubee.tools;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.lang.System.currentTimeMillis;
import static java.util.stream.Collectors.joining;

public class SmartFormatter extends Formatter {
    private static long last = currentTimeMillis();

    public static void configure(boolean debug) {
        if (debug)
            Logger.getLogger("").setLevel(Level.ALL);
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            if (debug)
                handler.setLevel(Level.ALL);
            handler.setFormatter(new SmartFormatter());
        }
    }

    @Override public String getHead(Handler h) {
        return BRIGHT_BLACK + "   Δt level   logger                                                      | message\n" + RESET;
    }

    @Override public synchronized String format(LogRecord record) {
        long delta = record.getMillis() - last;
        last = record.getMillis();
        return String.format(BRIGHT_BLACK + "% 5d %-7s %-60.60s| %s\n%s" + RESET,
            delta, record.getLevel(), record.getLoggerName(), formatMessage(record), stacktrace(record));
    }

    @Override public synchronized String formatMessage(LogRecord record) {
        String color = color(record);
        return color + String.join(BRIGHT_BLACK + "\n                                                                          | " + color,
            super.formatMessage(record).split("\n"));
    }

    private String color(LogRecord record) {
        switch (record.getLevel().getName()) {
            case "SEVERE":
                return RED;
            case "WARNING":
                return YELLOW;
            case "INFO":
                return BLACK;
            case "CONFIG":
                return BLUE;
            case "FINE":
                return WHITE;
            default:
                return BRIGHT_BLACK;
        }
    }

    private String stacktrace(LogRecord record) {
        Throwable throwable = record.getThrown();
        if (throwable == null)
            return "";
        return Stream.of(printStackTrace(throwable).split("\n"))
            .map(this::colorStackTraceLine)
            .collect(joining("\n", "", "\n\n"));
    }

    private String colorStackTraceLine(String line) {
        return (line.startsWith("\t") ? BRIGHT_BLACK : BLACK) + line + RESET;
    }

    private String printStackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static final String RESET = "\u001B[0m";
    private static final String BLACK = "\u001B[30m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String WHITE = "\u001B[37m";
    private static final String BRIGHT_BLACK = "\u001B[90m";
}
