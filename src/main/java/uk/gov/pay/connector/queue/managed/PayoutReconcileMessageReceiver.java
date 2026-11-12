package uk.gov.pay.connector.queue.managed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.payout.PayoutReconcileProcess;

import jakarta.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PayoutReconcileMessageReceiver implements SmartLifecycle {

    private static final String PAYOUT_RECONCILE_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-payoutReconcileMessageReceiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(PayoutReconcileMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;
    private final PayoutReconcileProcess payoutReconcileProcess;
    private final boolean payoutReconcileQueueEnabled;
    private ScheduledExecutorService payoutReconcileMessageExecutorService;
    private volatile boolean running;

    @Inject
    public PayoutReconcileMessageReceiver(PayoutReconcileProcess payoutReconcileProcess,
                                          ConnectorConfiguration connectorConfiguration) {
        this.payoutReconcileProcess = payoutReconcileProcess;

        int queueScheduleNumberOfThreads = connectorConfiguration.getPayoutReconcileProcessConfig()
                .getQueueSchedulerNumberOfThreads();

        payoutReconcileMessageExecutorService = ScheduledExecutors.create(PAYOUT_RECONCILE_MESSAGE_RECEIVER_THREAD_NAME, queueScheduleNumberOfThreads);

        queueSchedulerThreadDelayInSeconds = connectorConfiguration.getPayoutReconcileProcessConfig().getQueueSchedulerThreadDelayInSeconds();
        payoutReconcileQueueEnabled = connectorConfiguration.getPayoutReconcileProcessConfig().getPayoutReconcileQueueEnabled();
    }

    @Override
    public void start() {
        running = true;
        if (payoutReconcileQueueEnabled) {
            int initialDelay = queueSchedulerThreadDelayInSeconds;
            payoutReconcileMessageExecutorService.scheduleWithFixedDelay(
                    this::processPayouts,
                    initialDelay,
                    queueSchedulerThreadDelayInSeconds,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void stop() {
        running = false;
        LOGGER.info("Shutting down payout reconciliation service");
        payoutReconcileMessageExecutorService.shutdownNow();
        LOGGER.info("Payout reconciliation service shut down");
    }

    private void processPayouts() {
        try {
            payoutReconcileProcess.processPayouts();
        } catch (Exception e) {
            LOGGER.warn("Queue message payoutReconcileMessageReceiver thread exception [class={} message={}]", e.getClass(), e.getMessage());
        }
    }
}
