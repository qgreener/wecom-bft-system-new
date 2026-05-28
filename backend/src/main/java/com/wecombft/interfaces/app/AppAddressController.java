package com.wecombft.interfaces.app;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.student.AppAddressApplicationService;
import com.wecombft.application.student.AppAddressApplicationService.AddressCommand;
import com.wecombft.application.student.AppAddressApplicationService.AddressView;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/app/addresses")
public class AppAddressController {

    private final AppAddressApplicationService service;

    public AppAddressController(AppAddressApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressView>>> list(
        @RequestHeader("Authorization") String authorization
    ) {
        return ResponseEntity.ok(ApiResponse.ok(service.listMine(authorization), TraceIds.currentOrCreate()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressView>> create(
        @RequestHeader("Authorization") String authorization,
        @RequestBody AddressRequest body
    ) {
        AddressView view = service.create(authorization, body == null ? null : body.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(view, TraceIds.currentOrCreate()));
    }

    @PutMapping("/{address_id}")
    public ResponseEntity<ApiResponse<AddressView>> update(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("address_id") long addressId,
        @RequestBody AddressRequest body
    ) {
        AddressView view = service.update(authorization, addressId, body == null ? null : body.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(view, TraceIds.currentOrCreate()));
    }

    @DeleteMapping("/{address_id}")
    public ResponseEntity<ApiResponse<Void>> delete(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("address_id") long addressId
    ) {
        service.delete(authorization, addressId);
        return ResponseEntity.ok(ApiResponse.ok(null, TraceIds.currentOrCreate()));
    }

    public record AddressRequest(
        String receiverName,
        String receiverMobile,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode,
        Boolean isDefault
    ) {
        public AddressCommand toCommand() {
            return new AddressCommand(
                receiverName, receiverMobile, province, city, district,
                detailAddress, postalCode, isDefault != null && isDefault);
        }
    }
}
