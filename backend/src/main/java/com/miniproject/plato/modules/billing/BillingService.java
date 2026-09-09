package com.miniproject.plato.modules.billing;

import com.miniproject.plato.modules.billing.dto.BillSummaryResponse;
import com.miniproject.plato.modules.billing.dto.CompletePaymentRequest;
import com.miniproject.plato.modules.billing.dto.PaymentResponse;
import com.miniproject.plato.modules.billing.dto.RequestBillRequest;

import java.util.List;
import java.util.UUID;

public interface BillingService {

    // ── Customer Operations (Session Token Auth) ──────────────────────────────
    BillSummaryResponse getBillSummary(String sessionToken);

    PaymentResponse requestBill(String sessionToken, RequestBillRequest request);

    PaymentResponse getSessionPayment(String sessionToken);

    // ── Staff Operations (JWT Auth) ───────────────────────────────────────────
    List<PaymentResponse> getRestaurantPayments(UUID restaurantId, PaymentStatus statusFilter, UUID callerId, String callerRole);

    PaymentResponse getPaymentById(UUID paymentId, UUID callerId, String callerRole);

    PaymentResponse completePayment(UUID paymentId, CompletePaymentRequest request, UUID callerId, String callerRole);
}
