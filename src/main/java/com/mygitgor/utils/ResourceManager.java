package com.mygitgor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public class ResourceManager implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);

    private final List<Closeable> resources = new ArrayList<>();
    private volatile boolean closed = false;

    public ResourceManager() {
        logger.info("ResourceManager инициализирован");
    }

    /**
     * Регистрация ресурса
     */
    public void register(Closeable resource) {
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

        List<Closeable> resourcesCopy;
        synchronized (resources) {
            resourcesCopy = new ArrayList<>(resources);
            resources.clear();
        }

        // Закрываем в обратном порядке (LIFO)
        for (int i = resourcesCopy.size() - 1; i >= 0; i--) {
            Closeable resource = resourcesCopy.get(i);
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

        logger.info("ResourceManager закрыт. Закрыто ресурсов: {}", resourcesCopy.size());
    }

    public boolean isClosed() {
        return closed;
    }
}
