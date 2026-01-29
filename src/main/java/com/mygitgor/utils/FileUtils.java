package com.mygitgor.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

    public static String generateUniqueFileName(String prefix, String extension) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
        String timestamp = sdf.format(new Date());
        return String.format("%s_%s.%s", prefix, timestamp, extension);
    }

    public static boolean createDirectory(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.debug("Создана директория: {}", directoryPath);
                return true;
            }
            return true;
        } catch (IOException e) {
            logger.error("Ошибка при создании директории", e);
            return false;
        }
    }

    public static boolean copyFile(String sourcePath, String destinationPath) {
        try {
            Path source = Paths.get(sourcePath);
            Path destination = Paths.get(destinationPath);

            // Создаем директорию назначения если нужно
            createDirectory(destination.getParent().toString());

            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Файл скопирован: {} -> {}", sourcePath, destinationPath);
            return true;

        } catch (IOException e) {
            logger.error("Ошибка при копировании файла", e);
            return false;
        }
    }

    public static boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                logger.debug("Файл удален: {}", filePath);
            }
            return deleted;
        } catch (IOException e) {
            logger.error("Ошибка при удалении файла", e);
            return false;
        }
    }

    public static long getFileSize(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.size(path);
        } catch (IOException e) {
            logger.error("Ошибка при получении размера файла", e);
            return 0;
        }
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    public static String getFileNameWithoutExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    public static String getHumanReadableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }
}
