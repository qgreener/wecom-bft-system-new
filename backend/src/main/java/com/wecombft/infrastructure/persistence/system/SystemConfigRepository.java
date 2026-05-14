package com.wecombft.infrastructure.persistence.system;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public SystemConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SystemConfigRecord> findByGroup(String configGroup) {
        return jdbcTemplate.query(
            """
            select id, config_group, config_key, display_name, config_value, masked_value,
                   sensitive_flag, editable_flag, updated_at, updated_by, version
            from sys_config
            where config_group = ?
            order by id
            """,
            this::mapConfig,
            configGroup);
    }

    public Optional<SystemConfigRecord> findByGroupAndKey(String configGroup, String configKey) {
        return jdbcTemplate.query(
                """
                select id, config_group, config_key, display_name, config_value, masked_value,
                       sensitive_flag, editable_flag, updated_at, updated_by, version
                from sys_config
                where config_group = ? and config_key = ?
                """,
                this::mapConfig,
                configGroup,
                configKey)
            .stream()
            .findFirst();
    }

    public void updateValue(long configId, String configValue, String maskedValue, long updatedBy) {
        jdbcTemplate.update(
            """
            update sys_config
            set config_value = ?,
                masked_value = ?,
                updated_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ?
            """,
            configValue,
            maskedValue,
            LocalDateTime.now(),
            updatedBy,
            configId);
    }

    private SystemConfigRecord mapConfig(ResultSet resultSet, int rowNum) throws SQLException {
        return new SystemConfigRecord(
            resultSet.getLong("id"),
            resultSet.getString("config_group"),
            resultSet.getString("config_key"),
            resultSet.getString("display_name"),
            resultSet.getString("config_value"),
            resultSet.getString("masked_value"),
            resultSet.getInt("sensitive_flag") == 1,
            resultSet.getInt("editable_flag") == 1,
            resultSet.getObject("updated_at", LocalDateTime.class),
            nullableLong(resultSet, "updated_by"),
            resultSet.getLong("version"));
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
