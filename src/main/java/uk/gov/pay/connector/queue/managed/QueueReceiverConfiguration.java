package uk.gov.pay.connector.queue.managed;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.StateTransitionEmitterProcess;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.payout.PayoutReconcileProcess;
import uk.gov.pay.connector.queue.tasks.TaskQueueMessageHandler;

/**
 * The receivers that poll the SQS queues while the service runs. The capture queue and the payment state transition
 * queue are only polled while background processing is enabled.
 */
@Configuration(proxyBeanMethods = false)
public class QueueReceiverConfiguration {

    @Bean
    @Conditional(BackgroundProcessingEnabled.class)
    public CaptureMessageReceiver captureMessageReceiver(CardCaptureProcess cardCaptureProcess,
                                                         ConnectorConfiguration connectorConfiguration) {
        return new CaptureMessageReceiver(cardCaptureProcess, connectorConfiguration);
    }

    @Bean
    @Conditional(BackgroundProcessingEnabled.class)
    public StateTransitionMessageReceiver stateTransitionMessageReceiver(StateTransitionEmitterProcess stateTransitionEmitterProcess,
                                                                         ConnectorConfiguration connectorConfiguration) {
        return new StateTransitionMessageReceiver(stateTransitionEmitterProcess, connectorConfiguration);
    }

    @Bean
    public PayoutReconcileMessageReceiver payoutReconcileMessageReceiver(PayoutReconcileProcess payoutReconcileProcess,
                                                                         ConnectorConfiguration connectorConfiguration) {
        return new PayoutReconcileMessageReceiver(payoutReconcileProcess, connectorConfiguration);
    }

    @Bean
    public TaskQueueMessageReceiver taskQueueMessageReceiver(TaskQueueMessageHandler taskQueueMessageHandler,
                                                             ConnectorConfiguration connectorConfiguration) {
        return new TaskQueueMessageReceiver(taskQueueMessageHandler, connectorConfiguration);
    }

    /**
     * Matches while the service's settings enable background processing.
     */
    static class BackgroundProcessingEnabled implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getBeanFactory().getBean(ConnectorConfiguration.class)
                    .getCaptureProcessConfig().getBackgroundProcessingEnabled();
        }
    }
}
