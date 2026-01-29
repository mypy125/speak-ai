package com.mygitgor.utils;

import java.util.regex.Pattern;

public class ValidationUtils {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]{3,20}$");

    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }

    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }

        // Проверка на наличие хотя бы одной цифры и одной буквы
        boolean hasDigit = false;
        boolean hasLetter = false;

        for (char c : password.toCharArray()) {
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (Character.isLetter(c)) {
                hasLetter = true;
            }

            if (hasDigit && hasLetter) {
                return true;
            }
        }

        return false;
    }

    public static boolean isValidLanguageLevel(String level) {
        if (level == null) {
            return false;
        }

        String[] validLevels = {"A1", "A2", "B1", "B2", "C1", "C2"};
        for (String validLevel : validLevels) {
            if (validLevel.equalsIgnoreCase(level)) {
                return true;
            }
        }
        return false;
    }

    public static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }

        // Удаляем потенциально опасные символы
        return input.replaceAll("[<>\"']", "");
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNullOrBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
