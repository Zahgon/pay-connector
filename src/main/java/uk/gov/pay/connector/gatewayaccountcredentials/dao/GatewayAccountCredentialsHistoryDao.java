package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import jakarta.inject.Provider;
import org.springframework.transaction.annotation.Transactional;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Transactional
@Repository
public class GatewayAccountCredentialsHistoryDao {

    protected final Provider<EntityManager> entityManager;

    @Inject
    public GatewayAccountCredentialsHistoryDao(Provider<EntityManager> entityManager) {
        this.entityManager = entityManager;
    }

    public int delete(String serviceId) {
        String query = "DELETE FROM gateway_account_credentials_history WHERE " +
                "gateway_account_id IN (SELECT gateway_account_id FROM gateway_accounts WHERE service_id = ?1)";
        
        return entityManager.get().createNativeQuery(query)
                .setParameter(1, serviceId)
                .executeUpdate();
    }
}
