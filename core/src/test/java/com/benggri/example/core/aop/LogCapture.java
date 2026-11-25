package com.benggri.example.core.aop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Captures the events of one logger and restores its level on close. */
final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    LogCapture(String loggerName, Level level) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerName);
        this.previousLevel = logger.getLevel();
        logger.setLevel(level);
        appender.start();
        logger.addAppender(appender);
    }

    LogCapture(Class<?> type, Level level) {
        this(type.getName(), level);
    }

    List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    List<String> messages() {
        return events().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }
}
