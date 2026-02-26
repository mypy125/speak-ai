package com.mygitgor.utils.jpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvironmentDetector {
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentDetector.class);

    private static final boolean IS_JPRO_HEADLESS;

    static {
        boolean jproHeadless = false;
        try {
            String glassPlatform = System.getProperty("glass.platform", "");
            String monoclePlatform = System.getProperty("monocle.platform", "");

            jproHeadless = "Monocle".equals(glassPlatform) && "Headless".equals(monoclePlatform);

            if (jproHeadless) {
                logger.info("Обнаружен JPro headless режим. WebView будет отключен.");
            } else {
                logger.info("Обычный режим JavaFX. WebView доступен.");
            }
        } catch (Exception e) {
            logger.warn("Ошибка при определении окружения", e);
        }

        IS_JPRO_HEADLESS = jproHeadless;
    }

    public static boolean isJProHeadless() {
        return IS_JPRO_HEADLESS;
    }
}