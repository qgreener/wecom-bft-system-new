package com.wecombft.application.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.learning.LearningEntitlementService.EntitlementEventResult;
import com.wecombft.application.learning.LearningEntitlementService.PaymentSuccessEntitlementCommand;
import com.wecombft.application.learning.LearningEntitlementService.RefundEntitlementCommand;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningEntitlementServiceTest {

    @Autowired
    private LearningEntitlementService learningEntitlementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_open_freeze_and_revoke_entitlement_idempotently() {
        PaymentSuccessEntitlementCommand command = new PaymentSuccessEntitlementCommand(
            100000000302L,
            100000000009L,
            200000000001L,
            "ORD_S4_ENT",
            200000000002L,
            100000000451L,
            100000000452L,
            "{\"course_title\":\"S4 权益课程\",\"spec_name\":\"标准班\"}",
            LocalDateTime.now()
        );

        EntitlementEventResult firstOpen = learningEntitlementService.openForPaidOrder(command);
        EntitlementEventResult secondOpen = learningEntitlementService.openForPaidOrder(command);

        assertThat(secondOpen.entitlementId()).isEqualTo(firstOpen.entitlementId());
        assertThat(secondOpen.idempotentHit()).isTrue();
        assertThat(countEntitlements(command.studentId(), command.orderId(), command.courseId())).isEqualTo(1);

        RefundEntitlementCommand freezeCommand = new RefundEntitlementCommand(
            command.studentId(),
            command.orderId(),
            command.courseId(),
            200000000101L,
            "FREEZE",
            LocalDateTime.now()
        );
        EntitlementEventResult firstFreeze = learningEntitlementService.applyRefundEffect(freezeCommand);
        EntitlementEventResult secondFreeze = learningEntitlementService.applyRefundEffect(freezeCommand);

        assertThat(firstFreeze.status()).isEqualTo("FROZEN");
        assertThat(secondFreeze.idempotentHit()).isTrue();

        RefundEntitlementCommand revokeCommand = new RefundEntitlementCommand(
            command.studentId(),
            command.orderId(),
            command.courseId(),
            200000000101L,
            "REVOKE",
            LocalDateTime.now()
        );
        EntitlementEventResult firstRevoke = learningEntitlementService.applyRefundEffect(revokeCommand);
        EntitlementEventResult secondRevoke = learningEntitlementService.applyRefundEffect(revokeCommand);

        assertThat(firstRevoke.status()).isEqualTo("REVOKED");
        assertThat(secondRevoke.idempotentHit()).isTrue();
        assertThat(countEntitlements(command.studentId(), command.orderId(), command.courseId())).isEqualTo(1);

        String finalStatus = jdbcTemplate.queryForObject(
                """
                select status from learning_entitlement
                where student_id = ? and order_id = ? and course_id = ?
                """,
                String.class,
                command.studentId(),
                command.orderId(),
                command.courseId());
        Integer stopped = jdbcTemplate.queryForObject(
                """
                select remind_stopped from learning_entitlement
                where student_id = ? and order_id = ? and course_id = ?
                """,
                Integer.class,
                command.studentId(),
                command.orderId(),
                command.courseId());
        assertThat(finalStatus).isEqualTo("REVOKED");
        assertThat(stopped).isEqualTo(1);
    }

    private int countEntitlements(long studentId, long orderId, long courseId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*) from learning_entitlement
                where student_id = ? and order_id = ? and course_id = ?
                """,
                Integer.class,
                studentId,
                orderId,
                courseId);
        return count == null ? 0 : count;
    }
}
