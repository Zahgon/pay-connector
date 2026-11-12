package uk.gov.pay.connector.common.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps one entity manager open for a unit of work that spans several transactions, so that the entities it reads and
 * writes stay managed between them. Every transaction started while the unit of work is open runs on its entity
 * manager.
 */
@Component
public class UnitOfWork {

    private final EntityManagerFactory entityManagerFactory;

    public UnitOfWork(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * Opens the unit of work on the current thread, unless one is already open.
     *
     * @return whether this call opened it (and its {@link #end(boolean)} must close it)
     */
    public boolean begin() {
        if (TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
            return false;
        }
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(entityManager));
        return true;
    }

    /**
     * Closes the unit of work on the current thread if {@code opened} says {@link #begin()} opened it.
     */
    public void end(boolean opened) {
        if (opened) {
            EntityManagerHolder holder =
                    (EntityManagerHolder) TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            holder.getEntityManager().close();
        }
    }
}
