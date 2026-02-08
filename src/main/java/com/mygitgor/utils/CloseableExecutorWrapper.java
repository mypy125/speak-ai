package com.mygitgor.utils;

import java.io.Closeable;
import java.util.concurrent.ExecutorService;

public class CloseableExecutorWrapper implements Closeable {
    private final ExecutorService executorService;

    public CloseableExecutorWrapper(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void close() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}
