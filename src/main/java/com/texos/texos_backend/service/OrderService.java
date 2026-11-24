package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.*;
import com.texos.texos_backend.entity.*;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.exception.ResourceConflictException;
import com.texos.texos_backend.repository.*;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final RestaurantTableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantOrderRepository orderRepository;
    private final BillService billService;
    private final BillRepository billRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CustomerRepository customerRepository;
    private final CustomerSessionService customerSessionService;
    private final LoyaltyService loyaltyService;
    private final OrderCalculationService orderCalculationService;
    private final ApplicationEventPublisher eventPublisher;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request,HttpServletRequest servletRequest) {
        RestaurantTable table = tableRepository
                .findByQrTokenAndActiveTrue(request.getQrToken())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid QR code or inactive table"));

        Restaurant restaurant = restaurantRepository.findByIdForUpdate(table.getRestaurant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        if (!restaurant.isActive()) {
            throw new ResourceConflictException("Restaurant is currently unavailable");
        }

        // Check whether the customer is logged in for this restaurant.
        CustomerSession customerSession = customerSessionService.getActiveSession(servletRequest, restaurant.getId());

        Customer customer = null;

        if (customerSession != null) {
            customer = customerRepository
                .findByIdAndRestaurant_IdForUpdate(
                    customerSession.getCustomerId(),
                    restaurant.getId()
            )
            .orElse(null);
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidRequestException("Order must contain at least one item");
        }

        Set<Long> itemIds = new HashSet<>();
        RestaurantOrder order = new RestaurantOrder();
        order.setRestaurant(restaurant);
        order.setTable(table);

        String orderSessionKey = customerSessionService.getOrderSessionKey(
                servletRequest, restaurant.getId(), table.getId());
        boolean sittingHasPaidOrder = orderRepository
                .existsByRestaurant_IdAndTable_IdAndOrderSessionKeyAndPaymentStatus(
                        restaurant.getId(), table.getId(), orderSessionKey, PaymentStatus.PAID);
        if (sittingHasPaidOrder) {
            orderSessionKey = customerSessionService.rotateOrderSessionKey(
                    servletRequest, restaurant.getId(), table.getId());
        }
        order.setOrderSessionKey(orderSessionKey);

        // Link the order only to a customer with a valid login session.
order.setCustomer(customer);

if (customer != null) {

    // Use trusted account details for logged-in customers.
    order.setCustomerName(customer.getCustomerName());
    order.setCustomerMobile(customer.getMobileNumber());
    order.setCustomerEmail(customer.getEmail());

} else {

    // Guest checkout: save contact details only with this order.
   order.setCustomerName(
        request.getCustomerName() == null
                || request.getCustomerName().isBlank()
                ? null
                : request.getCustomerName().trim()
);

    order.setCustomerMobile(
            request.getCustomerMobile() == null
                    || request.getCustomerMobile().isBlank()
                    ? null
                    : request.getCustomerMobile().trim()
    );

    order.setCustomerEmail(
            request.getCustomerEmail() == null
                    || request.getCustomerEmail().isBlank()
                    ? null
                    : request.getCustomerEmail().trim()
    );
}

order.setOrderStatus(OrderStatus.NEW);
order.setApprovalStatus(restaurant.isOrderApprovalEnabled()
        ? OrderApprovalStatus.PENDING : OrderApprovalStatus.NOT_REQUIRED);
order.setPaymentStatus(PaymentStatus.PENDING);

        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest requestedItem : request.getItems()) {
            if (!itemIds.add(requestedItem.getItemId())) {
                throw new InvalidRequestException("Duplicate item found in order");
            }
            if (requestedItem.getQuantity() == null
                    || requestedItem.getQuantity() < 1
                    || requestedItem.getQuantity() > 99) {
                throw new InvalidRequestException("Item quantity must be between 1 and 99");
            }

            MenuItem menuItem = menuItemRepository.findById(requestedItem.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Menu item not found: " + requestedItem.getItemId()));

            if (!menuItem.isAvailable()) {
                throw new ResourceConflictException(menuItem.getItemName() + " is currently unavailable");
            }

            MenuCategory category = menuItem.getCategory();
            if (!category.isActive()) {
                throw new ResourceConflictException("Menu category is inactive: " + category.getCategoryName());
            }
            if (!category.getRestaurant().getId().equals(restaurant.getId())) {
                throw new InvalidRequestException("Menu item does not belong to this restaurant");
            }

            BigDecimal unitPrice = menuItem.getPrice();
            BigDecimal lineTotal = unitPrice
                    .multiply(BigDecimal.valueOf(requestedItem.getQuantity()))
                    .setScale(2, java.math.RoundingMode.HALF_UP);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setMenuItem(menuItem);
            orderItem.setItemName(menuItem.getItemName());
            orderItem.setCustomization(requestedItem.getCustomization() == null
                    || requestedItem.getCustomization().isBlank()
                    ? null : requestedItem.getCustomization().trim());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(requestedItem.getQuantity());
            orderItem.setLineTotal(lineTotal);
            orderItems.add(orderItem);
        }

        order.setItems(orderItems);
        BigDecimal grossTotal = orderCalculationService.calculateBill(order).getTotalAmount();
        order.setTotalAmount(grossTotal);
        RestaurantOrder savedOrder = orderRepository.save(order);
        savedOrder.setOrderNumber("TX - #" + String.format("%07d", savedOrder.getId()));
        savedOrder = orderRepository.save(savedOrder);
        if (request.getRewardId() != null) {
            if (customer == null) {
                throw new InvalidRequestException("Please log in before using loyalty points.");
            }
            BigDecimal eligibleSubtotal = savedOrder.getItems().stream()
                    .map(OrderItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            loyaltyService.applyRewardToOrder(
                    request.getRewardId(), customer, restaurant, savedOrder, eligibleSubtotal);
            savedOrder = orderRepository.save(savedOrder);
        } else if (request.getRewardPoints() != null) {
            if (customer == null) {
                throw new InvalidRequestException("Please log in before using loyalty points.");
            }
            BigDecimal eligibleSubtotal = savedOrder.getItems().stream()
                    .map(OrderItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            loyaltyService.applyPointConversionToOrder(
                    request.getRewardPoints(), customer, restaurant, savedOrder, eligibleSubtotal);
            savedOrder = orderRepository.save(savedOrder);
        }
        billService.createBill(savedOrder);
        customerSessionService.registerOrderAccess(
                servletRequest,
                restaurant.getId(),
                savedOrder.getOrderNumber()
        );

        OrderResponse response = mapToResponse(savedOrder);
        if (savedOrder.getApprovalStatus() != OrderApprovalStatus.PENDING) {
            messagingTemplate.convertAndSend(
                    "/topic/restaurants/" + savedOrder.getRestaurant().getId() + "/orders",
                    response
            );
            eventPublisher.publishEvent(
                    RestaurantLiveUpdateEvent.ordersChanged(savedOrder.getRestaurant().getId()));
        } else {
            eventPublisher.publishEvent(new OrderApprovalPendingEvent(
                    savedOrder.getRestaurant().getId(),
                    savedOrder.getId(),
                    savedOrder.getTable().getTableNumber(),
                    savedOrder.getTotalAmount()));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(
            String qrToken,
            HttpServletRequest servletRequest) {

        RestaurantTable table = tableRepository
                .findByQrTokenAndActiveTrue(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invalid QR code or inactive table"));

        Restaurant restaurant = table.getRestaurant();
        if (!restaurant.isActive()) {
            throw new ResourceNotFoundException(
                    "Restaurant is currently unavailable");
        }

        Long restaurantId = restaurant.getId();
        Map<String, RestaurantOrder> accessibleOrders = new LinkedHashMap<>();

        Set<String> orderNumbers =
                customerSessionService.getOrderNumbers(
                        servletRequest, restaurantId);
        if (!orderNumbers.isEmpty()) {
            orderRepository
                    .findAllByOrderNumberInAndRestaurant_IdOrderByCreatedAtDesc(
                            orderNumbers, restaurantId)
                    .forEach(order -> accessibleOrders.put(
                            order.getOrderNumber(), order));
        }

        CustomerSession customerSession =
                customerSessionService.getActiveSession(
                        servletRequest, restaurantId);
        if (customerSession != null) {
            orderRepository
                    .findAllByCustomer_IdAndRestaurant_IdOrderByCreatedAtDesc(
                            customerSession.getCustomerId(), restaurantId)
                    .forEach(order -> accessibleOrders.put(
                            order.getOrderNumber(), order));
        }

        return accessibleOrders.values().stream()
                .sorted((first, second) ->
                        second.getCreatedAt().compareTo(first.getCreatedAt()))
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getCustomerOrder(
            String orderNumber,
            HttpServletRequest servletRequest) {

        RestaurantOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        Long restaurantId = order.getRestaurant().getId();
        boolean hasGuestAccess = customerSessionService
                .getOrderNumbers(servletRequest, restaurantId)
                .contains(orderNumber);
        CustomerSession customerSession =
                customerSessionService.getActiveSession(
                        servletRequest, restaurantId);
        boolean hasAccountAccess = customerSession != null
                && order.getCustomer() != null
                && order.getCustomer().getId().equals(
                        customerSession.getCustomerId());

        if (!hasGuestAccess && !hasAccountAccess) {
            throw new ResourceNotFoundException("Order not found");
        }

        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForOwner(Long restaurantId) {
        return orderRepository.findAllByRestaurant_IdAndApprovalStatusNotInOrderByCreatedAtDesc(
                        restaurantId, List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED))
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderForOwner(Long restaurantId, Long orderId) {
        RestaurantOrder order = orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        ensureOwnerVisible(order);
        return mapToResponse(order);
    }

    
@Transactional
public OrderResponse updateOrderStatus(
        Long restaurantId,
        Long orderId,
        OrderStatus newStatus
) {
    RestaurantOrder order = orderRepository
            .findByIdAndRestaurantId(orderId, restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    ensureOwnerVisible(order);

    if (order.getOrderStatus() == OrderStatus.CANCELLED
            || order.getOrderStatus() == OrderStatus.SERVED) {
        throw new ResourceConflictException(
                "This order can no longer be updated"
        );
    }

    if (newStatus == null) {
        throw new InvalidRequestException(
                "Order status is required"
        );
    }

    boolean validTransition = newStatus == OrderStatus.CANCELLED
            || switch (order.getOrderStatus()) {
                case NEW -> newStatus == OrderStatus.ACCEPTED;
                case ACCEPTED -> newStatus == OrderStatus.PREPARING;
                case PREPARING -> newStatus == OrderStatus.READY;
                case READY -> newStatus == OrderStatus.SERVED;
                case SERVED, CANCELLED -> false;
            };
    if (!validTransition) {
        throw new InvalidRequestException(
                "Order status must follow the restaurant order flow"
        );
    }

    order.setOrderStatus(newStatus);

    RestaurantOrder savedOrder = orderRepository.save(order);
    loyaltyService.processOrderLifecycle(savedOrder);
    OrderResponse response = mapToResponse(savedOrder);


    // Notify restaurant dashboard.
    messagingTemplate.convertAndSend(
            "/topic/restaurants/" + restaurantId + "/orders",
            response
    );


    // Notify the customer tracking this specific order.
    messagingTemplate.convertAndSend(
            "/topic/orders/" + savedOrder.getOrderNumber(),
            response
    );
    eventPublisher.publishEvent(RestaurantLiveUpdateEvent.ordersChanged(restaurantId));

    return response;
}

@Transactional
public OrderResponse updatePaymentStatus(
        Long restaurantId,
        Long orderId,
        PaymentStatus newStatus
) {
    RestaurantOrder target = orderRepository
            .findByIdAndRestaurantIdForUpdate(orderId, restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    ensureOwnerVisible(target);
    if (newStatus == PaymentStatus.PAID) {
        throw new InvalidRequestException(
                "Record a verified online payment or use the offline collection endpoint to mark a bill paid.");
    }
    if (newStatus == target.getPaymentStatus()) {
        return mapToResponse(target);
    }
    if ((newStatus == PaymentStatus.FAILED && target.getPaymentStatus() == PaymentStatus.PENDING)
            || (newStatus == PaymentStatus.REFUNDED && target.getPaymentStatus() == PaymentStatus.PAID)) {
        List<RestaurantOrder> paymentGroup = target.getOrderSessionKey() != null
                ? orderRepository.findAllByRestaurant_IdAndTable_IdAndOrderSessionKeyOrderByCreatedAtAsc(
                        restaurantId, target.getTable().getId(), target.getOrderSessionKey())
                : List.of(target);
        OrderResponse updatedTarget = null;
        for (RestaurantOrder sittingOrder : paymentGroup) {
            if (newStatus == PaymentStatus.FAILED && sittingOrder.getPaymentStatus() != PaymentStatus.PENDING) continue;
            if (newStatus == PaymentStatus.REFUNDED && sittingOrder.getPaymentStatus() != PaymentStatus.PAID) continue;
            sittingOrder.setPaymentStatus(newStatus);
            RestaurantOrder saved = orderRepository.save(sittingOrder);
            loyaltyService.processOrderLifecycle(saved);
            OrderResponse updated = mapToResponse(saved);
            if (saved.getId().equals(target.getId())) updatedTarget = updated;
            messagingTemplate.convertAndSend("/topic/restaurants/" + restaurantId + "/orders", updated);
            messagingTemplate.convertAndSend("/topic/orders/" + saved.getOrderNumber(), updated);
        }
        if (updatedTarget != null) {
            eventPublisher.publishEvent(RestaurantLiveUpdateEvent.ordersChanged(restaurantId));
            return updatedTarget;
        }
        throw new ResourceConflictException("No eligible orders were found in this sitting for the requested payment update.");
    }
    throw new InvalidRequestException(
            "Only pending payments can be marked failed, and only paid orders can be refunded.");
}


        public OrderResponse toResponse(RestaurantOrder order) {
        return mapToResponse(order);
    }

    private OrderResponse mapToResponse(RestaurantOrder order) {
        return mapToResponse(order, billService.getBillByOrderNumber(order.getOrderNumber()));
    }

    private OrderResponse mapToResponse(RestaurantOrder order, BillResponse billResponse) {
        List<OrderItemResponse> itemResponses = order.getItems().stream().map(item -> {
            OrderItemResponse response = new OrderItemResponse();
            response.setItemId(item.getMenuItem() != null ? item.getMenuItem().getId() : null);
            response.setItemName(item.getItemName());
            response.setCustomization(item.getCustomization());
            response.setUnitPrice(item.getUnitPrice());
            response.setQuantity(item.getQuantity());
            response.setLineTotal(item.getLineTotal());
            return response;
        }).toList();

        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setRestaurantName(order.getRestaurant().getRestaurantName());
        response.setTableNumber(order.getTable().getTableNumber());
        response.setCustomerName(order.getCustomerName());
        response.setOrderStatus(order.getOrderStatus());
        response.setApprovalStatus(order.getApprovalStatus());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setItems(itemResponses);
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());
        response.setBill(billResponse);
        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getRecentOrdersForOwner(Long restaurantId) {
        long serviceStartedAtNanos = DashboardRequestMeasurements.startService();
        try {
            List<RestaurantOrder> visibleOrders = orderRepository
                    .findTop10ByRestaurant_IdOrderByCreatedAtDesc(restaurantId)
                    .stream()
                    .filter(order -> order.getApprovalStatus() != OrderApprovalStatus.PENDING
                            && order.getApprovalStatus() != OrderApprovalStatus.REJECTED)
                    .toList();
            if (visibleOrders.isEmpty()) {
                DashboardRequestMeasurements.recordResponseOrderCount(0);
                return List.of();
            }

            List<Long> orderIds = visibleOrders.stream()
                    .map(RestaurantOrder::getId)
                    .toList();
            orderRepository.findAllWithItemsRestaurantAndTableByIdIn(orderIds);

            Map<Long, Bill> billsByOrderId = new LinkedHashMap<>();
            for (Bill bill : billRepository.findAllByOrder_IdIn(orderIds)) {
                billsByOrderId.put(bill.getOrder().getId(), bill);
            }

            List<OrderResponse> responses = new ArrayList<>(visibleOrders.size());
            for (RestaurantOrder order : visibleOrders) {
                Bill bill = billsByOrderId.get(order.getId());
                if (bill == null) {
                    throw new ResourceNotFoundException("Bill not found");
                }
                responses.add(mapToResponse(order, billService.mapToResponse(bill)));
            }
            DashboardRequestMeasurements.recordResponseOrderCount(responses.size());
            return responses;
        } finally {
            DashboardRequestMeasurements.recordServiceDuration(serviceStartedAtNanos);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getActiveOrdersForOwner(Long restaurantId) {
        long serviceStartedAtNanos = DashboardRequestMeasurements.startService();
        try {
            List<OrderStatus> activeStatuses = List.of(
                    OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY
            );
            Map<Long, RestaurantOrder> visibleOrdersById = new LinkedHashMap<>();
            orderRepository
                    .findAllByRestaurant_IdAndOrderStatusInOrderByCreatedAtDesc(
                            restaurantId, activeStatuses)
                    .stream()
                    .filter(order -> order.getApprovalStatus() != OrderApprovalStatus.PENDING
                            && order.getApprovalStatus() != OrderApprovalStatus.REJECTED)
                    .forEach(order -> visibleOrdersById.putIfAbsent(order.getId(), order));

            if (visibleOrdersById.isEmpty()) {
                DashboardRequestMeasurements.recordResponseOrderCount(0);
                return List.of();
            }

            List<Long> orderIds = new ArrayList<>(visibleOrdersById.keySet());
            Map<Long, BillResponse> billResponsesByOrderId = new LinkedHashMap<>();
            for (Bill bill : billRepository.findAllByOrder_IdIn(orderIds)) {
                billResponsesByOrderId.put(
                        bill.getOrder().getId(),
                        billService.mapToResponse(bill));
            }

            List<OrderResponse> responses = new ArrayList<>(visibleOrdersById.size());
            for (RestaurantOrder order : visibleOrdersById.values()) {
                BillResponse billResponse = billResponsesByOrderId.get(order.getId());
                if (billResponse == null) {
                    throw new ResourceNotFoundException("Bill not found");
                }
                responses.add(mapToResponse(order, billResponse));
            }
            DashboardRequestMeasurements.recordResponseOrderCount(responses.size());
            return responses;
        } finally {
            DashboardRequestMeasurements.recordServiceDuration(serviceStartedAtNanos);
        }
    }

    private void ensureOwnerVisible(RestaurantOrder order) {
        if (order.getApprovalStatus() == OrderApprovalStatus.PENDING
                || order.getApprovalStatus() == OrderApprovalStatus.REJECTED) {
            throw new ResourceNotFoundException("Order not found");
        }
    }
}
