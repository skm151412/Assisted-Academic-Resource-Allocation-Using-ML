package com.college.ara.config;

import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@ComponentScan(basePackages = "com.college.ara.service")
@EnableJpaRepositories(basePackages = "com.college.ara.repository")
@PropertySource("classpath:db.properties")
public class AppConfig {

    private static final String FALLBACK_FLAG = "ara.datasource.fallback";

    @Bean
    public DataSource dataSource(Environment environment) {
        String rawUrl = firstNonBlank(
            environment.getProperty("db.url", ""),
            environment.getProperty("DB_URL", ""));
        String dbUrl = normalizeJdbcUrl(rawUrl);
        String dbUser = firstNonBlank(
            environment.getProperty("db.username", ""),
            environment.getProperty("DB_USERNAME", ""));
        String dbPassword = firstNonBlank(
            environment.getProperty("db.password", ""),
            environment.getProperty("DB_PASSWORD", ""));

        if (dbUrl.isBlank()) {
            return h2FallbackDataSource();
        }

        String driver = firstNonBlank(
            environment.getProperty("db.driverClassName", ""),
            environment.getProperty("DB_DRIVER", ""));
        if (driver.isBlank()) {
            driver = dbUrl.startsWith("jdbc:mysql:") ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver";
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driver);
        dataSource.setUrl(dbUrl);
        dataSource.setUsername(dbUser);
        dataSource.setPassword(dbPassword);

        if (dbUrl.startsWith("jdbc:mysql:")) {
            try (Connection ignored = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                System.clearProperty(FALLBACK_FLAG);
                return dataSource;
            } catch (Exception ex) {
                System.err.println("[ARA] MySQL connection failed at startup. Falling back to embedded H2. Cause: " + ex.getMessage());
                return h2FallbackDataSource();
            }
        }

        System.clearProperty(FALLBACK_FLAG);
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, Environment environment) {
        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPackagesToScan("com.college.ara.model");
        factoryBean.setJpaVendorAdapter(jpaVendorAdapter());
        factoryBean.setJpaProperties(jpaProperties(environment));
        return factoryBean;
    }

    @Bean
    public JpaVendorAdapter jpaVendorAdapter() {
        return new HibernateJpaVendorAdapter();
    }

    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean factoryBean) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(factoryBean.getObject());
        return transactionManager;
    }

    @Bean
    public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();
    }

    private Properties jpaProperties(Environment environment) {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", environment.getProperty("hibernate.hbm2ddl", "update"));
        String fallback = System.getProperty(FALLBACK_FLAG, "false");
        String configuredDialect = firstNonBlank(
            environment.getProperty("hibernate.dialect", ""),
            environment.getProperty("HIBERNATE_DIALECT", ""));
        String dialect = "true".equalsIgnoreCase(fallback)
                ? "org.hibernate.dialect.H2Dialect"
            : (configuredDialect.isBlank() ? "org.hibernate.dialect.H2Dialect" : configuredDialect);
        properties.setProperty("hibernate.dialect", dialect);
        properties.setProperty("hibernate.show_sql", environment.getProperty("hibernate.show_sql", "false"));
        properties.setProperty("hibernate.jdbc.time_zone", "UTC");
        return properties;
    }

    private DataSource h2FallbackDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:ara;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        System.setProperty(FALLBACK_FLAG, "true");
        return dataSource;
    }

    private String normalizeJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String normalized = url.trim();
        if (normalized.startsWith("jdbc:mysql:") || normalized.startsWith("mysql://")) {
            normalized = normalized.replaceAll("\\s+", "");
        }
        if (normalized.startsWith("mysql://")) {
            return "jdbc:" + normalized;
        }
        return normalized;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.trim().isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }
}
