package github.scarsz.discordsrv.util;

import github.scarsz.discordsrv.DiscordSRV;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task scheduling. Replaces the Bukkit scheduler: "sync" tasks run on the server thread through the platform,
 * asynchronous and timed tasks run on DiscordSRV's own scheduler threads. Delays and periods are given in
 * ticks (1 tick = 50ms) to keep the Spigot version's semantics.
 */
public class SchedulerUtil {

    private static final long MILLIS_PER_TICK = 50L;
    private static final Set<Future<?>> TASKS = ConcurrentHashMap.newKeySet();
    private static ScheduledExecutorService executor;

    private SchedulerUtil() {}

    private static synchronized ScheduledExecutorService executor() {
        if (executor == null || executor.isShutdown()) {
            AtomicInteger counter = new AtomicInteger();
            ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(4, runnable -> {
                Thread thread = new Thread(runnable, "DiscordSRV - Scheduler #" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
            pool.setRemoveOnCancelPolicy(true);
            pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            executor = pool;
        }
        return executor;
    }

    private static Runnable wrap(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                DiscordSRV.error("Error in a scheduled DiscordSRV task", t);
            }
        };
    }

    private static <T extends Future<?>> T track(T future) {
        TASKS.removeIf(Future::isDone);
        TASKS.add(future);
        return future;
    }

    /**
     * Runs the task on the server thread
     */
    public static void runTask(Runnable runnable) {
        DiscordSRV.getPlatform().runOnMainThread(wrap(runnable));
    }

    public static Future<?> runTaskAsynchronously(Runnable runnable) {
        return track(executor().submit(wrap(runnable)));
    }

    /**
     * Runs the task on the server thread after the given delay in ticks
     */
    public static ScheduledFuture<?> runTaskLater(Runnable runnable, long delayTicks) {
        return track(executor().schedule(() -> runTask(runnable), delayTicks * MILLIS_PER_TICK, TimeUnit.MILLISECONDS));
    }

    public static ScheduledFuture<?> runTaskLaterAsynchronously(Runnable runnable, long delayTicks) {
        return track(executor().schedule(wrap(runnable), delayTicks * MILLIS_PER_TICK, TimeUnit.MILLISECONDS));
    }

    public static ScheduledFuture<?> runTaskTimerAsynchronously(Runnable runnable, long initialDelayTicks, long periodTicks) {
        return track(executor().scheduleAtFixedRate(wrap(runnable), initialDelayTicks * MILLIS_PER_TICK,
                Math.max(1, periodTicks) * MILLIS_PER_TICK, TimeUnit.MILLISECONDS));
    }

    /**
     * Cancels all scheduled tasks and shuts down the scheduler threads
     */
    public static synchronized void cancelTasks() {
        TASKS.forEach(task -> task.cancel(false));
        TASKS.clear();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

}
