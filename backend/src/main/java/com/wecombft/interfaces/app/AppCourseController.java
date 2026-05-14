package com.wecombft.interfaces.app;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.course.CourseApplicationService;
import com.wecombft.application.course.CourseApplicationService.AppCoursePage;
import com.wecombft.application.course.CourseApplicationService.CourseDetailResponse;
import com.wecombft.application.course.CourseApplicationService.CourseSpecsResponse;
import com.wecombft.application.course.CourseApplicationService.EntitlementLessonsResponse;
import com.wecombft.application.course.CourseApplicationService.EntitlementPage;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class AppCourseController {

    private final CourseApplicationService courseApplicationService;

    public AppCourseController(CourseApplicationService courseApplicationService) {
        this.courseApplicationService = courseApplicationService;
    }

    @GetMapping("/api/app/courses")
    public ResponseEntity<ApiResponse<AppCoursePage>> courses(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "category_code", required = false) String categoryCode,
        @RequestParam(value = "course_type", required = false) String courseType,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            courseApplicationService.appCourses(keyword, categoryCode, courseType, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/courses/{course_id}")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> course(@PathVariable("course_id") long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(
            courseApplicationService.appCourseDetail(courseId),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/courses/{course_id}/specs")
    public ResponseEntity<ApiResponse<CourseSpecsResponse>> specs(@PathVariable("course_id") long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(
            courseApplicationService.appCourseSpecs(courseId),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/learning/entitlements")
    public ResponseEntity<ApiResponse<EntitlementPage>> entitlements(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            courseApplicationService.appEntitlements(authorization, status, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/learning/entitlements/{entitlement_id}/lessons")
    public ResponseEntity<ApiResponse<EntitlementLessonsResponse>> lessons(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("entitlement_id") long entitlementId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            courseApplicationService.appEntitlementLessons(authorization, entitlementId),
            TraceIds.currentOrCreate()));
    }
}
