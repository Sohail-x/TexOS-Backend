package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.dto.OrderItemResponse;

import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.OrderItem;
import com.texos.texos_backend.entity.RestaurantOrder;

import com.texos.texos_backend.exception.ResourceNotFoundException;

import com.texos.texos_backend.repository.BillRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final OrderCalculationService calculationService;

    @Transactional
    public Bill createBill(RestaurantOrder order) {

        Bill bill = calculationService.calculateBill(order);
        bill.setBillNumber(UUID.randomUUID().toString());

        // Store the calculated total on the order too.
        order.setTotalAmount(bill.getTotalAmount());

        Bill saved = billRepository.saveAndFlush(bill);
        saved.setBillNumber("INV/" + saved.getId());
        return billRepository.save(saved);
    }

    @Transactional
    public void refreshBill(RestaurantOrder order) {
        Bill bill = billRepository.findByOrderOrderNumber(order.getOrderNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        Bill calculated = calculationService.calculateBill(order);
        bill.setBillVersion(bill.getBillVersion() + 1);
        bill.setSubtotal(calculated.getSubtotal());
        bill.setTaxMode(calculated.getTaxMode());
        bill.setTaxRate(calculated.getTaxRate());
        bill.setTaxAmount(calculated.getTaxAmount());
        bill.setServiceChargeEnabled(calculated.isServiceChargeEnabled());
        bill.setServiceChargeRate(calculated.getServiceChargeRate());
        bill.setServiceChargeAmount(calculated.getServiceChargeAmount());
        bill.setRewardDiscountAmount(calculated.getRewardDiscountAmount());
        bill.setTotalAmount(calculated.getTotalAmount());
        order.setTotalAmount(calculated.getTotalAmount());
        billRepository.save(bill);
    }

    @Transactional(readOnly = true)
    public BillResponse getBillByOrderNumber(String orderNumber) {

        Bill bill = billRepository
                .findByOrderOrderNumber(orderNumber)
                .orElseThrow(() ->
                    new ResourceNotFoundException("Bill not found")
                );
        return mapToResponse(bill);
    }

    @Transactional(readOnly = true)
    public BillResponse getBillForOwner(
            Long restaurantId,
            Long orderId
    ) {

        Bill bill = billRepository
                .findByOrderIdAndOrderRestaurantId(
                    orderId,
                    restaurantId
                )
                .orElseThrow(() ->
                    new ResourceNotFoundException("Bill not found")
                );
        if (bill.getOrder().getApprovalStatus() == OrderApprovalStatus.PENDING
                || bill.getOrder().getApprovalStatus() == OrderApprovalStatus.REJECTED) {
            throw new ResourceNotFoundException("Bill not found");
        }

        return mapToResponse(bill);
    }

    public BillResponse mapToResponse(Bill bill) {

        RestaurantOrder order = bill.getOrder();

        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(this::mapItem)
                .toList();

        BillResponse response = new BillResponse();

        response.setBillNumber(bill.getBillNumber());
        response.setBillVersion(bill.getBillVersion());

        response.setOrderId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setOrderIds(List.of(order.getId()));
        response.setOrderNumbers(List.of(order.getOrderNumber()));
        response.setBillNumbers(List.of(bill.getBillNumber()));
        response.setRestaurantName(
                order.getRestaurant().getRestaurantName()
        );
        response.setTableNumber(order.getTable().getTableNumber());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setOrderStatuses(List.of(order.getOrderStatus()));
        response.setCustomerName(order.getCustomerName());
        response.setCustomerMobile(order.getCustomerMobile());

        response.setItems(items);

        response.setSubtotal(bill.getSubtotal());

        response.setTaxMode(bill.getTaxMode());
        response.setTaxRate(bill.getTaxRate());
        response.setTaxAmount(bill.getTaxAmount());

        response.setServiceChargeEnabled(
                bill.isServiceChargeEnabled()
        );

        response.setServiceChargeRate(
                bill.getServiceChargeRate()
        );

        response.setServiceChargeAmount(
                bill.getServiceChargeAmount()
        );

        response.setRewardDiscountAmount(bill.getRewardDiscountAmount());
        response.setTotalAmount(bill.getTotalAmount());
        response.setBillDate(bill.getCreatedAt());

        return response;
    }

    private OrderItemResponse mapItem(OrderItem item) {

        OrderItemResponse response = new OrderItemResponse();

        response.setItemId(
                item.getMenuItem() != null
                        ? item.getMenuItem().getId()
                        : null
        );

        response.setItemName(item.getItemName());
        response.setCustomization(item.getCustomization());
        response.setUnitPrice(item.getUnitPrice());
        response.setQuantity(item.getQuantity());
        response.setLineTotal(item.getLineTotal());

        return response;
    }
}