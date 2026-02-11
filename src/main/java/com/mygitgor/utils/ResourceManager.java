package com.mygitgor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ResourceManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);

    private final List<AutoCloseable> resources = new ArrayList<>();
    private final List<ExecutorService> executors = new ArrayList<>();
    private volatile boolean closed = false;

    public ResourceManager() {
        logger.info("ResourceManager инициализирован");
    }

    /**
     * Регистрация ресурса
     */
    public void register(AutoCloseable resource) {
        if (resource == null) return;

        if (!closed) {
            synchronized (resources) {
                resources.add(resource);
                logger.debug("Ресурс зарегистрирован: {}", resource.getClass().getSimpleName());
            }
        } else {
            logger.warn("Ресурс зарегистрирован после закрытия менеджера: {}",
                    resource.getClass().getSimpleName());
            try {
                resource.close();
            } catch (Exception e) {
                logger.warn("Ошибка при закрытии ресурса", e);
            }
        }
    }

    public void registerIfCloseable(Object resource) {
        if (resource != null && resource instanceof AutoCloseable && !closed) {
            resources.add((AutoCloseable) resource);
            logger.debug("Ресурс зарегистрирован (if closeable): {}", resource.getClass().getSimpleName());
        }
    }

    /**
     * Регистрирует ресурс с указанием имени
     */
    public <T extends AutoCloseable> T register(T resource, String name) {
        if (resource != null && !closed) {
            resources.add(resource);
            logger.debug("Ресурс '{}' зарегистрирован", name);
        }
        return resource;
    }

    public void registerExecutor(ExecutorService executor) {
        if (executor == null) return;

        if (!closed) {
            synchronized (executors) {
                executors.add(executor);
                logger.debug("ExecutorService зарегистрирован");
            }
        } else {
            logger.warn("ExecutorService зарегистрирован после закрытия менеджера");
            shutdownExecutor(executor);
        }
    }


    public void register(Object resource) {
        if (resource == null) return;

        if (resource instanceof AutoCloseable) {
            register((AutoCloseable) resource);
        } else if (resource instanceof ExecutorService) {
            registerExecutor((ExecutorService) resource);
        } else if (resource instanceof Closeable) {
            register((Closeable) resource);
        } else {
            logger.warn("Неизвестный тип ресурса: {}", resource.getClass().getName());
        }
    }

    public void unregister(AutoCloseable resource) {
        if (resource == null) return;

        synchronized (resources) {
            if (resources.remove(resource)) {
                logger.debug("Ресурс отменен: {}", resource.getClass().getSimpleName());
            }
        }
    }

    /**
     * Отмена регистрации ExecutorService
     */
    public void unregisterExecutor(ExecutorService executor) {
        if (executor == null) return;

        synchronized (executors) {
            if (executors.remove(executor)) {
                logger.debug("ExecutorService отменен");
            }
        }
    }

    /**
     * Универсальный метод отмены регистрации
     */
    public void unregister(Object resource) {
        if (resource == null) return;

        if (resource instanceof AutoCloseable) {
            unregister((AutoCloseable) resource);
        } else if (resource instanceof ExecutorService) {
            unregisterExecutor((ExecutorService) resource);
        } else {
            logger.warn("Неизвестный тип ресурса для отмены: {}",
                    resource.getClass().getName());
        }
    }

    /**
     * Закрытие всех ресурсов
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Закрытие ResourceManager...");
        shutdownAllExecutors();
        closeAllResources();

        logger.info("ResourceManager закрыт");
    }

    private void shutdownAllExecutors() {
        List<ExecutorService> executorsCopy;
        synchronized (executors) {
            executorsCopy = new ArrayList<>(executors);
            executors.clear();
        }

        for (int i = executorsCopy.size() - 1; i >= 0; i--) {
            ExecutorService executor = executorsCopy.get(i);
            if (executor != null) {
                shutdownExecutor(executor);
            }
        }
    }

    private void shutdownExecutor(ExecutorService executor) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("ExecutorService не завершился");
                }
            }
            logger.debug("ExecutorService закрыт");
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Закрытие ExecutorService прервано", e);
        } catch (Exception e) {
            logger.error("Ошибка при закрытии ExecutorService", e);
        }
    }

    private void closeAllResources() {
        List<AutoCloseable> resourcesCopy;
        synchronized (resources) {
            resourcesCopy = new ArrayList<>(resources);
            resources.clear();
        }

        // Закрываем в обратном порядке (LIFO)
        for (int i = resourcesCopy.size() - 1; i >= 0; i--) {
            AutoCloseable resource = resourcesCopy.get(i);
            if (resource != null) {
                try {
                    resource.close();
                    logger.debug("Ресурс закрыт: {}", resource.getClass().getSimpleName());
                } catch (Exception e) {
                    logger.error("Ошибка при закрытии ресурса {}",
                            resource.getClass().getSimpleName(), e);
                }
            }
        }
    }

    public void register(Closeable resource) {
        register((AutoCloseable) resource);
    }

    public boolean isClosed() {
        return closed;
    }
}
