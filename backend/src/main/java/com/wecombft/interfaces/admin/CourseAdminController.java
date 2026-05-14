package com.wecombft.interfaces.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.course.CourseApplicationService;
import com.wecombft.application.course.CourseApplicationService.CourseApprovalActionCommand;
import com.wecombft.application.course.CourseApplicationService.CourseApprovalActionResponse;
import com.wecombft.application.course.CourseApplicationService.CourseApprovalCommand;
import com.wecombft.application.course.CourseApplicationService.CourseApprovalResponse;
import com.wecombft.application.course.CourseApplicationService.CourseDetailResponse;
import com.wecombft.application.course.CourseApplicationService.CoursePage;
import com.wecombft.application.course.CourseApplicationService.CourseSaveCommand;
import com.wecombft.application.course.CourseApplicationService.CourseSaveResponse;
import com.wecombft.application.course.CourseApplicationService.CourseSpecCommand;
import com.wecombft.application.course.CourseApplicationService.CourseSpecResponse;
import com.wecombft.application.course.CourseApplicationService.LessonNodeCommand;
import com.wecombft.application.course.CourseApplicationService.LessonNodeResponse;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class CourseAdminController {

    private final CourseApplicationService courseApplicationService;

    public CourseAdminController(CourseApplicationService courseApplicationService) {
        this.courseApplicationService = courseApplicationService;
    }

    @GetMapping("/api/admin/courses")
    @RequireAnyPermission({"course:spec:write", "course:lesson:read", "course:lesson:write"})
    public ResponseEntity<ApiResponse<CoursePage>> courses(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "course_type", required = false) String courseType,
        @RequestParam(value = "teacher_user_id", required = false) Long teacherUserId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            courseApplicationService.adminCourses(keyword, courseType, teacherUserId, status, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/courses/{course_id}")
    @RequireAnyPermission({"course:spec:write", "course:lesson:read", "course:lesson:write"})
    public ResponseEntity<ApiResponse<CourseDetailResponse>> course(@PathVariable("course_id") long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(
            courseApplicationService.adminCourseDetail(courseId),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/courses")
    @RequirePermission("course:spec:write")
    public ResponseEntity<ApiResponse<CourseSaveResponse>> saveCourse(@RequestBody CourseSaveCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(courseApplicationService.saveCourse(command), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/courses/{course_id}/specs")
    @RequirePermission("course:spec:write")
    public ResponseEntity<ApiResponse<CourseSpecResponse>> saveSpec(
        @PathVariable("course_id") long courseId,
        @RequestBody CourseSpecCommand command
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(courseApplicationService.saveSpec(courseId, command), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/courses/{course_id}/lesson-nodes")
    @RequireAnyPermission({"course:spec:write", "course:lesson:write"})
    public ResponseEntity<ApiResponse<LessonNodeResponse>> saveLessonNode(
        @PathVariable("course_id") long courseId,
        @RequestBody LessonNodeCommand command
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(courseApplicationService.saveLessonNode(courseId, command), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/courses/{course_id}/approval")
    @RequirePermission("course:spec:write")
    public ResponseEntity<ApiResponse<CourseApprovalResponse>> submitApproval(
        @PathVariable("course_id") long courseId,
        @RequestBody CourseApprovalCommand command
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(courseApplicationService.submitApproval(courseId, command), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/collab/course-approvals/{approval_id}/actions")
    @RequirePermission("course:approval:approve")
    public ResponseEntity<ApiResponse<CourseApprovalActionResponse>> approvalAction(
        @PathVariable("approval_id") long approvalId,
        @RequestBody CourseApprovalActionCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            courseApplicationService.approvalAction(approvalId, command),
            TraceIds.currentOrCreate()));
    }
}
