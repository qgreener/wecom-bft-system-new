package com.wecombft.infrastructure.persistence.file;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FileAssetRepository {

    private static final String FIELDS = """
        id, file_no, file_name, file_type, storage_key, access_url, file_digest,
        file_size, biz_type, biz_id, order_id, status, uploaded_by, uploaded_at,
        created_at, updated_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public FileAssetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<FileAssetRecord> findByDigestBiz(String fileDigest, String bizType, long bizId) {
        return jdbcTemplate.query(
                "select " + FIELDS + " from sys_file_asset where file_digest = ? and biz_type = ? and biz_id = ?",
                rowMapper(),
                fileDigest, bizType, bizId)
            .stream()
            .findFirst();
    }

    public Optional<FileAssetRecord> findByFileNo(String fileNo) {
        return jdbcTemplate.query(
                "select " + FIELDS + " from sys_file_asset where file_no = ?",
                rowMapper(),
                fileNo)
            .stream()
            .findFirst();
    }

    public FileAssetRecord insert(FileAssetRecord record) {
        jdbcTemplate.update(
            """
            insert into sys_file_asset (
                id, file_no, file_name, file_type, storage_key, access_url, file_digest,
                file_size, biz_type, biz_id, order_id, status, uploaded_by, uploaded_at,
                created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            record.id(), record.fileNo(), record.fileName(), record.fileType(),
            record.storageKey(), record.accessUrl(), record.fileDigest(), record.fileSize(),
            record.bizType(), record.bizId(), record.orderId(), record.status(),
            record.uploadedBy(), record.uploadedAt(),
            record.uploadedBy(), record.uploadedBy());
        return findByFileNo(record.fileNo()).orElseThrow();
    }

    private RowMapper<FileAssetRecord> rowMapper() {
        return (rs, rowNum) -> new FileAssetRecord(
            rs.getLong("id"),
            rs.getString("file_no"),
            rs.getString("file_name"),
            rs.getString("file_type"),
            rs.getString("storage_key"),
            rs.getString("access_url"),
            rs.getString("file_digest"),
            rs.getLong("file_size"),
            rs.getString("biz_type"),
            rs.getLong("biz_id"),
            nullableLong(rs, "order_id"),
            rs.getString("status"),
            nullableLong(rs, "uploaded_by"),
            toLocalDateTime(rs, "uploaded_at"),
            toLocalDateTime(rs, "created_at"),
            toLocalDateTime(rs, "updated_at"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
