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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Objects;

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

        List<Long> requestedItemIds = request.getItems().stream()
                .map(OrderItemRequest::getItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, MenuItem> menuItemsById = new HashMap<>();
        if (!requestedItemIds.isEmpty()) {
            menuItemRepository.findAllWithCategoryAndRestaurantByIdIn(requestedItemIds)
                    .forEach(menuItem -> menuItemsById.put(menuItem.getId(), menuItem));
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

            MenuItem menuItem = menuItemsById.get(requestedItem.getItemId());
            if (menuItem == null) {
                throw new ResourceNotFoundException(
                        "Menu item not found: " + requestedItem.getItemId());
            }

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

        if (accessibleOrders.isEmpty()) {
            return List.of();
        }
        List<Long> sortedIds = accessibleOrders.values().stream()
                .sorted((first, second) ->
                        second.getCreatedAt().compareTo(first.getCreatedAt()))
                .limit(50)
                .map(RestaurantOrder::getId)
                .toList();
        Map<Long, RestaurantOrder> detailedById = new LinkedHashMap<>();
        for (RestaurantOrder detailed : orderRepository.findAllWithItemsRestaurantAndTableByIdIn(sortedIds)) {
            detailedById.put(detailed.getId(), detailed);
        }
        List<RestaurantOrder> orderedDetails = sortedIds.stream()
                .map(detailedById::get)
                .filter(Objects::nonNull)
                .toList();
        return mapOwnerOrdersWithBills(orderedDetails);
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
        List<RestaurantOrder> orders = orderRepository.findAllOwnerOrdersWithDetails(
                restaurantId,
                List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED));
        return mapOwnerOrdersWithBills(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForOwner(Long restaurantId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        List<Long> orderIds = orderRepository.findOwnerOrderIdsPage(
                restaurantId,
                List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED),
                PageRequest.of(safePage, safeSize));
        if (orderIds.isEmpty()) {
            return List.of();
        }
        Map<Long, RestaurantOrder> byId = new LinkedHashMap<>();
        for (RestaurantOrder order : orderRepository.findAllWithItemsRestaurantAndTableByIdIn(orderIds)) {
            byId.put(order.getId(), order);
        }
        List<RestaurantOrder> ordered = orderIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return mapOwnerOrdersWithBills(ordered);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getKitchenOrdersForOwner(Long restaurantId) {
        List<RestaurantOrder> orders = orderRepository.findKitchenOrdersWithDetails(
                restaurantId,
                List.of(OrderStatus.NEW, OrderStatus.ACCEPTED,
                        OrderStatus.PREPARING, OrderStatus.READY),
                List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED));
        return orders.stream()
                .map(order -> mapToResponse(order, null))
                .toList();
    }

    private List<OrderResponse> mapOwnerOrdersWithBills(List<RestaurantOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream()
                .map(RestaurantOrder::getId)
                .toList();
        Map<Long, BillResponse> billResponsesByOrderId = new LinkedHashMap<>();
        for (Bill bill : billRepository.findAllWithOrderByOrder_IdIn(orderIds)) {
            billResponsesByOrderId.put(
                    bill.getOrder().getId(),
                    billService.mapToResponse(bill));
        }

        List<OrderResponse> responses = new ArrayList<>(orders.size());
        for (RestaurantOrder order : orders) {
            BillResponse billResponse = billResponsesByOrderId.get(order.getId());
            if (billResponse == null) {
                throw new ResourceNotFoundException("Bill not found");
            }
            responses.add(mapToResponse(order, billResponse));
        }
        return responses;
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

    @Transactional(readOnly = true)
    public List<OrderResponse> toResponses(List<RestaurantOrder> orders) {
        return mapOwnerOrdersWithBills(orders);
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
            long initialQueryStartedAtNanos = DashboardRequestMeasurements.startStage();
            List<RestaurantOrder> latestOrders;
            try {
                latestOrders = orderRepository
                        .findTop10ByRestaurant_IdOrderByCreatedAtDesc(restaurantId);
            } finally {
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.RECENT_INITIAL_ORDER_REPOSITORY,
                        initialQueryStartedAtNanos);
            }
            List<RestaurantOrder> visibleOrders = latestOrders
                    .stream()
                    .filter(order -> order.getApprovalStatus() != OrderApprovalStatus.PENDING
                            && order.getApprovalStatus() != OrderApprovalStatus.REJECTED)
                    .toList();
            if (visibleOrders.isEmpty()) {
                DashboardRequestMeasurements.recordOrderPayloadCounts(0, 0, 0);
                return List.of();
            }

            List<Long> orderIds = visibleOrders.stream()
                    .map(RestaurantOrder::getId)
                    .toList();
            long detailQueryStartedAtNanos = DashboardRequestMeasurements.startStage();
            try {
                orderRepository.findAllWithItemsRestaurantAndTableByIdIn(orderIds);
            } finally {
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.RECENT_DETAIL_ORDER_REPOSITORY,
                        detailQueryStartedAtNanos);
            }

            long billQueryStartedAtNanos = DashboardRequestMeasurements.startStage();
            List<Bill> bills;
            try {
                bills = billRepository.findAllByOrder_IdIn(orderIds);
            } finally {
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.RECENT_BILL_REPOSITORY,
                        billQueryStartedAtNanos);
            }

            long mappingStartedAtNanos = DashboardRequestMeasurements.startStage();
            try {
                Map<Long, Bill> billsByOrderId = new LinkedHashMap<>();
                long billItemCount = 0;
                for (Bill bill : bills) {
                    billsByOrderId.put(bill.getOrder().getId(), bill);
                }

                List<OrderResponse> responses = new ArrayList<>(visibleOrders.size());
                long itemCount = 0;
                for (RestaurantOrder order : visibleOrders) {
                    Bill bill = billsByOrderId.get(order.getId());
                    if (bill == null) {
                        throw new ResourceNotFoundException("Bill not found");
                    }
                    var billResponse = billService.mapToResponse(bill);
                    billItemCount += billResponse.getItems() == null
                            ? 0
                            : billResponse.getItems().size();
                    OrderResponse response = mapToResponse(order, billResponse);
                    itemCount += response.getItems() == null ? 0 : response.getItems().size();
                    responses.add(response);
                }
                DashboardRequestMeasurements.recordOrderPayloadCounts(
                        responses.size(), itemCount, billItemCount);
                return responses;
            } finally {
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.RECENT_DTO_MAPPING,
                        mappingStartedAtNanos);
            }
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
            long ordersQueryStartedAtNanos = DashboardRequestMeasurements.startStage();
            List<RestaurantOrder> activeOrders;
            try {
                activeOrders = orderRepository
                        .findAllByRestaurant_IdAndOrderStatusInOrderByCreatedAtDesc(
                                restaurantId, activeStatuses);
            } finally {
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.ACTIVE_ORDERS_REPOSITORY,
                        ordersQueryStartedAtNanos);
            }
            activeOrders
                    .stream()
                    .filter(order -> order.getApprovalStatus() != OrderApprovalStatus.PENDING
                            && order.getApprovalStatus() != OrderApprovalStatus.REJECTED)
                    .forEach(order -> visibleOrdersById.putIfAbsent(order.getId(), order));

            if (visibleOrdersById.isEmpty()) {
                DashboardRequestMeasurements.recordOrderPayloadCounts(0, 0, 0);
                return List.of();
            }

            List<Long> orderIds = new ArrayList<>(visibleOrdersById.keySet());
            long billsQueryStartedAtNanos = DashboardRequestMeasurements.startStage();
            List<Bill> bills;
            try {
                bills = billRepository.findAllByOrder_IdIn(orderIds);
            } finally {
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.ACTIVE_BILLS_REPOSITORY,
                        billsQueryStartedAtNanos);
            }

            long mappingStartedAtNanos = DashboardRequestMeasurements.startStage();
            try {
                Map<Long, BillResponse> billResponsesByOrderId = new LinkedHashMap<>();
                long billItemCount = 0;
                for (Bill bill : bills) {
                    var billResponse = billService.mapToResponse(bill);
                    billItemCount += billResponse.getItems() == null
                            ? 0
                            : billResponse.getItems().size();
                    billResponsesByOrderId.put(
                            bill.getOrder().getId(),
                            billResponse);
                }

                List<OrderResponse> responses = new ArrayList<>(visibleOrdersById.size());
                long itemCount = 0;
                for (RestaurantOrder order : visibleOrdersById.values()) {
                    BillResponse billResponse = billResponsesByOrderId.get(order.getId());
                    if (billResponse == null) {
                        throw new ResourceNotFoundException("Bill not found");
                    }
                    OrderResponse response = mapToResponse(order, billResponse);
                    itemCount += response.getItems() == null ? 0 : response.getItems().size();
                    responses.add(response);
                }
                DashboardRequestMeasurements.recordOrderPayloadCounts(
                        responses.size(), itemCount, billItemCount);
                return responses;
            } finally {
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.ACTIVE_DTO_MAPPING,
                        mappingStartedAtNanos);
            }
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
