package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
public class CustomerBillService {
    private static final Duration SITTING_GAP = Duration.ofHours(2);

    private final RestaurantTableRepository tableRepository;
    private final RestaurantOrderRepository orderRepository;
    private final CustomerSessionService customerSessionService;
    private final BillService billService;
    private final RestaurantPhonePeConfigService phonePeConfigService;

    @Transactional(readOnly = true)
    public BillResponse getBillForOwner(Long restaurantId, Long orderId) {
        RestaurantOrder target = orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        if (target.getApprovalStatus() == com.texos.texos_backend.entity.OrderApprovalStatus.PENDING
                || target.getApprovalStatus() == com.texos.texos_backend.entity.OrderApprovalStatus.REJECTED) {
            throw new ResourceNotFoundException("Bill not found");
        }
        List<RestaurantOrder> tableOrders = target.getOrderSessionKey() != null
                ? orderRepository.findAllByRestaurant_IdAndTable_IdAndOrderSessionKeyOrderByCreatedAtAsc(
                        restaurantId, target.getTable().getId(), target.getOrderSessionKey())
                : orderRepository.findAllByRestaurant_IdAndTable_IdOrderByCreatedAtDesc(
                        restaurantId, target.getTable().getId()).stream()
                        .filter(order -> java.util.Objects.equals(
                                order.getCustomer() == null ? null : order.getCustomer().getId(),
                                target.getCustomer() == null ? null : target.getCustomer().getId()))
                        .filter(order -> java.util.Objects.equals(order.getCustomerName(), target.getCustomerName())
                                && java.util.Objects.equals(order.getCustomerMobile(), target.getCustomerMobile()))
                        .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt))
                        .toList();
        tableOrders = tableOrders.stream()
                .filter(order -> order.getOrderStatus() != OrderStatus.CANCELLED)
                .filter(order -> order.getApprovalStatus() != com.texos.texos_backend.entity.OrderApprovalStatus.PENDING
                        && order.getApprovalStatus() != com.texos.texos_backend.entity.OrderApprovalStatus.REJECTED)
                .toList();

        int targetIndex = -1;
        for (int i = 0; i < tableOrders.size(); i++) {
            if (tableOrders.get(i).getId().equals(target.getId())) { targetIndex = i; break; }
        }
        if (targetIndex < 0) throw new ResourceNotFoundException("Bill not found");

        int start = targetIndex;
        while (start > 0) {
            RestaurantOrder previous = tableOrders.get(start - 1);
            RestaurantOrder current = tableOrders.get(start);
            if ((target.getOrderSessionKey() == null && previous.getPaymentStatus() == PaymentStatus.PAID)
                    || (target.getOrderSessionKey() == null
                    && Duration.between(previous.getCreatedAt(), current.getCreatedAt()).compareTo(SITTING_GAP) >= 0)) break;
            start--;
        }
        int end = targetIndex;
        while (end + 1 < tableOrders.size()) {
            RestaurantOrder current = tableOrders.get(end);
            RestaurantOrder next = tableOrders.get(end + 1);
            if ((target.getOrderSessionKey() == null && current.getPaymentStatus() == PaymentStatus.PAID)
                    || (target.getOrderSessionKey() == null
                    && Duration.between(current.getCreatedAt(), next.getCreatedAt()).compareTo(SITTING_GAP) >= 0)) break;
            end++;
        }
        List<RestaurantOrder> sitting = tableOrders.subList(start, end + 1);
        List<BillResponse> bills = billService.getBillsForOrders(sitting);
        RestaurantOrder latestOrder = sitting.get(sitting.size() - 1);
        return combineBills(bills, latestOrder, latestOrder.getCreatedAt().plus(SITTING_GAP));
    }

    @Transactional(readOnly = true)
    public BillResponse getCurrentBill(String qrToken, HttpServletRequest request) {
        RestaurantTable table = tableRepository.findByQrTokenAndActiveTrue(qrToken)
                .filter(value -> value.getRestaurant().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid QR code or inactive table"));

        Long restaurantId = table.getRestaurant().getId();
        List<RestaurantOrder> accessibleOrders = new ArrayList<>();
        String orderSessionKey = customerSessionService.getOrderSessionKey(request, restaurantId, table.getId());
        accessibleOrders.addAll(orderRepository
                .findAllByRestaurant_IdAndTable_IdAndOrderSessionKeyOrderByCreatedAtAsc(
                        restaurantId, table.getId(), orderSessionKey));
        if (accessibleOrders.isEmpty()) {
            CustomerSession customerSession = customerSessionService.getActiveSession(request, restaurantId);
            if (customerSession != null) {
                accessibleOrders.addAll(orderRepository
                        .findAllByCustomer_IdAndRestaurant_IdAndTable_IdOrderByCreatedAtDesc(
                                customerSession.getCustomerId(), restaurantId, table.getId()));
            } else {
                Set<String> orderNumbers = customerSessionService.getOrderNumbers(request, restaurantId);
                if (!orderNumbers.isEmpty()) {
                    orderRepository.findAllByOrderNumberInAndRestaurant_IdOrderByCreatedAtDesc(
                                    orderNumbers, restaurantId)
                            .stream()
                            .filter(order -> order.getCustomer() == null
                                    && order.getTable().getId().equals(table.getId()))
                            .forEach(accessibleOrders::add);
                }
            }
        }

        List<RestaurantOrder> recent = accessibleOrders.stream()
                .filter(order -> order.getOrderStatus() != OrderStatus.CANCELLED)
                .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt).reversed())
                .toList();
        if (recent.isEmpty()) {
            throw new ResourceNotFoundException("No recent bill found for this table");
        }

        List<RestaurantOrder> sitting = new ArrayList<>();
        RestaurantOrder latestOrder = recent.get(0);
        String latestSessionKey = latestOrder.getOrderSessionKey();
        RestaurantOrder previous = null;
        for (RestaurantOrder order : recent) {
            if (previous != null) {
                if (latestSessionKey != null
                        && !Objects.equals(latestSessionKey, order.getOrderSessionKey())) break;
                if (latestSessionKey == null
                        && (previous.getPaymentStatus() == PaymentStatus.PAID
                        || order.getPaymentStatus() == PaymentStatus.PAID)) break;
            }
            sitting.add(order);
            previous = order;
        }

        boolean paid = sitting.stream().allMatch(order -> order.getPaymentStatus() == PaymentStatus.PAID);
        LocalDateTime expiresAt = paid ? latestOrder.getCreatedAt().plus(SITTING_GAP) : null;
        if (expiresAt != null && !LocalDateTime.now().isBefore(expiresAt)) {
            throw new ResourceNotFoundException("This bill has expired. Place a new order to start another bill.");
        }

        List<RestaurantOrder> chronologicalSitting = sitting.stream()
                .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .toList();
        List<BillResponse> bills = billService.getBillsForOrders(chronologicalSitting);
        BillResponse response = combineBills(bills, latestOrder, expiresAt);
        response.setOnlinePaymentAvailable(phonePeConfigService.isOnlinePaymentsEnabled(restaurantId));
        return response;
    }

    private BillResponse combineBills(
            List<BillResponse> bills,
            RestaurantOrder latestOrder,
            LocalDateTime expiresAt) {
        BillResponse firstBill = bills.get(0);
        BillResponse result = new BillResponse();
        result.setBillNumber(firstBill.getBillNumber());
        result.setBillVersion(firstBill.getBillVersion());
        result.setRestaurantName(firstBill.getRestaurantName());
        result.setTableNumber(firstBill.getTableNumber());
        result.setTaxMode(firstBill.getTaxMode());
        result.setTaxRate(firstBill.getTaxRate());
        result.setServiceChargeEnabled(firstBill.isServiceChargeEnabled());
        result.setServiceChargeRate(firstBill.getServiceChargeRate());

        List<com.texos.texos_backend.dto.OrderItemResponse> items = new ArrayList<>();
        List<Long> orderIds = new ArrayList<>();
        List<String> orderNumbers = new ArrayList<>();
        List<String> billNumbers = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal serviceChargeAmount = BigDecimal.ZERO;
        BigDecimal rewardDiscountAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean allPaid = true;
        boolean allFinalized = true;
        boolean anyFailed = false;
        boolean allRefunded = true;
        List<com.texos.texos_backend.entity.OrderStatus> orderStatuses = new ArrayList<>();
        for (BillResponse bill : bills) {
            if (bill.getItems() != null) items.addAll(bill.getItems());
            if (bill.getOrderIds() != null) orderIds.addAll(bill.getOrderIds());
            else if (bill.getOrderId() != null) orderIds.add(bill.getOrderId());
            if (bill.getOrderNumbers() != null) orderNumbers.addAll(bill.getOrderNumbers());
            else if (bill.getOrderNumber() != null) orderNumbers.add(bill.getOrderNumber());
            if (bill.getBillNumber() != null && !billNumbers.contains(bill.getBillNumber())) {
                billNumbers.add(bill.getBillNumber());
            }
            subtotal = add(subtotal, bill.getSubtotal());
            taxAmount = add(taxAmount, bill.getTaxAmount());
            serviceChargeAmount = add(serviceChargeAmount, bill.getServiceChargeAmount());
            rewardDiscountAmount = add(rewardDiscountAmount, bill.getRewardDiscountAmount());
            totalAmount = add(totalAmount, bill.getTotalAmount());
            allPaid &= bill.getPaymentStatus() == PaymentStatus.PAID;
            allFinalized &= bill.getOrderStatuses() != null
                    && bill.getOrderStatuses().stream().allMatch(status -> status == OrderStatus.SERVED);
            if (bill.getOrderStatuses() != null) orderStatuses.addAll(bill.getOrderStatuses());
            anyFailed |= bill.getPaymentStatus() == PaymentStatus.FAILED;
            allRefunded &= bill.getPaymentStatus() == PaymentStatus.REFUNDED;
        }
        result.setItems(items);
        result.setOrderIds(orderIds);
        result.setOrderNumbers(orderNumbers);
        result.setBillNumbers(billNumbers);
        result.setSubtotal(subtotal);
        result.setTaxAmount(taxAmount);
        result.setServiceChargeAmount(serviceChargeAmount);
        result.setRewardDiscountAmount(rewardDiscountAmount);
        result.setTotalAmount(totalAmount);
        result.setPaymentStatus(allPaid ? PaymentStatus.PAID
                : anyFailed ? PaymentStatus.FAILED
                : allRefunded ? PaymentStatus.REFUNDED : PaymentStatus.PENDING);
        result.setOrderStatuses(orderStatuses);
        result.setCustomerName(latestOrder.getCustomerName());
        result.setCustomerMobile(latestOrder.getCustomerMobile());
        result.setEligibleForOnlinePayment(!allPaid
                && !anyFailed
                && !allRefunded
                && allFinalized
                && totalAmount.compareTo(BigDecimal.ONE) >= 0);
        result.setBillDate(latestOrder.getCreatedAt());
        result.setExpiresAt(expiresAt == null ? null : expiresAt.atZone(ZoneId.systemDefault()).toInstant());
        result.setPaymentQrImage(latestOrder.getRestaurant().getPaymentQrImage());
        result.setUpiId(latestOrder.getRestaurant().getUpiId());
        result.setOrderId(latestOrder.getId());
        result.setOrderNumber(latestOrder.getOrderNumber());
        return result;
    }

    private BigDecimal add(BigDecimal total, BigDecimal value) {
        return total.add(value == null ? BigDecimal.ZERO : value);
    }
}
