package com.miniproject.plato.modules.billing;

import com.miniproject.plato.modules.billing.dto.BillSummaryResponse;
import com.miniproject.plato.modules.billing.dto.PaymentResponse;
import com.miniproject.plato.modules.order.dto.OrderResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class BillingMapper {

    public PaymentResponse toPaymentResponse(Payment payment, String tableNumber) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .sessionId(payment.getSessionId())
                .restaurantId(payment.getRestaurantId())
                .tableNumber(tableNumber)
                .subtotal(payment.getSubtotal())
                .tax(payment.getTax())
                .serviceCharge(payment.getServiceCharge())
                .discount(payment.getDiscount())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .transactionReference(payment.getTransactionReference())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    public BillSummaryResponse toBillSummaryResponse(
            UUID sessionId,
            UUID restaurantId,
            String restaurantName,
            UUID tableId,
            String tableNumber,
            List<OrderResponse> orders,
            BigDecimal ordersSubtotal,
            BigDecimal taxTotal,
            BigDecimal serviceChargePercentage,
            BigDecimal serviceChargeAmount,
            BigDecimal discount,
            BigDecimal grandTotal,
            boolean hasPendingOrders) {

        return BillSummaryResponse.builder()
                .sessionId(sessionId)
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .tableId(tableId)
                .tableNumber(tableNumber)
                .orders(orders)
                .ordersSubtotal(ordersSubtotal)
                .taxTotal(taxTotal)
                .serviceChargePercentage(serviceChargePercentage)
                .serviceChargeAmount(serviceChargeAmount)
                .discount(discount)
                .grandTotal(grandTotal)
                .hasPendingOrders(hasPendingOrders)
                .build();
    }
}
