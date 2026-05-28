package com.wecombft.application.student;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class AppAddressApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AppStudentApplicationService appStudentApplicationService;

    public AppAddressApplicationService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        AppStudentApplicationService appStudentApplicationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.appStudentApplicationService = appStudentApplicationService;
    }

    public List<AddressView> listMine(String authorizationHeader) {
        long studentId = appStudentApplicationService.requireStudent(authorizationHeader).studentId();
        return jdbcTemplate.query(
            """
            select id, student_id, receiver_name, receiver_mobile, province, city, district,
                   detail_address, postal_code, is_default, status, created_at, updated_at
            from student_address
            where student_id = ? and deleted_flag = 0
            order by is_default desc, updated_at desc, id desc
            """,
            this::mapAddress,
            studentId);
    }

    @Transactional
    public AddressView create(String authorizationHeader, AddressCommand cmd) {
        long studentId = appStudentApplicationService.requireStudent(authorizationHeader).studentId();
        validate(cmd);
        long id = idGenerator.nextId();
        if (cmd.isDefault()) {
            jdbcTemplate.update("update student_address set is_default = 0 where student_id = ? and deleted_flag = 0", studentId);
        }
        jdbcTemplate.update(
            """
            insert into student_address (
                id, student_id, receiver_name, receiver_mobile, province, city, district,
                detail_address, postal_code, is_default, status, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
            id,
            studentId,
            cmd.receiverName(),
            cmd.receiverMobile(),
            cmd.province(),
            cmd.city(),
            cmd.district(),
            cmd.detailAddress(),
            cmd.postalCode(),
            cmd.isDefault() ? 1 : 0,
            studentId,
            studentId);
        return findById(id).orElseThrow();
    }

    @Transactional
    public AddressView update(String authorizationHeader, long addressId, AddressCommand cmd) {
        long studentId = appStudentApplicationService.requireStudent(authorizationHeader).studentId();
        ensureOwned(studentId, addressId);
        validate(cmd);
        if (cmd.isDefault()) {
            jdbcTemplate.update("update student_address set is_default = 0 where student_id = ? and deleted_flag = 0", studentId);
        }
        int rows = jdbcTemplate.update(
            """
            update student_address
            set receiver_name = ?, receiver_mobile = ?, province = ?, city = ?, district = ?,
                detail_address = ?, postal_code = ?, is_default = ?, updated_by = ?, updated_at = ?,
                version = version + 1
            where id = ? and student_id = ? and deleted_flag = 0
            """,
            cmd.receiverName(),
            cmd.receiverMobile(),
            cmd.province(),
            cmd.city(),
            cmd.district(),
            cmd.detailAddress(),
            cmd.postalCode(),
            cmd.isDefault() ? 1 : 0,
            studentId,
            LocalDateTime.now(),
            addressId,
            studentId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "地址不存在或无权访问");
        }
        return findById(addressId).orElseThrow();
    }

    @Transactional
    public void delete(String authorizationHeader, long addressId) {
        long studentId = appStudentApplicationService.requireStudent(authorizationHeader).studentId();
        ensureOwned(studentId, addressId);
        int rows = jdbcTemplate.update(
            """
            update student_address
            set deleted_flag = 1, deleted_at = ?, deleted_by = ?, updated_at = ?, version = version + 1
            where id = ? and student_id = ? and deleted_flag = 0
            """,
            LocalDateTime.now(),
            studentId,
            LocalDateTime.now(),
            addressId,
            studentId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "地址不存在或无权访问");
        }
    }

    private void validate(AddressCommand cmd) {
        if (cmd.receiverName() == null || cmd.receiverName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "收货人不能为空");
        }
        if (cmd.receiverMobile() == null || !cmd.receiverMobile().matches("1[3-9]\\d{9}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "手机号格式错误");
        }
        if (cmd.province() == null || cmd.city() == null || cmd.district() == null
            || cmd.detailAddress() == null || cmd.detailAddress().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "省市区与详细地址必填");
        }
    }

    private void ensureOwned(long studentId, long addressId) {
        Integer cnt = jdbcTemplate.queryForObject(
            "select count(*) from student_address where id = ? and student_id = ? and deleted_flag = 0",
            Integer.class, addressId, studentId);
        if (cnt == null || cnt == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "地址不存在或无权访问");
        }
    }

    private Optional<AddressView> findById(long addressId) {
        List<AddressView> list = jdbcTemplate.query(
            """
            select id, student_id, receiver_name, receiver_mobile, province, city, district,
                   detail_address, postal_code, is_default, status, created_at, updated_at
            from student_address
            where id = ? and deleted_flag = 0
            """,
            this::mapAddress, addressId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private AddressView mapAddress(ResultSet rs, int rowNum) throws SQLException {
        return new AddressView(
            rs.getLong("id"),
            rs.getLong("student_id"),
            rs.getString("receiver_name"),
            rs.getString("receiver_mobile"),
            rs.getString("province"),
            rs.getString("city"),
            rs.getString("district"),
            rs.getString("detail_address"),
            rs.getString("postal_code"),
            rs.getInt("is_default") == 1,
            rs.getString("status"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class));
    }

    public record AddressCommand(
        String receiverName,
        String receiverMobile,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode,
        boolean isDefault
    ) {
    }

    public record AddressView(
        long id,
        long studentId,
        String receiverName,
        String receiverMobile,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode,
        boolean isDefault,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
    }
}
