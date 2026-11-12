package uk.gov.pay.connector.app;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.orm.jpa.vendor.EclipseLinkJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import uk.gov.pay.connector.app.config.DatabaseConfig;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * The PostgreSQL connection pool and the JPA persistence unit (EclipseLink) on it, with Spring-managed transactions.
 * Each API request works with one entity manager from start to end, so entities read early in a request are still
 * managed when a later transactional step of the same request changes them.
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
public class PersistenceConfiguration {

    private static final String PERSISTENCE_UNIT = "ConnectorUnit";

    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(ConnectorConfiguration configuration) {
        DatabaseConfig database = configuration.getDatabaseConfig();
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("connector");
        hikari.setDriverClassName(database.getDriverClass());
        hikari.setJdbcUrl(database.getUrl());
        hikari.setUsername(database.getUser());
        hikari.setPassword(database.getPassword());
        hikari.setConnectionTimeout(Math.max(250, database.getMaxWaitForConnection().toMillis()));
        if (database.getValidationQuery() != null) {
            hikari.setConnectionTestQuery(database.getValidationQuery());
        }
        if (database.getValidationQueryTimeout() != null) {
            hikari.setValidationTimeout(Math.max(250, database.getValidationQueryTimeout().toMillis()));
        }
        hikari.setMinimumIdle(database.getMinSize());
        hikari.setMaximumPoolSize(database.getMaxSize());
        hikari.setIdleTimeout(database.getMinIdleTime().toMillis());
        database.getProperties().forEach(hikari::addDataSourceProperty);
        // The pool opens its connections when first used, so the service starts while the database is unavailable.
        hikari.setInitializationFailTimeout(-1);
        return new HikariDataSource(hikari);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
                                                                       ConnectorConfiguration configuration) {
        JPAConfiguration jpaConfiguration = configuration.getJpaConfiguration();
        Map<String, Object> properties = new HashMap<>();
        properties.put("eclipselink.logging.level", jpaConfiguration.getJpaLoggingLevel());
        properties.put("eclipselink.logging.level.sql", jpaConfiguration.getSqlLoggingLevel());
        properties.put("eclipselink.query-results-cache", jpaConfiguration.getCacheSharedDefault());
        properties.put("eclipselink.cache.shared.default", jpaConfiguration.getCacheSharedDefault());
        properties.put("eclipselink.ddl-generation.output-mode", jpaConfiguration.getDdlGenerationOutputMode());
        properties.put("eclipselink.weaving", "false");
        properties.put("eclipselink.logging.logger", "JavaLogger");
        if (configuration.isXrayEnabled()) {
            properties.put("eclipselink.session.customizer", "uk.gov.pay.connector.util.ConnectorSessionCustomiserWithXrayProfiling");
        } else {
            properties.put("eclipselink.session.customizer", "uk.gov.pay.connector.util.ConnectorSessionCustomiser");
        }

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setPersistenceUnitName(PERSISTENCE_UNIT);
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(new EclipseLinkJpaVendorAdapter());
        factory.setPackagesToScan("uk.gov.pay.connector", "uk.gov.service.payments.commons.model",
                "uk.gov.service.payments.commons.jpa");
        factory.setJpaPropertyMap(properties);
        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * The entity manager the DAOs use: the one bound to the current request or transaction.
     */
    @Bean
    public EntityManager entityManager(EntityManagerFactory entityManagerFactory) {
        return SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    }

    @Bean
    public FilterRegistrationBean<OpenEntityManagerInViewFilter> openEntityManagerInViewFilter() {
        FilterRegistrationBean<OpenEntityManagerInViewFilter> registration =
                new FilterRegistrationBean<>(new OpenEntityManagerInViewFilter());
        registration.addInitParameter("entityManagerFactoryBeanName", "entityManagerFactory");
        return registration;
    }
}
