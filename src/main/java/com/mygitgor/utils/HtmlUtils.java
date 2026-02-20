package com.mygitgor.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

public class HtmlUtils {
    private static final Logger logger = LoggerFactory.getLogger(HtmlUtils.class);

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile("\\s+");
    private static final Pattern MULTIPLE_NEWLINES_PATTERN = Pattern.compile("\\n\\s*\\n");
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("<(script|style)[^>]*>.*?</\\1>", Pattern.DOTALL);
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[\\*\\_\\`\\#\\[\\]\\(\\)]");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w\\.-]+@[\\w\\.-]+\\.\\w+\\b");

    private static final Pattern MARKDOWN_CODE_BLOCK_PATTERN = Pattern.compile("```.*?```", Pattern.DOTALL);
    private static final Pattern MARKDOWN_HEADER_PATTERN = Pattern.compile("(?m)^#{1,6}\\s+");
    private static final Pattern MARKDOWN_BOLD_STAR_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\*");
    private static final Pattern MARKDOWN_BOLD_UNDERSCORE_PATTERN = Pattern.compile("__(.*?)__");
    private static final Pattern MARKDOWN_ITALIC_STAR_PATTERN = Pattern.compile("\\*(.*?)\\*");
    private static final Pattern MARKDOWN_ITALIC_UNDERSCORE_PATTERN = Pattern.compile("_(.*?)_");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\([^\\)]+\\)");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\([^\\)]+\\)");
    private static final Pattern MARKDOWN_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern MARKDOWN_QUOTE_PATTERN = Pattern.compile("(?m)^>\\s+");
    private static final Pattern MARKDOWN_LIST_STAR_PATTERN = Pattern.compile("(?m)^[\\*\\-\\+]\\s+");
    private static final Pattern MARKDOWN_LIST_NUMBER_PATTERN = Pattern.compile("(?m)^\\d+\\.\\s+");

    private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(\\d+);");
    private static final Pattern HEX_ENTITY_PATTERN = Pattern.compile("&#x([0-9a-fA-F]+);");

    private static final Map<String, String> HTML_ENTITIES = new HashMap<>();

    static {
        HTML_ENTITIES.put("&nbsp;", " ");
        HTML_ENTITIES.put("&amp;", "and");
        HTML_ENTITIES.put("&lt;", "less than");
        HTML_ENTITIES.put("&gt;", "greater than");
        HTML_ENTITIES.put("&quot;", "\"");
        HTML_ENTITIES.put("&#39;", "'");
        HTML_ENTITIES.put("&apos;", "'");
        HTML_ENTITIES.put("&copy;", "copyright");
        HTML_ENTITIES.put("&reg;", "registered");
        HTML_ENTITIES.put("&trade;", "trademark");
        HTML_ENTITIES.put("&euro;", "euro");
        HTML_ENTITIES.put("&pound;", "pound");
        HTML_ENTITIES.put("&yen;", "yen");
        HTML_ENTITIES.put("&cent;", "cent");
        HTML_ENTITIES.put("&sect;", "section");
        HTML_ENTITIES.put("&para;", "paragraph");
        HTML_ENTITIES.put("&bull;", "bullet");
        HTML_ENTITIES.put("&hellip;", "...");
        HTML_ENTITIES.put("&ndash;", "-");
        HTML_ENTITIES.put("&mdash;", "--");
        HTML_ENTITIES.put("&lsquo;", "'");
        HTML_ENTITIES.put("&rsquo;", "'");
        HTML_ENTITIES.put("&ldquo;", "\"");
        HTML_ENTITIES.put("&rdquo;", "\"");
        HTML_ENTITIES.put("&laquo;", "\"");
        HTML_ENTITIES.put("&raquo;", "\"");
        HTML_ENTITIES.put("&deg;", "degrees");
        HTML_ENTITIES.put("&plusmn;", "plus minus");
        HTML_ENTITIES.put("&times;", "times");
        HTML_ENTITIES.put("&divide;", "divided by");
        HTML_ENTITIES.put("&micro;", "micro");
        HTML_ENTITIES.put("&middot;", "middle dot");
    }

    private HtmlUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String stripHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String text = html;

        text = SCRIPT_STYLE_PATTERN.matcher(text).replaceAll(" ");
        text = HTML_COMMENT_PATTERN.matcher(text).replaceAll(" ");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        text = decodeHtmlEntities(text);
        text = MULTIPLE_SPACES_PATTERN.matcher(text).replaceAll(" ").trim();
        return text;
    }

    public static String extractReadableText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String text = html;
        text = SCRIPT_STYLE_PATTERN.matcher(text).replaceAll("");

        text = text.replaceAll("(?i)</?(div|p|h[1-6]|blockquote|section|article)[^>]*>", "\n");
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</?(li|tr|td|th)[^>]*>", "\n• ");
        text = text.replaceAll("(?i)</?table[^>]*>", "\n");

        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");
        text = decodeHtmlEntities(text);

        text = MULTIPLE_NEWLINES_PATTERN.matcher(text).replaceAll("\n\n");

        return text.trim();
    }

    public static String prepareForTts(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String text = html;
        text = SCRIPT_STYLE_PATTERN.matcher(text).replaceAll(" ");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");

        text = decodeHtmlEntities(text);

        text = SPECIAL_CHARS_PATTERN.matcher(text).replaceAll(" ");
        text = URL_PATTERN.matcher(text).replaceAll("link");
        text = EMAIL_PATTERN.matcher(text).replaceAll("email address");
        text = replaceNumbersWithWords(text);

        text = MULTIPLE_SPACES_PATTERN.matcher(text).replaceAll(" ").trim();
        text = naturalizeForTts(text);
        return text;
    }

    public static String stripMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        String text = markdown;

        text = MARKDOWN_CODE_BLOCK_PATTERN.matcher(text).replaceAll(" ");
        text = MARKDOWN_HEADER_PATTERN.matcher(text).replaceAll("");
        text = MARKDOWN_BOLD_STAR_PATTERN.matcher(text).replaceAll("$1");
        text = MARKDOWN_BOLD_UNDERSCORE_PATTERN.matcher(text).replaceAll("$1");
        text = MARKDOWN_ITALIC_STAR_PATTERN.matcher(text).replaceAll("$1");
        text = MARKDOWN_ITALIC_UNDERSCORE_PATTERN.matcher(text).replaceAll("$1");

        text = MARKDOWN_LINK_PATTERN.matcher(text).replaceAll("$1");
        text = MARKDOWN_IMAGE_PATTERN.matcher(text).replaceAll("");
        text = MARKDOWN_CODE_PATTERN.matcher(text).replaceAll("$1");
        text = MARKDOWN_QUOTE_PATTERN.matcher(text).replaceAll("");
        text = MARKDOWN_LIST_STAR_PATTERN.matcher(text).replaceAll("• ");
        text = MARKDOWN_LIST_NUMBER_PATTERN.matcher(text).replaceAll("");
        text = MULTIPLE_SPACES_PATTERN.matcher(text).replaceAll(" ").trim();
        return text;
    }

    private static String decodeHtmlEntities(String text) {
        String result = text;

        for (Map.Entry<String, String> entry : HTML_ENTITIES.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        StringBuffer sb = new StringBuffer();
        Matcher numericMatcher = NUMERIC_ENTITY_PATTERN.matcher(result);
        while (numericMatcher.find()) {
            try {
                int code = Integer.parseInt(numericMatcher.group(1));
                numericMatcher.appendReplacement(sb, String.valueOf((char) code));
            } catch (NumberFormatException e) {
                numericMatcher.appendReplacement(sb, " ");
            }
        }
        numericMatcher.appendTail(sb);
        result = sb.toString();

        sb = new StringBuffer();
        Matcher hexMatcher = HEX_ENTITY_PATTERN.matcher(result);
        while (hexMatcher.find()) {
            try {
                int code = Integer.parseInt(hexMatcher.group(1), 16);
                hexMatcher.appendReplacement(sb, String.valueOf((char) code));
            } catch (NumberFormatException e) {
                hexMatcher.appendReplacement(sb, " ");
            }
        }
        hexMatcher.appendTail(sb);
        result = sb.toString();

        return result;
    }

    private static String replaceNumbersWithWords(String text) {
        String[] units = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        String[] teens = {"ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
                "sixteen", "seventeen", "eighteen", "nineteen"};
        String[] tens = {"", "", "twenty", "thirty", "forty", "fifty",
                "sixty", "seventy", "eighty", "ninety"};

        StringBuilder result = new StringBuilder();
        String[] words = text.split(" ");

        for (String word : words) {
            if (word.matches("\\d+")) {
                int num = Integer.parseInt(word);
                if (num < 10) {
                    result.append(units[num]).append(" ");
                } else if (num < 20) {
                    result.append(teens[num - 10]).append(" ");
                } else if (num < 100) {
                    result.append(tens[num / 10]).append(" ");
                    if (num % 10 != 0) {
                        result.append(units[num % 10]).append(" ");
                    }
                } else {
                    result.append(word).append(" ");
                }
            } else {
                result.append(word).append(" ");
            }
        }

        return result.toString().trim();
    }

    private static String naturalizeForTts(String text) {
        String result = text;

        result = result.replaceAll("\\b(I'm)\\b", "I am");
        result = result.replaceAll("\\b(I've)\\b", "I have");
        result = result.replaceAll("\\b(I'll)\\b", "I will");
        result = result.replaceAll("\\b(I'd)\\b", "I would");
        result = result.replaceAll("\\b(you're)\\b", "you are");
        result = result.replaceAll("\\b(you've)\\b", "you have");
        result = result.replaceAll("\\b(you'll)\\b", "you will");
        result = result.replaceAll("\\b(he's)\\b", "he is");
        result = result.replaceAll("\\b(she's)\\b", "she is");
        result = result.replaceAll("\\b(it's)\\b", "it is");
        result = result.replaceAll("\\b(we're)\\b", "we are");
        result = result.replaceAll("\\b(they're)\\b", "they are");
        result = result.replaceAll("\\b(can't)\\b", "cannot");
        result = result.replaceAll("\\b( don't)\\b", " do not");
        result = result.replaceAll("\\b(doesn't)\\b", "does not");
        result = result.replaceAll("\\b(didn't)\\b", "did not");
        result = result.replaceAll("\\b(won't)\\b", "will not");
        result = result.replaceAll("\\b( wouldn't)\\b", "would not");
        result = result.replaceAll("\\b( shouldn't)\\b", "should not");
        result = result.replaceAll("\\b( couldn't)\\b", "could not");
        result = result.replaceAll("\\b( haven't)\\b", "have not");
        result = result.replaceAll("\\b( hasn't)\\b", "has not");
        result = result.replaceAll("\\b( isn't)\\b", "is not");
        result = result.replaceAll("\\b(aren't)\\b", "are not");
        result = result.replaceAll("\\b(wasn't)\\b", "was not");
        result = result.replaceAll("\\b(weren't)\\b", "were not");

        result = result.replace("%", " percent");
        result = result.replace("+", " plus ");
        result = result.replace("=", " equals ");
        result = result.replace("/", " or ");
        result = result.replace("&", " and ");
        result = result.replace("@", " at ");
        result = result.replace("#", " number ");

        result = result.replace(" .", ".");
        result = result.replace(" ,", ",");
        result = result.replace(" :", ":");
        result = result.replace(" ;", ";");
        result = result.replace(" !", "!");
        result = result.replace(" ?", "?");
        result = result.replace("()", "");
        result = result.replace("[]", "");

        return result;
    }

    public static String replacePhonemes(String text, Map<String, String> phonemeMap) {
        if (text == null || text.isEmpty() || phonemeMap == null) {
            return text;
        }

        String result = text;
        for (Map.Entry<String, String> entry : phonemeMap.entrySet()) {
            result = result.replace("/" + entry.getKey() + "/", "the " + entry.getValue() + " sound");
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public static boolean containsHtml(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return HTML_TAG_PATTERN.matcher(text).find();
    }

    public static boolean isPlainText(String text) {
        return !containsHtml(text);
    }

    public static Map<String, Object> getHtmlStats(String html) {
        Map<String, Object> stats = new HashMap<>();

        if (html == null || html.isEmpty()) {
            stats.put("length", 0);
            stats.put("tagCount", 0);
            stats.put("entityCount", 0);
            return stats;
        }

        int originalLength = html.length();
        String withoutTags = HTML_TAG_PATTERN.matcher(html).replaceAll("");

        stats.put("originalLength", originalLength);
        stats.put("textLength", withoutTags.length());

        Matcher tagMatcher = HTML_TAG_PATTERN.matcher(html);
        int tagCount = 0;
        while (tagMatcher.find()) {
            tagCount++;
        }
        stats.put("tagCount", tagCount);

        int entityCount = 0;
        for (String entity : HTML_ENTITIES.keySet()) {
            int index = 0;
            while ((index = html.indexOf(entity, index)) != -1) {
                entityCount++;
                index += entity.length();
            }
        }
        stats.put("entityCount", entityCount);

        stats.put("containsHtml", containsHtml(html));

        return stats;
    }

    public static void main(String[] args) {
        String testHtml = """
            <div class='info-box'>
                <h2>Hello World!</h2>
                <p>This is a &nbsp; test &amp; example.</p>
                <ul>
                    <li>Item 1</li>
                    <li>Item 2</li>
                </ul>
            </div>
            """;

        String testMarkdown = """
            # Header
            **Bold text** and *italic text*
            [link](https://example.com)
            `code here`
            - list item 1
            - list item 2
            """;

        System.out.println("=== HTML Utils Test ===");
        System.out.println("\n--- HTML Tests ---");
        System.out.println("Original HTML:\n" + testHtml);
        System.out.println("\nStripped HTML:\n" + stripHtml(testHtml));
        System.out.println("\nReadable Text:\n" + extractReadableText(testHtml));
        System.out.println("\nTTS Prepared:\n" + prepareForTts(testHtml));
        System.out.println("\nContains HTML: " + containsHtml(testHtml));
        System.out.println("\nStats: " + getHtmlStats(testHtml));

        System.out.println("\n--- Markdown Tests ---");
        System.out.println("Original Markdown:\n" + testMarkdown);
        System.out.println("\nStripped Markdown:\n" + stripMarkdown(testMarkdown));
    }
}