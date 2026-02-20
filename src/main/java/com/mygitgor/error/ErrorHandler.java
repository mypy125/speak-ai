package com.mygitgor.error;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ErrorHandler {
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    private ErrorHandler() {}

    public static <T> T handle(Supplier<T> operation,
                               Function<Exception, T> fallback,
                               String errorMessage) {
        try {
            return operation.get();
        } catch (Exception e) {
            logger.error(errorMessage, e);
            return fallback.apply(e);
        }
    }

    public static void handle(Runnable operation,
                              Consumer<Exception> errorHandler,
                              String errorMessage) {
        try {
            operation.run();
        } catch (Exception e) {
            logger.error(errorMessage, e);
            errorHandler.accept(e);
        }
    }

    public static <T> CompletableFuture<T> handleAsync(Supplier<CompletableFuture<T>> operation,
                                                       Function<Throwable, T> fallback) {
        try {
            return operation.get()
                    .exceptionally(fallback);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(fallback.apply(e));
        }
    }

    public static void handleAsyncFuture(CompletableFuture<?> future,
                                         Consumer<Throwable> errorHandler) {
        future.exceptionally(throwable -> {
            errorHandler.accept(throwable);
            return null;
        });
    }

    public static void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static void showErrorAndLog(String title, String message, Throwable e) {
        logger.error("{}: {}", title, message, e);
        showError(title, message + "\n\n" + e.getMessage());
    }

    public static void showWarning(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static <T> T retry(Supplier<T> operation,
                              int maxRetries,
                              long delayMs,
                              String operationName) {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                return operation.get();
            } catch (Exception e) {
                attempts++;
                logger.warn("Попытка {} из {} для {} не удалась: {}",
                        attempts, maxRetries, operationName, e.getMessage());

                if (attempts == maxRetries) {
                    throw new ServiceInitializationException(
                            String.format("Не удалось выполнить %s после %d попыток",
                                    operationName, maxRetries), e);
                }

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ServiceInitializationException(
                            String.format("Прервано во время %s", operationName), ie);
                }
            }
        }
        throw new ServiceInitializationException(
                String.format("Не удалось выполнить %s", operationName));
    }

    public static void safeClose(AutoCloseable closeable, String resourceName) {
        if (closeable != null) {
            try {
                closeable.close();
                logger.debug("Ресурс {} успешно закрыт", resourceName);
            } catch (Exception e) {
                logger.error("Ошибка при закрытии ресурса: {}", resourceName, e);
            }
        }
    }

    public static <T> T orElse(Supplier<T> operation, T defaultValue, String errorMessage) {
        try {
            return operation.get();
        } catch (Exception e) {
            logger.warn("{}: {}", errorMessage, e.getMessage());
            return defaultValue;
        }
    }

    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new ServiceInitializationException(message);
        }
        return obj;
    }

    public static void requireState(boolean condition, String message) {
        if (!condition) {
            throw new ServiceInitializationException(message);
        }
    }
}
