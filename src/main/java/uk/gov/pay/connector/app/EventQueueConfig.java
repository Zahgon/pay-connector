package uk.gov.pay.connector.app;


public class EventQueueConfig {
    private Boolean eventQueueEnabled;
    private int paymentStateTransitionPollerNumberOfThreads;

    public Boolean getEventQueueEnabled() {
        return eventQueueEnabled;
    }

    public int getPaymentStateTransitionPollerNumberOfThreads() {
        return paymentStateTransitionPollerNumberOfThreads;
    }
}
