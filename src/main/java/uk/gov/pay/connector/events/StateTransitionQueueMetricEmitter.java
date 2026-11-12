package uk.gov.pay.connector.events;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

import uk.gov.pay.connector.queue.statetransition.StateTransitionQueue;

import jakarta.inject.Inject;
import org.springframework.stereotype.Component;

@Component
public class StateTransitionQueueMetricEmitter {
    private final MetricRegistry metricRegistry;
    private StateTransitionQueue stateTransitionQueue;

    @Inject
    public StateTransitionQueueMetricEmitter(
            MetricRegistry metricRegistry,
            StateTransitionQueue stateTransitionQueue) {
        
        this.stateTransitionQueue = stateTransitionQueue;
        this.metricRegistry = metricRegistry;
    }
    
    public void register() {
        final Gauge<Integer> gauge = () -> stateTransitionQueue.size();
        
        metricRegistry.register("state-transition.in-memory-queue.size", gauge);
    }
}
