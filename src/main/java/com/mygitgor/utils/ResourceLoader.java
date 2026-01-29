package com.mygitgor.utils;

import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ResourceLoader {
    private static final Logger logger = LoggerFactory.getLogger(ResourceLoader.class);
    private static final Map<String, Image> imageCache = new HashMap<>();

    public static Image loadImage(String resourcePath) {
        if (imageCache.containsKey(resourcePath)) {
            return imageCache.get(resourcePath);
        }

        try {
            InputStream inputStream = ResourceLoader.class.getResourceAsStream(resourcePath);
            if (inputStream == null) {
                logger.warn("Ресурс не найден: {}", resourcePath);
                return null;
            }

            Image image = new Image(inputStream);
            imageCache.put(resourcePath, image);
            logger.debug("Загружено изображение: {}", resourcePath);
            return image;

        } catch (Exception e) {
            logger.error("Ошибка при загрузке изображения: {}", resourcePath, e);
            return null;
        }
    }

    public static InputStream loadResourceAsStream(String resourcePath) {
        try {
            InputStream inputStream = ResourceLoader.class.getResourceAsStream(resourcePath);
            if (inputStream == null) {
                logger.warn("Ресурс не найден: {}", resourcePath);
            }
            return inputStream;
        } catch (Exception e) {
            logger.error("Ошибка при загрузке ресурса: {}", resourcePath, e);
            return null;
        }
    }

    public static void clearCache() {
        imageCache.clear();
        logger.debug("Кэш ресурсов очищен");
    }
}
