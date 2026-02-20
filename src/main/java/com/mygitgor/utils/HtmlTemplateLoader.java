package com.mygitgor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HtmlTemplateLoader {
    private static final Logger logger = LoggerFactory.getLogger(HtmlTemplateLoader.class);

    private static final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> templateTimestamps = new ConcurrentHashMap<>();
    private static final String TEMPLATE_PATH = "/templates/";

    // Паттерн для извлечения содержимого между тегами body
    private static final Pattern BODY_CONTENT_PATTERN = Pattern.compile(
            "(?is)<body[^>]*>(.*?)</body>"
    );

    // Паттерн для удаления HTML тегов
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    public static final String COLOR_PRIMARY = "#3498db";
    public static final String COLOR_SUCCESS = "#27ae60";
    public static final String COLOR_WARNING = "#f39c12";
    public static final String COLOR_DANGER = "#e74c3c";
    public static final String COLOR_PURPLE = "#8e44ad";
    public static final String COLOR_ORANGE = "#e67e22";

    public static String loadTemplate(String templateName) {
        return templateCache.computeIfAbsent(templateName, name -> {
            String resourcePath = TEMPLATE_PATH + name;
            long startTime = System.currentTimeMillis();

            try (InputStream is = HtmlTemplateLoader.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    logger.error("Шаблон не найден: {}", resourcePath);
                    return getDefaultTemplate();
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String content = reader.lines().collect(Collectors.joining("\n"));

                    // Сохраняем метку времени загрузки
                    templateTimestamps.put(name, System.currentTimeMillis());

                    long loadTime = System.currentTimeMillis() - startTime;
                    logger.debug("Шаблон загружен: {}, размер: {} байт, время: {} мс",
                            resourcePath, content.length(), loadTime);

                    return content;
                }
            } catch (IOException e) {
                logger.error("Ошибка при загрузке шаблона: {}", resourcePath, e);
                return getDefaultTemplate();
            }
        });
    }

    public static String wrapWithTemplate(String templateName, String content) {
        String template = loadTemplate(templateName);
        return template.replace("{{CONTENT}}", content);
    }

    public static String wrapWithTemplate(String templateName, String content, Map<String, String> params) {
        String template = loadTemplate(templateName);
        String result = template.replace("{{CONTENT}}", content);

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }

        return result;
    }

    /**
     * Извлекает только содержимое между тегами <body> и очищает от HTML
     * Идеально подходит для TTS
     */
    public static String extractBodyContentForTts(String fullHtml) {
        if (fullHtml == null || fullHtml.isEmpty()) {
            return "";
        }

        // Извлекаем содержимое между тегами body
        java.util.regex.Matcher matcher = BODY_CONTENT_PATTERN.matcher(fullHtml);
        String bodyContent = matcher.find() ? matcher.group(1) : fullHtml;

        // Удаляем все HTML теги из содержимого body
        String textOnly = HTML_TAG_PATTERN.matcher(bodyContent).replaceAll(" ");

        // Нормализуем пробелы
        textOnly = MULTIPLE_SPACES.matcher(textOnly).replaceAll(" ").trim();

        // Декодируем HTML сущности
        textOnly = decodeHtmlEntities(textOnly);

        return textOnly;
    }

    /**
     * Извлекает только содержимое {{CONTENT}} из обработанного шаблона
     */
    public static String extractContent(String wrappedHtml) {
        if (wrappedHtml == null || wrappedHtml.isEmpty()) {
            return "";
        }

        // Ищем содержимое между тегами body
        java.util.regex.Matcher matcher = BODY_CONTENT_PATTERN.matcher(wrappedHtml);
        if (matcher.find()) {
            String bodyContent = matcher.group(1);
            // Удаляем HTML теги из содержимого
            String textOnly = HTML_TAG_PATTERN.matcher(bodyContent).replaceAll(" ");
            textOnly = MULTIPLE_SPACES.matcher(textOnly).replaceAll(" ").trim();
            return decodeHtmlEntities(textOnly);
        }

        // Если не нашли body, просто удаляем все теги
        String textOnly = HTML_TAG_PATTERN.matcher(wrappedHtml).replaceAll(" ");
        textOnly = MULTIPLE_SPACES.matcher(textOnly).replaceAll(" ").trim();
        return decodeHtmlEntities(textOnly);
    }

    /**
     * Быстрый метод для получения текста для TTS из результата wrapWithTemplate
     */
    public static String getTtsText(String templateName, String content) {
        String wrapped = wrapWithTemplate(templateName, content);
        return extractContent(wrapped);
    }

    /**
     * Очищает HTML от тегов, но сохраняет структуру текста
     */
    public static String stripHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String text = HTML_TAG_PATTERN.matcher(html).replaceAll(" ");
        text = MULTIPLE_SPACES.matcher(text).replaceAll(" ").trim();
        return decodeHtmlEntities(text);
    }

    private static String decodeHtmlEntities(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "and")
                .replace("&lt;", "less than")
                .replace("&gt;", "greater than")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&ndash;", " to ")
                .replace("&mdash;", ", ")
                .replace("&hellip;", "... ")
                .replace("&copy;", "copyright ")
                .replace("&reg;", "registered ")
                .replace("&trade;", "trademark ");
    }

    // ... остальные методы (createBlock, createBadge, createProgressBar и т.д.) ...
    public static String createBlock(String title, String content, String type) {
        String blockClass = switch (type.toLowerCase()) {
            case "success" -> "success-box";
            case "warning" -> "warning-box";
            case "tip" -> "tip-box";
            default -> "info-box";
        };

        return String.format("""
            <div class='%s'>
                <h3>%s</h3>
                %s
            </div>
            """, blockClass, title, content);
    }

    public static String createBadge(String text, String type) {
        String badgeClass = switch (type.toLowerCase()) {
            case "green" -> "badge badge-green";
            case "blue" -> "badge badge-blue";
            case "orange" -> "badge badge-orange";
            case "purple" -> "badge badge-purple";
            default -> "badge";
        };

        return String.format("<span class='%s'>%s</span>", badgeClass, text);
    }

    public static String createProgressBar(double value, String color, String label) {
        String colorClass = switch (color.toLowerCase()) {
            case "green" -> "progress-green";
            case "blue" -> "progress-blue";
            case "orange" -> "progress-orange";
            case "purple" -> "progress-purple";
            default -> "progress-blue";
        };

        double percent = Math.min(100, Math.max(0, value));

        return String.format("""
            <div class='progress-section'>
                <div class='stat-row'>
                    <span class='stat-label'>%s</span>
                    <span class='stat-value' style='color: %s;'>%.1f%%</span>
                </div>
                <div class='progress-bar'>
                    <div class='progress-fill %s' style='width: %.1f%%;'></div>
                </div>
            </div>
            """, label, getColorForType(color), percent, colorClass, percent);
    }

    public static String createStatsTable(Map<String, String> stats) {
        StringBuilder table = new StringBuilder("<table class='stats-table'>");

        for (Map.Entry<String, String> entry : stats.entrySet()) {
            table.append("<tr>")
                    .append("<td>").append(entry.getKey()).append(":</td>")
                    .append("<td style='font-weight: bold;'>").append(entry.getValue()).append("</td>")
                    .append("</tr>");
        }

        table.append("</table>");
        return table.toString();
    }

    public static String createWordChips(java.util.List<String> words) {
        StringBuilder chips = new StringBuilder("<div class='word-chips'>");
        for (String word : words) {
            chips.append("<span class='word-chip'>").append(word).append("</span>");
        }
        chips.append("</div>");
        return chips.toString();
    }

    public static String createPhonemeChips(java.util.List<String> phonemes) {
        StringBuilder chips = new StringBuilder("<div class='phoneme-chips'>");
        for (String phoneme : phonemes) {
            chips.append("<span class='phoneme-chip'>/").append(phoneme).append("/</span>");
        }
        chips.append("</div>");
        return chips.toString();
    }

    private static String getColorForType(String type) {
        return switch (type.toLowerCase()) {
            case "green" -> COLOR_SUCCESS;
            case "blue" -> COLOR_PRIMARY;
            case "orange" -> COLOR_ORANGE;
            case "purple" -> COLOR_PURPLE;
            case "red" -> COLOR_DANGER;
            case "yellow" -> COLOR_WARNING;
            default -> COLOR_PRIMARY;
        };
    }

    private static String getDefaultTemplate() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { 
                        font-family: 'Segoe UI', sans-serif; 
                        font-size: 13px; 
                        color: #2c3e50; 
                        margin: 0; 
                        padding: 8px;
                        line-height: 1.5;
                    }
                    h3 { color: #3498db; }
                    .info-box { 
                        background-color: #e8f4f8; 
                        padding: 10px; 
                        border-radius: 8px; 
                        margin: 8px 0;
                        border-left: 4px solid #3498db;
                    }
                    .success-box { 
                        background-color: #e8f5e9; 
                        padding: 10px; 
                        border-radius: 8px; 
                        margin: 8px 0;
                        border-left: 4px solid #27ae60;
                    }
                    .warning-box { 
                        background-color: #fef9e7; 
                        padding: 10px; 
                        border-radius: 8px; 
                        margin: 8px 0;
                        border-left: 4px solid #f39c12;
                    }
                    .badge-green { background-color: #27ae60; color: white; padding: 4px 10px; border-radius: 20px; }
                    .word-chip { background-color: #2ecc71; color: white; padding: 4px 12px; border-radius: 20px; display: inline-block; margin: 3px; }
                </style>
            </head>
            <body>
                <div class='info-box'>
                    <h3>⚠️ Упрощенный шаблон</h3>
                    <p>Оригинальный шаблон не найден. Используется базовая версия.</p>
                </div>
                {{CONTENT}}
            </body>
            </html>
            """;
    }

    public static boolean isTemplateCached(String templateName) {
        return templateCache.containsKey(templateName);
    }

    public static long getTemplateLoadTime(String templateName) {
        return templateTimestamps.getOrDefault(templateName, -1L);
    }

    public static void clearCache() {
        templateCache.clear();
        templateTimestamps.clear();
        logger.info("Кэш шаблонов очищен");
    }

    public static int getCacheSize() {
        return templateCache.size();
    }

    public static void preloadTemplate(String templateName) {
        CompletableFuture.runAsync(() -> {
            try {
                loadTemplate(templateName);
                logger.debug("Шаблон предзагружен: {}", templateName);
            } catch (Exception e) {
                logger.warn("Не удалось предзагрузить шаблон {}: {}", templateName, e.getMessage());
            }
        });
    }
}