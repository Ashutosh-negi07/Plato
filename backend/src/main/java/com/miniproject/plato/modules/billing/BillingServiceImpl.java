package com.miniproject.plato.modules.billing;

import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.exception.ValidationException;
import com.miniproject.plato.modules.billing.dto.BillSummaryResponse;
import com.miniproject.plato.modules.billing.dto.CompletePaymentRequest;
import com.miniproject.plato.modules.billing.dto.PaymentResponse;
import com.miniproject.plato.modules.billing.dto.RequestBillRequest;
import com.miniproject.plato.modules.employee.EmployeeRepository;
import com.miniproject.plato.modules.order.OrderService;
import com.miniproject.plato.modules.order.OrderStatus;
import com.miniproject.plato.modules.order.dto.OrderResponse;
import com.miniproject.plato.modules.restaurant.Restaurant;
import com.miniproject.plato.modules.restaurant.RestaurantRepository;
import com.miniproject.plato.modules.session.CustomerSession;
import com.miniproject.plato.modules.session.CustomerSessionService;
import com.miniproject.plato.modules.table.RestaurantTable;
import com.miniproject.plato.modules.table.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BillingServiceImpl implements BillingService {

    private final PaymentRepository paymentRepository;
    private final CustomerSessionService sessionService;
    private final OrderService orderService;
    private final RestaurantRepository restaurantRepository;
    private final TableRepository tableRepository;
    private final EmployeeRepository employeeRepository;
    private final BillingMapper billingMapper;

    // ── Helper methods ────────────────────────────────────────────────────────

    private void verifyStaffAccess(UUID restaurantId, UUID callerId, String callerRole) {
        if ("SUPER_ADMIN".equals(callerRole)) {
            return;
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

        if ("OWNER".equals(callerRole) && restaurant.getOwnerId().equals(callerId)) {
            return;
        }

        if ("EMPLOYEE".equals(callerRole) && employeeRepository.existsByUserIdAndRestaurantId(callerId, restaurantId)) {
            return;
        }

        throw new UnauthorizedAccessException("You do not have permission to access billing for this restaurant");
    }

    private void validatePaymentMethodSupport(Restaurant restaurant, PaymentMethod method) {
        switch (method) {
            case CASH -> {
                if (Boolean.FALSE.equals(restaurant.getAllowCashPayment())) {
                    throw new ValidationException("This restaurant does not accept cash payments");
                }
            }
            case CARD -> {
                if (Boolean.FALSE.equals(restaurant.getAllowCardPayment())) {
                    throw new ValidationException("This restaurant does not accept card payments");
                }
            }
            case UPI -> {
                if (Boolean.FALSE.equals(restaurant.getAllowUpi())) {
                    throw new ValidationException("This restaurant does not accept UPI payments");
                }
            }
            case ONLINE -> {
                if (Boolean.FALSE.equals(restaurant.getAllowOnlinePayment())) {
                    throw new ValidationException("This restaurant does not accept online gateway payments");
                }
            }
        }
    }

    // ── Customer Operations ───────────────────────────────────────────────────

    @Override
    public BillSummaryResponse getBillSummary(String sessionToken) {
        CustomerSession session = sessionService.validateAndRefreshSession(sessionToken);

        Restaurant restaurant = restaurantRepository.findById(session.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", session.getRestaurantId()));

        RestaurantTable table = tableRepository.findById(session.getTableId()).orElse(null);
        String tableNumber = (table != null) ? table.getTableNumber() : null;

        List<OrderResponse> allOrders = orderService.getSessionOrders(sessionToken);
        List<OrderResponse> activeOrders = allOrders.stream()
                .filter(o -> o.status() != OrderStatus.CANCELLED)
                .toList();

        BigDecimal ordersSubtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;

        for (OrderResponse order : activeOrders) {
            ordersSubtotal = ordersSubtotal.add(order.subtotal());
            taxTotal = taxTotal.add(order.tax());
            discountTotal = discountTotal.add(order.discount() != null ? order.discount() : BigDecimal.ZERO);
        }

        BigDecimal serviceChargePercentage = restaurant.getServiceCharge() != null
                ? restaurant.getServiceCharge()
                : BigDecimal.ZERO;

        BigDecimal serviceChargeAmount = ordersSubtotal.multiply(serviceChargePercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal grandTotal = ordersSubtotal.add(taxTotal).add(serviceChargeAmount)
                .subtract(discountTotal)
                .max(BigDecimal.ZERO);

        boolean hasPendingOrders = activeOrders.stream()
                .anyMatch(o -> o.status() == OrderStatus.PENDING || o.status() == OrderStatus.PREPARING);

        return billingMapper.toBillSummaryResponse(
                session.getId(),
                restaurant.getId(),
                restaurant.getName(),
                session.getTableId(),
                tableNumber,
                activeOrders,
                ordersSubtotal,
                taxTotal,
                serviceChargePercentage,
                serviceChargeAmount,
                discountTotal,
                grandTotal,
                hasPendingOrders
        );
    }

    @Override
    @Transactional
    public PaymentResponse requestBill(String sessionToken, RequestBillRequest request) {
        CustomerSession session = sessionService.validateAndRefreshSession(sessionToken);

        Restaurant restaurant = restaurantRepository.findById(session.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", session.getRestaurantId()));

        validatePaymentMethodSupport(restaurant, request.paymentMethod());

        BillSummaryResponse billSummary = getBillSummary(sessionToken);
        if (billSummary.orders().isEmpty()) {
            throw new ValidationException("Cannot request bill for a session with no active orders");
        }

        // Check if payment already exists for this session
        Optional<Payment> existingPaymentOpt = paymentRepository.findBySessionId(session.getId());
        Payment payment;

        if (existingPaymentOpt.isPresent()) {
            payment = existingPaymentOpt.get();
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                throw new ValidationException("Payment for this session has already been completed");
            }
            payment.setSubtotal(billSummary.ordersSubtotal());
            payment.setTax(billSummary.taxTotal());
            payment.setServiceCharge(billSummary.serviceChargeAmount());
            payment.setDiscount(billSummary.discount());
            payment.setAmount(billSummary.grandTotal());
            payment.setPaymentMethod(request.paymentMethod());
            payment.setStatus(PaymentStatus.PENDING);
        } else {
            payment = Payment.builder()
                    .sessionId(session.getId())
                    .restaurantId(restaurant.getId())
                    .subtotal(billSummary.ordersSubtotal())
                    .tax(billSummary.taxTotal())
                    .serviceCharge(billSummary.serviceChargeAmount())
                    .discount(billSummary.discount())
                    .amount(billSummary.grandTotal())
                    .paymentMethod(request.paymentMethod())
                    .status(PaymentStatus.PENDING)
                    .build();
        }

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Bill requested for session {}: method={}, amount={}",
                session.getId(), savedPayment.getPaymentMethod(), savedPayment.getAmount());

        return billingMapper.toPaymentResponse(savedPayment, billSummary.tableNumber());
    }

    @Override
    public PaymentResponse getSessionPayment(String sessionToken) {
        CustomerSession session = sessionService.validateAndRefreshSession(sessionToken);

        Payment payment = paymentRepository.findBySessionId(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment has not been requested for this session yet"));

        RestaurantTable table = tableRepository.findById(session.getTableId()).orElse(null);
        String tableNumber = (table != null) ? table.getTableNumber() : null;

        return billingMapper.toPaymentResponse(payment, tableNumber);
    }

    // ── Staff Operations ──────────────────────────────────────────────────────

    @Override
    public List<PaymentResponse> getRestaurantPayments(UUID restaurantId, PaymentStatus statusFilter, UUID callerId, String callerRole) {
        verifyStaffAccess(restaurantId, callerId, callerRole);

        List<Payment> payments = (statusFilter != null)
                ? paymentRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, statusFilter)
                : paymentRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);

        return payments.stream()
                .map(payment -> {
                    // Resolve table number from session
                    return billingMapper.toPaymentResponse(payment, null);
                })
                .toList();
    }

    @Override
    public PaymentResponse getPaymentById(UUID paymentId, UUID callerId, String callerRole) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        verifyStaffAccess(payment.getRestaurantId(), callerId, callerRole);
        return billingMapper.toPaymentResponse(payment, null);
    }

    @Override
    @Transactional
    public PaymentResponse completePayment(UUID paymentId, CompletePaymentRequest request, UUID callerId, String callerRole) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        verifyStaffAccess(payment.getRestaurantId(), callerId, callerRole);

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            throw new ValidationException("This payment has already been completed");
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        if (request != null && request.transactionReference() != null && !request.transactionReference().isBlank()) {
            payment.setTransactionReference(request.transactionReference());
        }

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment {} completed for amount {}", savedPayment.getId(), savedPayment.getAmount());

        // Automatically close the dining session and release the table to AVAILABLE
        sessionService.closeSession(payment.getSessionId(), callerId, callerRole);
        log.info("Session {} auto-closed and table released following payment completion", payment.getSessionId());

        return billingMapper.toPaymentResponse(savedPayment, null);
    }
}
