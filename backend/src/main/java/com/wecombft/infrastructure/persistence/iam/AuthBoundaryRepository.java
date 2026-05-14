package com.wecombft.infrastructure.persistence.iam;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthBoundaryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthBoundaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AppStudentRecord> findActiveStudentByUserNo(String userNo) {
        return jdbcTemplate.query(
                """
                select u.user_no, s.student_no, u.mobile
                from sys_user u
                join edu_student s on s.user_id = u.id
                where u.user_no = ?
                  and u.user_type = 'STUDENT'
                  and u.status = 'ACTIVE'
                  and u.deleted_flag = 0
                  and s.status = 'ACTIVE'
                  and s.deleted_flag = 0
                """,
                (resultSet, rowNum) -> new AppStudentRecord(
                    resultSet.getString("user_no"),
                    resultSet.getString("student_no"),
                    resultSet.getString("mobile")),
                userNo)
            .stream()
            .findFirst();
    }

    public Optional<SupplierAuthRecord> findActiveSupplierByNo(String supplierNo) {
        return jdbcTemplate.query(
                """
                select supplier_no, supplier_name, access_status, status
                from supplier
                where supplier_no = ?
                  and access_status = 'ENABLED'
                  and status = 'ACTIVE'
                  and deleted_flag = 0
                """,
                (resultSet, rowNum) -> new SupplierAuthRecord(
                    resultSet.getString("supplier_no"),
                    resultSet.getString("supplier_name"),
                    resultSet.getString("access_status"),
                    resultSet.getString("status")),
                supplierNo)
            .stream()
            .findFirst();
    }

    public record AppStudentRecord(String userNo, String studentNo, String mobile) {
    }

    public record SupplierAuthRecord(String supplierNo, String supplierName, String accessStatus, String status) {
    }
}
