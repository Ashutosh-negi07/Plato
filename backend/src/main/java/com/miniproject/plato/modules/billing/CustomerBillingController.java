package com.miniproject.plato.modules.billing;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.modules.billing.dto.BillSummaryResponse;
import com.miniproject.plato.modules.billing.dto.PaymentResponse;
import com.miniproject.plato.modules.billing.dto.RequestBillRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/billing")
@RequiredArgsConstructor
public class CustomerBillingController {

    private final BillingService billingService;

    /**
     * Customer views their itemized bill summary (aggregated across all non-cancelled orders).
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BillSummaryResponse>> getBillSummary(
            @RequestHeader("X-Session-Token") String sessionToken) {

        BillSummaryResponse response = billingService.getBillSummary(sessionToken);
        return ResponseEntity.ok(ApiResponse.ok("Bill summary retrieved", response));
    }

    /**
     * Customer requests the bill with their selected payment method (CASH, CARD, UPI).
     */
    @PostMapping("/request-bill")
    public ResponseEntity<ApiResponse<PaymentResponse>> requestBill(
            @RequestHeader("X-Session-Token") String sessionToken,
            @Valid @RequestBody RequestBillRequest request) {

        PaymentResponse response = billingService.requestBill(sessionToken, request);
        return ResponseEntity.ok(ApiResponse.ok("Bill requested successfully", response));
    }

    /**
     * Customer checks their payment status for this dining session.
     */
    @GetMapping("/payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> getSessionPayment(
            @RequestHeader("X-Session-Token") String sessionToken) {

        PaymentResponse response = billingService.getSessionPayment(sessionToken);
        return ResponseEntity.ok(ApiResponse.ok("Payment status retrieved", response));
    }
}
