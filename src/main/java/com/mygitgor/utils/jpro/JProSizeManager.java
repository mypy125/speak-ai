package com.mygitgor.utils.jpro;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JProSizeManager {
    private static final Logger logger = LoggerFactory.getLogger(JProSizeManager.class);

    private static final double DEFAULT_MIN_WIDTH = 100;
    private static final double DEFAULT_PREF_WIDTH = 400;
    private static final double DEFAULT_MIN_HEIGHT = 50;
    private static final double DEFAULT_PREF_HEIGHT = 100;

    public static void ensureNonZeroWidth(Region region, String nodeName) {
        if (region == null) return;

        if (region.getWidth() == 0) {
            logger.debug("Region {} has zero width, forcing min width", nodeName);

            if (region.getMinWidth() == Region.USE_COMPUTED_SIZE) {
                region.setMinWidth(DEFAULT_MIN_WIDTH);
            }
            if (region.getPrefWidth() == Region.USE_COMPUTED_SIZE) {
                region.setPrefWidth(DEFAULT_PREF_WIDTH);
            }

            region.applyCss();
            region.layout();
        }
    }

    public static void ensureNonZeroHeight(Region region, String nodeName) {
        if (region == null) return;

        if (region.getHeight() == 0) {
            logger.debug("Region {} has zero height, forcing min height", nodeName);

            if (region.getMinHeight() == Region.USE_COMPUTED_SIZE) {
                region.setMinHeight(DEFAULT_MIN_HEIGHT);
            }
            if (region.getPrefHeight() == Region.USE_COMPUTED_SIZE) {
                region.setPrefHeight(DEFAULT_PREF_HEIGHT);
            }

            region.applyCss();
            region.layout();
        }
    }

    public static void ensureNonZeroSize(Region region, String nodeName) {
        ensureNonZeroWidth(region, nodeName);
        ensureNonZeroHeight(region, nodeName);
    }

    public static void setMinSizes(Region region, double minWidth, double minHeight) {
        if (region == null) return;

        if (region.getMinWidth() == Region.USE_COMPUTED_SIZE) {
            region.setMinWidth(minWidth);
        }
        if (region.getMinHeight() == Region.USE_COMPUTED_SIZE) {
            region.setMinHeight(minHeight);
        }
    }

    public static void addWidthListener(Region region, String nodeName, Runnable onZeroWidth) {
        if (region == null) return;

        region.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() == 0) {
                logger.warn("{} width became 0", nodeName);
                if (onZeroWidth != null) {
                    Platform.runLater(onZeroWidth);
                }
            }
        });
    }

    public static void addHeightListener(Region region, String nodeName, Runnable onZeroHeight) {
        if (region == null) return;

        region.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() == 0) {
                logger.warn("{} height became 0", nodeName);
                if (onZeroHeight != null) {
                    Platform.runLater(onZeroHeight);
                }
            }
        });
    }

    public static boolean isRegion(Node node) {
        return node instanceof Region;
    }

    public static double safeGetWidth(Node node) {
        if (node instanceof Region) {
            return ((Region) node).getWidth();
        }
        return -1;
    }

    public static double safeGetHeight(Node node) {
        if (node instanceof Region) {
            return ((Region) node).getHeight();
        }
        return -1;
    }
}