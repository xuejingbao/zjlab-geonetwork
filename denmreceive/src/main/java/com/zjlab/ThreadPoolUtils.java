package com.zjlab;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author xue
 * @create 2022-10-27 13:38
 */
public class ThreadPoolUtils {
    public final static Integer KEEP_ALIVE_TIME = 60;
    public final static Integer QUEUE_SIZE = 100;

    public static ThreadPoolExecutor getThreadPool(int corePoolSize,
                                                   int maximumPoolSize,
                                                   String poolName) {
        return new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE),
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, poolName + "-" + count.getAndAdd(1));
                    }
                }
        );
    }

    public static ScheduledThreadPoolExecutor getScheduledPool(int corePoolSize, String poolName) {
        return new ScheduledThreadPoolExecutor(1,
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, poolName + "-" + count.getAndAdd(1));
                    }
                }
        );
    }
}
