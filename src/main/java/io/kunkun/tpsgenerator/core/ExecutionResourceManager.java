package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.config.Constants;
import io.kunkun.tpsgenerator.config.TestConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the thread pool, scheduler, and shutdown lifecycle for a load test.
 *
 * <p>Responsible for creating and gracefully tearing down the worker
 * {@link ExecutorService} and the single-threaded rate-update
 * {@link ScheduledExecutorService}.  Also registers (and removes) a JVM
 * shutdown hook so that in-flight workers are not abandoned on SIGTERM.
 */
@Slf4j
public class ExecutionResourceManager {

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final Thread shutdownHook;

    /**
     * Creates the executor and scheduler based on the thread-pool section of
     * the supplied config.
     *
     * @param config test configuration; must not be {@code null}
     */
    public ExecutionResourceManager(TestConfig config) {
        this.executor = new ThreadPoolExecutor(
                config.getThreadPool().getCoreSize(),
                config.getThreadPool().getMaxSize(),
                config.getThreadPool().getKeepAliveTime().toMillis(),
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.getThreadPool().getQueueSize()),
                new ThreadFactory() {
                    private final AtomicLong counter = new AtomicLong(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("tps-worker-" + counter.incrementAndGet());
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.scheduler = Executors.newScheduledThreadPool(1);

        this.shutdownHook = new Thread(this::shutdownGracefullyQuiet, "tps-shutdown-hook");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the worker {@link ExecutorService}.
     *
     * @return the executor; never {@code null}
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Returns the single-threaded {@link ScheduledExecutorService} used for
     * periodic rate updates and progress logging.
     *
     * @return the scheduler; never {@code null}
     */
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    // -------------------------------------------------------------------------
    // Shutdown-hook management
    // -------------------------------------------------------------------------

    /**
     * Registers a JVM shutdown hook that calls {@link #shutdownGracefully}.
     * Must be called after all initialisation has succeeded to avoid orphaned
     * hooks if a constructor throws.
     */
    public void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Removes the JVM shutdown hook registered by {@link #addShutdownHook()}.
     * Safe to call even when the hook was never added or the JVM is already
     * shutting down (the latter is silently ignored).
     */
    public void removeShutdownHook() {
        try {
            if (!Thread.currentThread().equals(shutdownHook)) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
        } catch (IllegalStateException e) {
            // JVM is already shutting down — ignore
        }
    }

    // -------------------------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------------------------

    /**
     * Shuts down both the executor and the scheduler gracefully, waiting up to
     * {@code timeout} in the given {@code unit} for in-flight tasks to finish.
     * If tasks do not finish within the timeout they are interrupted with
     * {@code shutdownNow()}.
     *
     * @param timeout maximum time to wait for each service to terminate
     * @param unit    time unit for the timeout
     */
    public void shutdownGracefully(long timeout, TimeUnit unit) {
        log.debug("Shutting down executor resources");
        shutdownService(executor, timeout, unit);
        shutdownService(scheduler, timeout, unit);
    }

    /**
     * Convenience overload that uses {@link Constants#GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS}.
     */
    public void shutdownGracefully() {
        shutdownGracefully(Constants.GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Called from the shutdown hook — must not throw. */
    private void shutdownGracefullyQuiet() {
        try {
            shutdownGracefully();
        } catch (Exception e) {
            log.warn("Error during shutdown-hook cleanup", e);
        }
    }

    private static void shutdownService(ExecutorService svc, long timeout, TimeUnit unit) {
        if (svc.isShutdown()) {
            return;
        }
        svc.shutdown();
        try {
            if (!svc.awaitTermination(timeout, unit)) {
                svc.shutdownNow();
            }
        } catch (InterruptedException e) {
            svc.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
