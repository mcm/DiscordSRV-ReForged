/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

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
