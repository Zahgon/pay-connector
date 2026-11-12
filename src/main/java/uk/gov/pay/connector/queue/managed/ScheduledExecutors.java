package uk.gov.pay.connector.queue.managed;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Creates the scheduled executors the queue receivers poll their queues with. Their threads are named after
 * {@code nameFormat} (a {@code %d} in it is replaced by the thread's number); the receivers shut them down when
 * they stop.
 */
final class ScheduledExecutors {

    private ScheduledExecutors() {
    }

    static ScheduledExecutorService create(String nameFormat, int threads) {
        return new ScheduledThreadPoolExecutor(threads, new ThreadFactoryBuilder().setNameFormat(nameFormat).build());
    }
}
