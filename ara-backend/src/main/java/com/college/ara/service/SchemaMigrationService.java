package com.college.ara.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class SchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationService(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        migrateAllocationTable();
        migrateUserTable();
    }

    private void migrateAllocationTable() {
        if (!tableExists("ALLOCATION")) {
            return;
        }

        addColumnIfMissing("ALLOCATION", "YEAR_GROUP", "varchar(40)");
        addColumnIfMissing("ALLOCATION", "FACULTY_NAME", "varchar(255)");
        addColumnIfMissing("ALLOCATION", "ALLOCATION_TYPE", "varchar(30)");
        addColumnIfMissing("ALLOCATION", "SOURCE_LABEL", "varchar(60)");

        jdbcTemplate.execute("UPDATE allocation SET year_group = COALESCE(year_group, 'II Year')");
        jdbcTemplate.execute("UPDATE allocation SET allocation_type = COALESCE(allocation_type, 'LECTURE')");
        makeColumnNullable("ALLOCATION", "RESOURCE_ID");
    }

    private void migrateUserTable() {
        if (!tableExists("USER")) {
            return;
        }

        addColumnIfMissing("USER", "DEPARTMENT", "varchar(100)");
        addColumnIfMissing("USER", "SUBJECTS_HANDLED", "varchar(255)");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        if (columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?",
                Integer.class,
                tableName,
                columnName);
        return count != null && count > 0;
    }

    private void makeColumnNullable(String tableName, String columnName) {
        if (!columnExists(tableName, columnName)) {
            return;
        }

        try {
            // H2/PostgreSQL style
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " DROP NOT NULL");
            return;
        } catch (Exception ignored) {
            // Fall back to MySQL-style syntax below.
        }

        String columnType = jdbcTemplate.queryForObject(
                "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ? AND TABLE_SCHEMA = DATABASE()",
                String.class,
                tableName,
                columnName);

        if (columnType != null && !columnType.isBlank()) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " " + columnType + " NULL");
        }
    }
}
