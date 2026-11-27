package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.dto.OrderItemResponse;
import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.OrderItem;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final OrderCalculationService calculationService;
    private final RestaurantOrderRepository orderRepository;

    @Transactional
    public Bill createBill(RestaurantOrder order) {
        Bill calculated = calculationService.calculateBill(order);
        order.setTotalAmount(calculated.getTotalAmount());

        String sessionKey = order.getOrderSessionKey();
        if (sessionKey != null) {
            Optional<Bill> existing = billRepository
                    .findByOrderSessionKeyAndSupersededByBillIdIsNull(sessionKey);
            if (existing.isPresent()) {
                Bill sittingBill = existing.get();
                addAmounts(sittingBill, calculated);
                sittingBill.setBillVersion(sittingBill.getBillVersion() + 1);
                return billRepository.save(sittingBill);
            }
        }

        calculated.setOrder(order);
        calculated.setOrderSessionKey(sessionKey);
        calculated.setBillNumber(UUID.randomUUID().toString());
        Bill saved = billRepository.saveAndFlush(calculated);
        saved.setBillNumber("INV/" + saved.getId());
        return billRepository.save(saved);
    }

    @Transactional
    public void refreshBill(RestaurantOrder order) {
        Bill bill = findCanonicalBill(order)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        List<RestaurantOrder> sittingOrders = findSittingOrders(bill).stream()
                .filter(this::isBillable)
                .toList();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal serviceChargeAmount = BigDecimal.ZERO;
        BigDecimal rewardDiscountAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (RestaurantOrder sittingOrder : sittingOrders) {
            Bill calculated = calculationService.calculateBill(sittingOrder);
            sittingOrder.setTotalAmount(calculated.getTotalAmount());
            orderRepository.save(sittingOrder);
            subtotal = subtotal.add(calculated.getSubtotal());
            taxAmount = taxAmount.add(calculated.getTaxAmount());
            serviceChargeAmount = serviceChargeAmount.add(calculated.getServiceChargeAmount());
            rewardDiscountAmount = rewardDiscountAmount.add(calculated.getRewardDiscountAmount());
            totalAmount = totalAmount.add(calculated.getTotalAmount());
        }
        bill.setSubtotal(subtotal);
        bill.setTaxAmount(taxAmount);
        bill.setServiceChargeAmount(serviceChargeAmount);
        bill.setRewardDiscountAmount(rewardDiscountAmount);
        bill.setTotalAmount(totalAmount);
        bill.setBillVersion(bill.getBillVersion() + 1);
        billRepository.save(bill);
    }

    @Transactional(readOnly = true)
    public BillResponse getBillByOrderNumber(String orderNumber) {
        RestaurantOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        return getBillForOrder(order);
    }

    @Transactional(readOnly = true)
    public String getBillNumberForOrderNumber(String orderNumber) {
        RestaurantOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        return findCanonicalBill(order)
                .map(Bill::getBillNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
    }

    @Transactional(readOnly = true)
    public BillResponse getBillForOrder(RestaurantOrder order) {
        Bill bill = findCanonicalBill(order)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        return mapToResponse(bill);
    }

    @Transactional(readOnly = true)
    public List<BillResponse> getBillsForOrders(List<RestaurantOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        Map<String, RestaurantOrder> groupRepresentatives = new LinkedHashMap<>();
        for (RestaurantOrder order : orders) {
            groupRepresentatives.putIfAbsent(groupKey(order), order);
        }

        List<String> sessionKeys = groupRepresentatives.values().stream()
                .map(RestaurantOrder::getOrderSessionKey)
                .filter(key -> key != null)
                .distinct()
                .toList();
        Map<String, Bill> sessionBills = new LinkedHashMap<>();
        if (!sessionKeys.isEmpty()) {
            for (Bill bill : billRepository
                    .findAllByOrderSessionKeyInAndSupersededByBillIdIsNull(sessionKeys)) {
                sessionBills.put(bill.getOrderSessionKey(), bill);
            }
        }

        List<Long> standaloneOrderIds = groupRepresentatives.values().stream()
                .filter(order -> order.getOrderSessionKey() == null)
                .map(RestaurantOrder::getId)
                .toList();
        Map<Long, Bill> standaloneBills = new LinkedHashMap<>();
        if (!standaloneOrderIds.isEmpty()) {
            for (Bill bill : billRepository.findAllWithOrderDetailsByOrder_IdIn(standaloneOrderIds)) {
                if (bill.getSupersededByBillId() == null) {
                    standaloneBills.put(bill.getOrder().getId(), bill);
                }
            }
        }

        Map<String, List<RestaurantOrder>> sessionOrders = loadSessionOrders(
                groupRepresentatives.values());

        List<BillResponse> responses = new ArrayList<>(groupRepresentatives.size());
        for (Map.Entry<String, RestaurantOrder> entry : groupRepresentatives.entrySet()) {
            RestaurantOrder representative = entry.getValue();
            Bill bill = representative.getOrderSessionKey() == null
                    ? standaloneBills.get(representative.getId())
                    : sessionBills.get(representative.getOrderSessionKey());
            if (bill == null) {
                throw new ResourceNotFoundException("Bill not found");
            }
            List<RestaurantOrder> sittingOrders = representative.getOrderSessionKey() == null
                    ? List.of(representative)
                    : sessionOrders.getOrDefault(
                            representative.getOrderSessionKey(), List.of(representative));
            responses.add(mapToResponse(bill, sittingOrders));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public BillResponse getBillForOwner(Long restaurantId, Long orderId) {
        RestaurantOrder order = orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        if (order.getApprovalStatus() == OrderApprovalStatus.PENDING
                || order.getApprovalStatus() == OrderApprovalStatus.REJECTED) {
            throw new ResourceNotFoundException("Bill not found");
        }
        return getBillForOrder(order);
    }

    @Transactional(readOnly = true)
    public BillResponse mapToResponse(Bill bill) {
        return mapToResponse(bill, findSittingOrders(bill));
    }

    private BillResponse mapToResponse(Bill bill, Collection<RestaurantOrder> orders) {
        List<RestaurantOrder> sittingOrders = orders.stream()
                .filter(this::isBillable)
                .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .toList();
        if (sittingOrders.isEmpty()) {
            sittingOrders = List.of(bill.getOrder());
        }

        RestaurantOrder latestOrder = sittingOrders.get(sittingOrders.size() - 1);
        List<OrderItemResponse> items = sittingOrders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(this::mapItem)
                .toList();
        List<Long> orderIds = sittingOrders.stream().map(RestaurantOrder::getId).toList();
        List<String> orderNumbers = sittingOrders.stream()
                .map(RestaurantOrder::getOrderNumber)
                .toList();

        BillResponse response = new BillResponse();
        response.setBillNumber(bill.getBillNumber());
        response.setBillVersion(bill.getBillVersion());
        response.setOrderId(latestOrder.getId());
        response.setOrderNumber(latestOrder.getOrderNumber());
        response.setOrderIds(orderIds);
        response.setOrderNumbers(orderNumbers);
        response.setBillNumbers(List.of(bill.getBillNumber()));
        response.setRestaurantName(latestOrder.getRestaurant().getRestaurantName());
        response.setTableNumber(latestOrder.getTable().getTableNumber());
        response.setPaymentStatus(aggregatePaymentStatus(sittingOrders));
        response.setOrderStatuses(sittingOrders.stream().map(RestaurantOrder::getOrderStatus).toList());
        response.setCustomerName(latestOrder.getCustomerName());
        response.setCustomerMobile(latestOrder.getCustomerMobile());
        response.setItems(items);
        response.setSubtotal(bill.getSubtotal());
        response.setTaxMode(bill.getTaxMode());
        response.setTaxRate(bill.getTaxRate());
        response.setTaxAmount(bill.getTaxAmount());
        response.setServiceChargeEnabled(bill.isServiceChargeEnabled());
        response.setServiceChargeRate(bill.getServiceChargeRate());
        response.setServiceChargeAmount(bill.getServiceChargeAmount());
        response.setRewardDiscountAmount(bill.getRewardDiscountAmount());
        response.setTotalAmount(bill.getTotalAmount());
        response.setBillDate(bill.getCreatedAt());
        return response;
    }

    private Map<String, List<RestaurantOrder>> loadSessionOrders(
            Collection<RestaurantOrder> representatives) {
        Map<Long, List<String>> keysByRestaurant = new LinkedHashMap<>();
        for (RestaurantOrder order : representatives) {
            if (order.getOrderSessionKey() != null) {
                keysByRestaurant.computeIfAbsent(
                        order.getRestaurant().getId(), ignored -> new ArrayList<>())
                        .add(order.getOrderSessionKey());
            }
        }
        Map<String, List<RestaurantOrder>> result = new LinkedHashMap<>();
        keysByRestaurant.forEach((restaurantId, keys) ->
                orderRepository.findAllByRestaurant_IdAndOrderSessionKeyInOrderByCreatedAtAsc(
                                restaurantId, keys)
                        .forEach(order -> result.computeIfAbsent(
                                order.getOrderSessionKey(), ignored -> new ArrayList<>())
                                .add(order)));
        return result;
    }

    private List<RestaurantOrder> findSittingOrders(Bill bill) {
        if (bill.getOrderSessionKey() == null) {
            return List.of(bill.getOrder());
        }
        return orderRepository.findAllByRestaurant_IdAndTable_IdAndOrderSessionKeyOrderByCreatedAtAsc(
                bill.getOrder().getRestaurant().getId(),
                bill.getOrder().getTable().getId(),
                bill.getOrderSessionKey());
    }

    private Optional<Bill> findCanonicalBill(RestaurantOrder order) {
        if (order.getOrderSessionKey() != null) {
            return billRepository.findByOrderSessionKeyAndSupersededByBillIdIsNull(
                    order.getOrderSessionKey());
        }
        return billRepository.findByOrderIdAndOrderRestaurantId(
                order.getId(), order.getRestaurant().getId());
    }

    private String groupKey(RestaurantOrder order) {
        return order.getOrderSessionKey() == null
                ? "order:" + order.getId()
                : "session:" + order.getOrderSessionKey();
    }

    private boolean isBillable(RestaurantOrder order) {
        return order.getOrderStatus() != OrderStatus.CANCELLED
                && order.getApprovalStatus() != OrderApprovalStatus.REJECTED;
    }

    private PaymentStatus aggregatePaymentStatus(List<RestaurantOrder> orders) {
        if (orders.stream().allMatch(order -> order.getPaymentStatus() == PaymentStatus.PAID)) {
            return PaymentStatus.PAID;
        }
        if (orders.stream().anyMatch(order -> order.getPaymentStatus() == PaymentStatus.FAILED)) {
            return PaymentStatus.FAILED;
        }
        if (orders.stream().allMatch(order -> order.getPaymentStatus() == PaymentStatus.REFUNDED)) {
            return PaymentStatus.REFUNDED;
        }
        return PaymentStatus.PENDING;
    }

    private void addAmounts(Bill target, Bill addition) {
        target.setSubtotal(add(target.getSubtotal(), addition.getSubtotal()));
        target.setTaxAmount(add(target.getTaxAmount(), addition.getTaxAmount()));
        target.setServiceChargeAmount(
                add(target.getServiceChargeAmount(), addition.getServiceChargeAmount()));
        target.setRewardDiscountAmount(
                add(target.getRewardDiscountAmount(), addition.getRewardDiscountAmount()));
        target.setTotalAmount(add(target.getTotalAmount(), addition.getTotalAmount()));
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        return (left == null ? BigDecimal.ZERO : left)
                .add(right == null ? BigDecimal.ZERO : right);
    }

    private OrderItemResponse mapItem(OrderItem item) {
        OrderItemResponse response = new OrderItemResponse();
        response.setItemId(item.getMenuItem() != null ? item.getMenuItem().getId() : null);
        response.setItemName(item.getItemName());
        response.setCustomization(item.getCustomization());
        response.setUnitPrice(item.getUnitPrice());
        response.setQuantity(item.getQuantity());
        response.setLineTotal(item.getLineTotal());
        return response;
    }
}
