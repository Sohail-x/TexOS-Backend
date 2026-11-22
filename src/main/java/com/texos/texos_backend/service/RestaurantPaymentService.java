package com.texos.texos_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.entity.*;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceConflictException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantPaymentService {
    private static final Duration WAITER_RECALL_COOLDOWN = Duration.ofMinutes(2);
    private static final List<String> OPEN_ATTEMPT_STATES =
            List.of("CREATED", "PROCESSING", "PENDING", "UNKNOWN");

    private final RestaurantTableRepository tableRepository;
    private final RestaurantOrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final BillRepository billRepository;
    private final CustomerBillService customerBillService;
    private final CustomerSessionService customerSessionService;
    private final CustomerPaymentTransactionRepository transactionRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentAuditLogRepository auditRepository;
    private final RestaurantPhonePeConfigService configService;
    private final PhonePeGateway phonePeGateway;
    private final OfflineAssistanceRequestRepository assistanceRepository;
    private final RestaurantPaymentNotificationRepository notificationRepository;
    private final PhonePeWebhookEventRepository webhookRepository;
    private final PaymentReceiptRepository receiptRepository;
    private final OwnerRepository ownerRepository;
    private final LoyaltyService loyaltyService;
    private final ObjectMapper objectMapper;

    @Value("${app.customer-base-url}")
    private String customerBaseUrl;

    @Transactional
    public Map<String, Object> createOnlinePayment(String qrToken, HttpServletRequest request) {
        RestaurantTable table = requireActiveTable(qrToken);
        BillResponse bill = customerBillService.getCurrentBill(qrToken, request);
        if (bill.getPaymentStatus() != PaymentStatus.PENDING || !bill.isEligibleForOnlinePayment()) {
            throw new ResourceConflictException("Online payment is available after all bill items are served.");
        }
        if (!bill.isOnlinePaymentAvailable()) {
            throw new ResourceConflictException(
                    "Online payments are not enabled for this restaurant. Please call a waiter.");
        }

        List<RestaurantOrder> sittingOrders = lockAndValidateBillOrders(
                table.getRestaurant().getId(), table.getId(), bill.getOrderIds(), true);
        RestaurantOrder latestOrder = sittingOrders.stream()
                .max(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .orElseThrow(() -> new ResourceNotFoundException("No eligible unpaid bill was found"));
        String sessionKey = latestOrder.getOrderSessionKey();
        if (sessionKey == null || sittingOrders.stream()
                .anyMatch(order -> !Objects.equals(sessionKey, order.getOrderSessionKey()))) {
            throw new ResourceConflictException(
                    "This bill cannot be paid online because its orders do not share one active restaurant sitting.");
        }

        BigDecimal amount = sittingOrders.stream()
                .map(RestaurantOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        long amountMinor = amount.movePointRight(2).longValueExact();
        if (amountMinor < 100 || amount.compareTo(bill.getTotalAmount()) != 0) {
            throw new ResourceConflictException("The bill total changed. Refresh the bill before paying.");
        }

        RestaurantPhonePeConfig config = configService.requireConfigured(table.getRestaurant().getId());
        String activeKey = activeKey(table.getRestaurant().getId(), sessionKey);
        CustomerPaymentTransaction transaction = transactionRepository
                .findByRestaurantAndActiveKeyForUpdate(table.getRestaurant().getId(), activeKey)
                .orElse(null);
        if (transaction != null && isUnresolved(transaction.getPaymentStatus())) {
            PaymentAttempt existingAttempt = attemptRepository
                    .findFirstByPaymentTransaction_IdOrderByCreatedAtDesc(transaction.getId())
                    .orElse(null);
            return paymentView(transaction, existingAttempt, null);
        }
        if (transaction != null && "PAID".equals(transaction.getPaymentStatus())) {
            throw new ResourceConflictException("This bill has already been paid.");
        }

        if (transaction == null) {
            transaction = new CustomerPaymentTransaction();
            transaction.setRestaurant(table.getRestaurant());
            transaction.setTable(table);
            transaction.setCustomer(latestOrder.getCustomer());
            transaction.setOrderSessionKey(sessionKey);
            transaction.setBillKey(sessionKey);
            transaction.setActiveKey(activeKey);
            transaction.setCurrency("INR");
        }
        transaction.setAmount(amount);
        transaction.setPaymentStatus("PROCESSING");
        transaction.setPaymentMethod("PHONEPE");
        transaction.setOrders(new LinkedHashSet<>(sittingOrders));
        transaction = transactionRepository.saveAndFlush(transaction);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentTransaction(transaction);
        attempt.setMerchantOrderId("TXOS-" + table.getRestaurant().getId() + "-"
                + UUID.randomUUID().toString().replace("-", ""));
        attempt.setAmountMinor(amountMinor);
        attempt.setPaymentStatus("PROCESSING");
        attemptRepository.saveAndFlush(attempt);
        audit(transaction, attempt, "PAYMENT_ATTEMPT_CREATED", "CUSTOMER", null,
                Map.of("amount", amount, "merchantOrderId", attempt.getMerchantOrderId()));

        String redirectUrl = customerBaseUrl.replaceAll("/+$", "") + "/menu/"
                + java.net.URLEncoder.encode(qrToken, java.nio.charset.StandardCharsets.UTF_8)
                + "?phonepePayment=" + attempt.getMerchantOrderId();
        PhonePeMerchantCredentials credentials = configService.getCredentials(config);
        try {
            PhonePeGateway.CreatedPayment created = phonePeGateway.createPayment(
                    "restaurant-" + table.getRestaurant().getId(),
                    credentials,
                    attempt.getMerchantOrderId(),
                    amountMinor,
                    redirectUrl);
            attempt.setProviderOrderId(created.providerOrderId());
            attempt.setCheckoutUrl(created.checkoutUrl());
            attempt.setProviderState(created.state());
            attempt.setPaymentStatus("PENDING");
            transaction.setPaymentStatus("PENDING");
            attemptRepository.save(attempt);
            transactionRepository.save(transaction);
            audit(transaction, attempt, "PHONEPE_CHECKOUT_CREATED", "PHONEPE", null,
                    Map.of("providerOrderId", created.providerOrderId()));
        } catch (PhonePeGateway.PhonePeRequestException ex) {
            if (ex.getHttpStatus() >= 500) {
                markUnknown(transaction, attempt, "PHONEPE_HTTP_" + ex.getHttpStatus());
            } else {
                markFailed(transaction, attempt, "PHONEPE_HTTP_" + ex.getHttpStatus());
            }
        } catch (PhonePeGateway.PhonePeTransportException ex) {
            markUnknown(transaction, attempt, "PHONEPE_TRANSPORT_ERROR");
        } catch (IllegalStateException ex) {
            markUnknown(transaction, attempt, "PHONEPE_RESPONSE_UNRESOLVED");
        }
        return paymentView(transaction, attempt, null);
    }

    @Transactional
    public Map<String, Object> getCustomerPaymentStatus(
            String merchantOrderId, String qrToken, HttpServletRequest request) {
        PaymentAttempt attempt = attemptRepository.findByMerchantOrderId(merchantOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment attempt not found"));
        CustomerPaymentTransaction transaction = attempt.getPaymentTransaction();
        authorizeCustomerTransaction(transaction, qrToken, request);
        if (!"PAID".equals(transaction.getPaymentStatus())) {
            reconcileAttempt(attempt.getMerchantOrderId());
        }
        return paymentView(transaction, attempt, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCustomerPaymentHistory(
            String qrToken, HttpServletRequest request) {
        RestaurantTable table = requireActiveTable(qrToken);
        Long restaurantId = table.getRestaurant().getId();
        CustomerSession session = customerSessionService.getActiveSession(request, restaurantId);
        Map<Long, CustomerPaymentTransaction> transactions = new LinkedHashMap<>();
        if (session != null) {
            transactionRepository.findAllByRestaurant_IdAndCustomer_IdOrderByCreatedAtDesc(
                            restaurantId, session.getCustomerId())
                    .forEach(transaction -> transactions.put(transaction.getId(), transaction));
        } else {
            Set<String> orderNumbers = customerSessionService.getOrderNumbers(request, restaurantId);
            if (!orderNumbers.isEmpty()) {
                List<Long> orderIds = orderRepository.findAllByOrderNumberInAndRestaurant_IdOrderByCreatedAtDesc(
                                orderNumbers, restaurantId)
                        .stream()
                        .filter(order -> order.getTable().getId().equals(table.getId()))
                        .map(RestaurantOrder::getId)
                        .toList();
                transactionRepository.findAllByOrders_IdInAndRestaurant_Id(orderIds, restaurantId)
                        .forEach(transaction -> transactions.put(transaction.getId(), transaction));
            }
        }
        return transactions.values().stream()
                .sorted(Comparator.comparing(CustomerPaymentTransaction::getCreatedAt).reversed())
                .map(transaction -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("transactionId", transaction.getId());
                    view.put("amount", transaction.getAmount());
                    view.put("paymentStatus", transaction.getPaymentStatus());
                    view.put("paymentMethod", nullToEmpty(transaction.getPaymentMethod()));
                    view.put("paymentReference", nullToEmpty(transaction.getPaymentReference()));
                    view.put("paidAt", transaction.getPaidAt());
                    view.put("createdAt", transaction.getCreatedAt());
                    view.put("orderNumbers", transaction.getOrders().stream()
                            .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt))
                            .map(RestaurantOrder::getOrderNumber).toList());
                    return view;
                }).toList();
    }

    @Transactional
    public Map<String, Object> recordOfflinePayment(
            Long restaurantId, Long orderId, Long ownerId, String method) {
        if (!"CASH".equals(method) && !"OFFLINE_UPI".equals(method)) {
            throw new InvalidRequestException("Offline payment method must be CASH or OFFLINE_UPI");
        }
        Restaurant restaurant = restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        BillResponse bill = customerBillService.getBillForOwner(restaurantId, orderId);
        List<RestaurantOrder> orders = lockAndValidateBillOrders(
                restaurantId, null, bill.getOrderIds(), false);
        if (orders.stream().anyMatch(order -> order.getPaymentStatus() == PaymentStatus.PAID
                || order.getPaymentStatus() == PaymentStatus.REFUNDED)) {
            throw new ResourceConflictException("This bill has already been paid.");
        }
        RestaurantOrder latestOrder = orders.stream()
                .max(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
        String sessionKey = latestOrder.getOrderSessionKey();
        String billKey = sessionKey == null
                ? orders.stream().map(order -> String.valueOf(order.getId())).sorted()
                    .reduce((left, right) -> left + "," + right).orElseThrow()
                : sessionKey;
        String activeKey = activeKey(restaurantId, billKey);
        CustomerPaymentTransaction transaction = transactionRepository
                .findByRestaurantAndActiveKeyForUpdate(restaurantId, activeKey)
                .orElse(null);
        if (transaction != null && isUnresolved(transaction.getPaymentStatus())) {
            throw new ResourceConflictException(
                    "An online payment is still being verified. Reconcile it before recording offline collection.");
        }
        if (transaction == null) {
            transaction = new CustomerPaymentTransaction();
            transaction.setRestaurant(restaurant);
            transaction.setTable(latestOrder.getTable());
            transaction.setCustomer(latestOrder.getCustomer());
            transaction.setOrderSessionKey(sessionKey);
            transaction.setBillKey(billKey);
            transaction.setCurrency("INR");
        }
        BigDecimal amount = orders.stream().map(RestaurantOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        transaction.setAmount(amount);
        transaction.setPaymentStatus("PAID");
        transaction.setPaymentMethod(method);
        transaction.setPaymentReference(null);
        transaction.setPaidAt(LocalDateTime.now(ZoneOffset.UTC));
        transaction.setActiveKey(null);
        transaction.setOrders(new LinkedHashSet<>(orders));
        transaction = transactionRepository.saveAndFlush(transaction);
        persistPaymentReceipt(transaction);

        for (RestaurantOrder order : orders) {
            order.setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(order);
            loyaltyService.processOrderLifecycle(order);
        }
        audit(transaction, null, "OFFLINE_PAYMENT_COLLECTED", "OWNER", ownerId,
                Map.of("method", method, "amount", amount));
        return transactionView(transaction);
    }

    @Transactional
    public Map<String, Object> createAssistanceRequest(String qrToken, HttpServletRequest request) {
        RestaurantTable table = requireActiveTable(qrToken);
        BillResponse bill = customerBillService.getCurrentBill(qrToken, request);
        if (bill.getPaymentStatus() != PaymentStatus.PENDING || bill.getOrderIds() == null
                || bill.getOrderIds().isEmpty()) {
            throw new ResourceConflictException("There is no active unpaid bill for this table.");
        }
        List<RestaurantOrder> orders = lockAndValidateBillOrders(
                table.getRestaurant().getId(), table.getId(), bill.getOrderIds(), false);
        RestaurantOrder latestOrder = orders.stream()
                .max(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .orElseThrow(() -> new ResourceNotFoundException("No active bill was found"));
        String sessionKey = latestOrder.getOrderSessionKey();
        String billKey = sessionKey == null
                ? orders.stream().map(order -> String.valueOf(order.getId())).sorted()
                    .reduce((left, right) -> left + "," + right).orElseThrow()
                : sessionKey;
        OfflineAssistanceRequest existing = assistanceRepository
                .findByRestaurant_IdAndActiveBillKey(table.getRestaurant().getId(), billKey)
                .orElse(null);
        if (existing != null) return assistanceView(existing);

        OfflineAssistanceRequest assistance = new OfflineAssistanceRequest();
        assistance.setRestaurant(table.getRestaurant());
        assistance.setTable(table);
        assistance.setCustomer(latestOrder.getCustomer());
        assistance.setOrderSessionKey(sessionKey);
        assistance.setLatestOrder(latestOrder);
        assistance.setBillKey(billKey);
        assistance.setActiveBillKey(billKey);
        assistance.setRequestStatus("PENDING");
        assistance.setLastRecalledAt(LocalDateTime.now(ZoneOffset.UTC));
        assistance.setCustomerName(latestOrder.getCustomerName());
        assistance.setCustomerMobile(latestOrder.getCustomerMobile());
        assistance = assistanceRepository.saveAndFlush(assistance);

        RestaurantPaymentNotification notification = new RestaurantPaymentNotification();
        notification.setRestaurant(table.getRestaurant());
        notification.setNotificationType("CALL_WAITER");
        notification.setAssistanceRequest(assistance);
        notification.setPriority(0);
        notification.setNotificationStatus("PENDING");
        notificationRepository.save(notification);
        return assistanceView(assistance);
    }

    @Transactional
    public Map<String, Object> recallWaiter(
            Long requestId, String qrToken, HttpServletRequest request) {
        RestaurantTable table = requireActiveTable(qrToken);
        OfflineAssistanceRequest assistance = assistanceRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter request not found"));
        authorizeCustomerAssistance(assistance, table, request);
        if (!"PENDING".equals(assistance.getRequestStatus())) {
            throw new ResourceConflictException("This waiter request is no longer active.");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime lastRecalledAt = assistance.getLastRecalledAt() != null
                ? assistance.getLastRecalledAt()
                : assistance.getRequestedAt();
        if (lastRecalledAt.plus(WAITER_RECALL_COOLDOWN).isAfter(now)) {
            throw new ResourceConflictException(
                    "Please wait 2 minutes before trying again.");
        }

        assistance.setLastRecalledAt(now);
        assistanceRepository.save(assistance);

        RestaurantPaymentNotification notification = new RestaurantPaymentNotification();
        notification.setRestaurant(assistance.getRestaurant());
        notification.setNotificationType("CALL_WAITER");
        notification.setAssistanceRequest(assistance);
        notification.setPriority(0);
        notification.setNotificationStatus("PENDING");
        notificationRepository.save(notification);
        return assistanceView(assistance);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAssistanceStatus(
            Long requestId, String qrToken, HttpServletRequest request) {
        RestaurantTable table = requireActiveTable(qrToken);
        OfflineAssistanceRequest assistance = assistanceRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter request not found"));
        authorizeCustomerAssistance(assistance, table, request);
        return assistanceView(assistance);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getNextOwnerNotification(Long restaurantId, Long ownerId) {
        restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        return notificationRepository
                .findFirstByRestaurant_IdAndNotificationStatusOrderByPriorityAscCreatedAtAsc(
                        restaurantId, "PENDING")
                .map(this::notificationView)
                .orElseGet(Map::of);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPaymentHistory(Long restaurantId, Long ownerId) {
        restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        return transactionRepository.findAllByRestaurant_IdOrderByCreatedAtDesc(restaurantId)
                .stream().map(transaction -> {
                    Map<String, Object> view = transactionView(transaction);
                    view.put("orderNumbers", transaction.getOrders().stream()
                            .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt))
                            .map(RestaurantOrder::getOrderNumber).toList());
                    view.put("tableNumber", transaction.getTable().getTableNumber());
                    view.put("customerName", transaction.getOrders().stream()
                            .max(Comparator.comparing(RestaurantOrder::getCreatedAt))
                            .map(RestaurantOrder::getCustomerName).orElse(""));
                    return view;
                }).toList();
    }

    @Transactional
    public void dismissOwnerNotification(Long restaurantId, Long notificationId, Long ownerId) {
        restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        RestaurantPaymentNotification notification = notificationRepository
                .findByIdAndRestaurant_Id(notificationId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment notification not found"));
        if (!"PENDING".equals(notification.getNotificationStatus())) return;
        notification.setNotificationStatus("DISMISSED");
        notification.setClosedAt(LocalDateTime.now(ZoneOffset.UTC));
        notificationRepository.save(notification);
    }

    @Transactional
    public Map<String, Object> assignWaiter(Long restaurantId, Long notificationId, Long ownerId) {
        restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        RestaurantPaymentNotification notification = notificationRepository
                .findByIdAndRestaurant_Id(notificationId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter request not found"));
        if (!"CALL_WAITER".equals(notification.getNotificationType())
                || notification.getAssistanceRequest() == null) {
            throw new InvalidRequestException("This notification is not a waiter request");
        }
        OfflineAssistanceRequest assistance = assistanceRepository
                .findByIdAndRestaurantForUpdate(notification.getAssistanceRequest().getId(), restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter request not found"));
        if (!"PENDING".equals(assistance.getRequestStatus())) {
            throw new ResourceConflictException("This waiter request has already been assigned.");
        }
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        LocalDateTime assignedAt = LocalDateTime.now(ZoneOffset.UTC);
        assistance.setRequestStatus("ASSIGNED");
        assistance.setActiveBillKey(null);
        assistance.setAssignedAt(assignedAt);
        assistance.setAssignedBy(owner);
        assistanceRepository.save(assistance);
        notification.setNotificationStatus("ASSIGNED");
        notification.setClosedAt(assignedAt);
        notificationRepository.save(notification);
        Map<String, Object> result = assistanceView(assistance);
        result.put("message", "A waiter will be with you shortly. Please wait.");
        return result;
    }

    @Transactional
    public Map<String, Object> getReceipt(Long restaurantId, Long paymentTransactionId, Long ownerId) {
        restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        CustomerPaymentTransaction transaction = transactionRepository
                .findByIdAndRestaurant_Id(paymentTransactionId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment receipt not found"));
        if (!"PAID".equals(transaction.getPaymentStatus())) {
            throw new ResourceNotFoundException("Verified payment receipt not found");
        }
        PaymentReceipt existingReceipt = receiptRepository
                .findByPaymentTransaction_Id(transaction.getId()).orElse(null);
        if (existingReceipt != null) {
            try {
                return objectMapper.readValue(
                        existingReceipt.getReceiptJson(), new TypeReference<>() {});
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Unable to read the stored payment receipt", ex);
            }
        }
        return persistPaymentReceipt(transaction);
    }

    private Map<String, Object> persistPaymentReceipt(CustomerPaymentTransaction transaction) {
        PaymentReceipt existingReceipt = receiptRepository
                .findByPaymentTransaction_Id(transaction.getId()).orElse(null);
        if (existingReceipt != null) {
            try {
                return objectMapper.readValue(
                        existingReceipt.getReceiptJson(), new TypeReference<>() {});
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Unable to read the stored payment receipt", ex);
            }
        }
        Map<String, Object> receipt = buildReceiptView(transaction);
        PaymentReceipt record = new PaymentReceipt();
        record.setPaymentTransaction(transaction);
        record.setReceiptNumber((String) receipt.get("receiptNumber"));
        try {
            record.setReceiptJson(objectMapper.writeValueAsString(receipt));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to store the payment receipt", ex);
        }
        receiptRepository.save(record);
        return receipt;
    }

    private Map<String, Object> buildReceiptView(CustomerPaymentTransaction transaction) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        Restaurant restaurant = transaction.getRestaurant();
        RestaurantOrder latestOrder = transaction.getOrders().stream()
                .max(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .orElseThrow(() -> new ResourceNotFoundException("Payment receipt has no orders"));
        List<Map<String, Object>> items = transaction.getOrders().stream()
                .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .flatMap(order -> order.getItems().stream())
                .map(item -> {
                    Map<String, Object> itemView = new LinkedHashMap<>();
                    itemView.put("name", item.getItemName());
                    itemView.put("customization", nullToEmpty(item.getCustomization()));
                    itemView.put("quantity", item.getQuantity());
                    itemView.put("unitPrice", item.getUnitPrice());
                    itemView.put("lineTotal", item.getLineTotal());
                    return itemView;
                }).toList();
        List<String> orderNumbers = transaction.getOrders().stream()
                .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .map(RestaurantOrder::getOrderNumber).toList();
        List<String> billNumbers = transaction.getOrders().stream()
                .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .map(order -> billRepository.findByOrderOrderNumber(order.getOrderNumber())
                        .map(Bill::getBillNumber).orElse(""))
                .filter(number -> !number.isBlank()).toList();
        receipt.put("receiptNumber", "RCP/" + transaction.getId());
        receipt.put("restaurantName", restaurant.getRestaurantName());
        receipt.put("restaurantAddress", restaurant.getAddress());
        receipt.put("restaurantPhone", restaurant.getPhone());
        receipt.put("restaurantEmail", restaurant.getEmail());
        receipt.put("customerName", latestOrder.getCustomerName());
        receipt.put("customerMobile", latestOrder.getCustomerMobile());
        receipt.put("orderNumbers", orderNumbers);
        receipt.put("billNumbers", billNumbers);
        receipt.put("tableNumber", transaction.getTable().getTableNumber());
        receipt.put("items", items);
        receipt.put("totalAmount", transaction.getAmount());
        receipt.put("paymentMethod", transaction.getPaymentMethod());
        receipt.put("paymentReference", transaction.getPaymentReference());
        receipt.put("paymentDate", transaction.getPaidAt() == null
                ? null
                : transaction.getPaidAt().toInstant(ZoneOffset.UTC).toString());
        receipt.put("paymentStatus", "VERIFIED");
        return receipt;
    }

    @Transactional
    public void acceptPhonePeWebhook(String rawBody, String keyId, String signature) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException ex) {
            throw new InvalidRequestException("Invalid PhonePe webhook payload");
        }
        String eventType = root.path("event").asText("");
        JsonNode payload = root.path("payload");
        String merchantOrderId = payload.path("merchantOrderId").asText("");
        if (merchantOrderId.isBlank()
                || !eventType.equals("checkout.order.completed")
                && !eventType.equals("checkout.order.failed")) {
            throw new InvalidRequestException("Unsupported PhonePe webhook event");
        }
        PaymentAttempt attempt = attemptRepository.findByMerchantOrderId(merchantOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PhonePe payment attempt not found"));
        RestaurantPhonePeConfig config = configService.findConfiguration(
                attempt.getPaymentTransaction().getRestaurant().getId());
        phonePeGateway.verifyWebhookSignature(
                rawBody, keyId, signature, config.getWebhookKeyId(),
                configService.getWebhookSecret(config));
        String payloadMerchantId = payload.path("merchantId").asText("");
        if (!payloadMerchantId.isBlank() && !config.getMerchantId().equals(payloadMerchantId)) {
            throw new SecurityException("PhonePe webhook merchant does not match the restaurant");
        }
        String providerOrderId = payload.path("orderId").asText("");
        if (!providerOrderId.isBlank() && attempt.getProviderOrderId() != null
                && !attempt.getProviderOrderId().equals(providerOrderId)) {
            throw new SecurityException("PhonePe webhook order does not match the payment attempt");
        }
        String eventHash = phonePeGateway.sha256(rawBody);
        if (webhookRepository.findByEventHash(eventHash).isPresent()) return;
        PhonePeWebhookEvent event = new PhonePeWebhookEvent();
        event.setEventHash(eventHash);
        event.setMerchantOrderId(merchantOrderId);
        event.setEventType(eventType);
        event.setPayloadJson(rawBody);
        event.setSignatureVerified(true);
        event.setProcessingStatus("RECEIVED");
        webhookRepository.save(event);
        audit(attempt.getPaymentTransaction(), attempt, "PHONEPE_WEBHOOK_RECEIVED",
                "PHONEPE", null, Map.of("event", eventType, "eventHash", eventHash));
    }

    @Transactional(readOnly = true)
    public boolean isKnownPhonePeOrder(String merchantOrderId) {
        return merchantOrderId != null && !merchantOrderId.isBlank()
                && attemptRepository.findByMerchantOrderId(merchantOrderId).isPresent();
    }

    @Transactional
    public Map<String, Object> reconcileAttempt(String merchantOrderId) {
        PaymentAttempt attempt = attemptRepository.findByMerchantOrderId(merchantOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment attempt not found"));
        CustomerPaymentTransaction transaction = transactionRepository
                .findByIdForUpdate(attempt.getPaymentTransaction().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found"));
        attempt = attemptRepository.findByMerchantOrderId(merchantOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment attempt not found"));
        if ("PAID".equals(transaction.getPaymentStatus())) return paymentView(transaction, attempt, null);

        RestaurantPhonePeConfig config = configService.findConfiguration(
                transaction.getRestaurant().getId());
        PhonePeGateway.VerifiedPayment status = phonePeGateway.getOrderStatus(
                "restaurant-" + transaction.getRestaurant().getId(),
                configService.getCredentials(config),
                attempt.getMerchantOrderId(),
                attempt.getAmountMinor());
        if (!status.merchantId().isBlank() && !config.getMerchantId().equals(status.merchantId())) {
            throw new SecurityException("PhonePe status merchant does not match the restaurant");
        }
        if (!status.providerOrderId().isBlank() && attempt.getProviderOrderId() != null
                && !attempt.getProviderOrderId().equals(status.providerOrderId())) {
            throw new SecurityException("PhonePe status order does not match the payment attempt");
        }
        applyVerifiedStatus(transaction, attempt, status);
        return paymentView(transaction, attempt, null);
    }

    @Transactional(readOnly = true)
    public List<String> getUnresolvedMerchantOrderIds() {
        return attemptRepository.findTop100ByPaymentStatusInOrderByUpdatedAtAsc(OPEN_ATTEMPT_STATES)
                .stream().map(PaymentAttempt::getMerchantOrderId).toList();
    }

    @Transactional
    public List<Long> getPendingWebhookIds() {
        return webhookRepository.findTop100ByProcessingStatusInOrderByReceivedAtAsc(
                        List.of("RECEIVED", "RETRY"))
                .stream().map(PhonePeWebhookEvent::getId).toList();
    }

    @Transactional
    public void processWebhookEvent(Long eventId) {
        PhonePeWebhookEvent event = webhookRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("PhonePe webhook event not found"));
        try {
            reconcileAttempt(event.getMerchantOrderId());
            event.setProcessingStatus("PROCESSED");
            event.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
        } catch (PhonePeGateway.PhonePeRequestException | PhonePeGateway.PhonePeTransportException ex) {
            event.setProcessingStatus("RETRY");
        }
        webhookRepository.save(event);
    }

    private void applyVerifiedStatus(
            CustomerPaymentTransaction transaction,
            PaymentAttempt attempt,
            PhonePeGateway.VerifiedPayment status) {
        String providerState = status.state();
        attempt.setProviderState(providerState);
        attempt.setErrorCode(status.errorCode());
        attempt.setPaymentMethod(status.paymentMethod());
        attempt.setProviderPaymentReference(status.paymentReference());
        if ("COMPLETED".equals(providerState)) {
            if (status.amountMinor() != attempt.getAmountMinor()) {
                throw new SecurityException("PhonePe captured amount does not match the bill");
            }
            LocalDateTime paidAt = status.paidAt() == null
                    ? LocalDateTime.now(ZoneOffset.UTC) : status.paidAt();
            attempt.setPaymentStatus("COMPLETED");
            attempt.setCompletedAt(paidAt);
            transaction.setPaymentStatus("PAID");
            transaction.setPaymentMethod(status.paymentMethod());
            transaction.setPaymentReference(status.paymentReference());
            transaction.setPaidAt(paidAt);
            transaction.setActiveKey(null);
            for (RestaurantOrder order : transaction.getOrders()) {
                if (order.getPaymentStatus() != PaymentStatus.PAID) {
                    order.setPaymentStatus(PaymentStatus.PAID);
                    orderRepository.save(order);
                    loyaltyService.processOrderLifecycle(order);
                }
            }
            transactionRepository.save(transaction);
            attemptRepository.save(attempt);
            persistPaymentReceipt(transaction);
            createPaymentNotification(transaction);
            audit(transaction, attempt, "PHONEPE_PAYMENT_VERIFIED", "PHONEPE", null,
                    Map.of("state", providerState,
                            "paymentReference", status.paymentReference() == null ? "" : status.paymentReference(),
                            "paymentMethod", status.paymentMethod() == null ? "" : status.paymentMethod(),
                            "paidAt", paidAt.toString()));
            return;
        }
        if ("FAILED".equals(providerState) || "CANCELLED".equals(providerState)) {
            attempt.setPaymentStatus(providerState);
            transaction.setPaymentStatus(providerState);
            attemptRepository.save(attempt);
            transactionRepository.save(transaction);
            audit(transaction, attempt, "PHONEPE_PAYMENT_" + providerState,
                    "PHONEPE", null, Map.of("state", providerState));
            return;
        }
        if ("PENDING".equals(providerState) || "PROCESSING".equals(providerState)) {
            attempt.setPaymentStatus(providerState);
            transaction.setPaymentStatus(providerState);
        } else {
            attempt.setPaymentStatus("UNKNOWN");
            transaction.setPaymentStatus("UNKNOWN");
        }
        attemptRepository.save(attempt);
        transactionRepository.save(transaction);
    }

    private void createPaymentNotification(CustomerPaymentTransaction transaction) {
        if (notificationRepository.existsByPaymentTransaction_IdAndNotificationType(
                transaction.getId(), "PAYMENT_SUCCESS")) return;
        RestaurantPaymentNotification notification = new RestaurantPaymentNotification();
        notification.setRestaurant(transaction.getRestaurant());
        notification.setNotificationType("PAYMENT_SUCCESS");
        notification.setPaymentTransaction(transaction);
        notification.setPriority(1);
        notification.setNotificationStatus("PENDING");
        notificationRepository.save(notification);
    }

    private List<RestaurantOrder> lockAndValidateBillOrders(
            Long restaurantId, Long tableId, List<Long> orderIds, boolean requireServed) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new ResourceNotFoundException("No orders were found for this bill");
        }
        List<RestaurantOrder> orders = new ArrayList<>();
        for (Long orderId : orderIds) {
            RestaurantOrder order = orderRepository.findByIdAndRestaurantIdForUpdate(orderId, restaurantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
            if (tableId != null && !order.getTable().getId().equals(tableId)) {
                throw new ResourceNotFoundException("Bill not found");
            }
            if (order.getOrderStatus() == OrderStatus.CANCELLED
                    || order.getApprovalStatus() == OrderApprovalStatus.PENDING
                    || order.getApprovalStatus() == OrderApprovalStatus.REJECTED
                    || order.getPaymentStatus() != PaymentStatus.PENDING
                    || requireServed && order.getOrderStatus() != OrderStatus.SERVED) {
                throw new ResourceConflictException(
                        requireServed
                                ? "Online payment is available only after every item has been served."
                                : "This bill is no longer available for payment.");
            }
            orders.add(order);
        }
        return orders;
    }

    private boolean isUnresolved(String status) {
        return OPEN_ATTEMPT_STATES.contains(status);
    }

    private String activeKey(Long restaurantId, String billKey) {
        return restaurantId + ":" + billKey;
    }

    private void markFailed(
            CustomerPaymentTransaction transaction, PaymentAttempt attempt, String errorCode) {
        transaction.setPaymentStatus("FAILED");
        attempt.setPaymentStatus("FAILED");
        attempt.setProviderState("FAILED");
        attempt.setErrorCode(errorCode);
        transactionRepository.save(transaction);
        attemptRepository.save(attempt);
        audit(transaction, attempt, "PHONEPE_CHECKOUT_REJECTED", "PHONEPE", null,
                Map.of("code", errorCode));
    }

    private void markUnknown(
            CustomerPaymentTransaction transaction, PaymentAttempt attempt, String errorCode) {
        transaction.setPaymentStatus("UNKNOWN");
        attempt.setPaymentStatus("UNKNOWN");
        attempt.setProviderState("UNKNOWN");
        attempt.setErrorCode(errorCode);
        transactionRepository.save(transaction);
        attemptRepository.save(attempt);
        audit(transaction, attempt, "PHONEPE_CHECKOUT_STATUS_UNKNOWN", "SYSTEM", null,
                Map.of("code", errorCode));
    }

    private void audit(
            CustomerPaymentTransaction transaction,
            PaymentAttempt attempt,
            String eventType,
            String actorType,
            Long actorId,
            Map<String, ?> details) {
        PaymentAuditLog entry = new PaymentAuditLog();
        entry.setPaymentTransaction(transaction);
        entry.setPaymentAttempt(attempt);
        entry.setEventType(eventType);
        entry.setActorType(actorType);
        entry.setActorId(actorId);
        try {
            entry.setDetailsJson(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize payment audit details", ex);
        }
        auditRepository.save(entry);
    }

    private Map<String, Object> paymentView(
            CustomerPaymentTransaction transaction, PaymentAttempt attempt, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionId", transaction.getId());
        result.put("merchantOrderId", attempt == null ? "" : attempt.getMerchantOrderId());
        result.put("amount", transaction.getAmount());
        result.put("currency", transaction.getCurrency());
        result.put("status", customerStatus(transaction.getPaymentStatus()));
        result.put("paymentStatus", transaction.getPaymentStatus());
        result.put("checkoutUrl", attempt == null ? "" : nullToEmpty(attempt.getCheckoutUrl()));
        result.put("paymentMethod", nullToEmpty(transaction.getPaymentMethod()));
        result.put("paymentReference", nullToEmpty(transaction.getPaymentReference()));
        result.put("paidAt", transaction.getPaidAt());
        result.put("message", message == null ? "" : message);
        return result;
    }

    private Map<String, Object> transactionView(CustomerPaymentTransaction transaction) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", transaction.getId());
        result.put("transactionId", transaction.getId());
        result.put("paymentStatus", transaction.getPaymentStatus());
        result.put("paymentMethod", transaction.getPaymentMethod());
        result.put("amount", transaction.getAmount());
        result.put("paidAt", transaction.getPaidAt());
        return result;
    }

    private String customerStatus(String status) {
        return switch (status) {
            case "PAID" -> "SUCCESSFUL";
            case "FAILED" -> "FAILED";
            case "CANCELLED" -> "FAILED";
            case "PROCESSING" -> "PROCESSING";
            case "UNKNOWN", "CREATED" -> "PENDING";
            default -> "PENDING";
        };
    }

    private Map<String, Object> assistanceView(OfflineAssistanceRequest assistance) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", assistance.getId());
        result.put("status", assistance.getRequestStatus());
        result.put("requestedAt", assistance.getRequestedAt());
        LocalDateTime cooldownStart = assistance.getLastRecalledAt() != null
                ? assistance.getLastRecalledAt()
                : assistance.getRequestedAt();
        result.put("cooldownUntil",
                cooldownStart.plus(WAITER_RECALL_COOLDOWN).toInstant(ZoneOffset.UTC));
        result.put("assignedAt", assistance.getAssignedAt());
        result.put("message", "ASSIGNED".equals(assistance.getRequestStatus())
                ? "A waiter will be with you shortly. Please wait."
                : "Your request has been sent to the restaurant.");
        return result;
    }

    private Map<String, Object> notificationView(RestaurantPaymentNotification notification) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notificationId", notification.getId());
        result.put("type", notification.getNotificationType());
        result.put("createdAt", notification.getCreatedAt());
        if ("CALL_WAITER".equals(notification.getNotificationType())) {
            OfflineAssistanceRequest assistance = notification.getAssistanceRequest();
            result.put("requestId", assistance.getId());
            result.put("customerName", nullToEmpty(assistance.getCustomerName()));
            result.put("customerMobile", nullToEmpty(assistance.getCustomerMobile()));
            result.put("orderNumber", assistance.getLatestOrder().getOrderNumber());
            result.put("billNumber", billRepository
                    .findByOrderOrderNumber(assistance.getLatestOrder().getOrderNumber())
                    .map(Bill::getBillNumber).orElse(""));
            result.put("tableNumber", assistance.getTable().getTableNumber());
            result.put("requestedAt", assistance.getRequestedAt());
            return result;
        }
        CustomerPaymentTransaction transaction = notification.getPaymentTransaction();
        RestaurantOrder latestOrder = transaction.getOrders().stream()
                .max(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .orElseThrow(() -> new ResourceNotFoundException("Payment notification has no order"));
        result.put("transactionId", transaction.getId());
        result.put("customerName", nullToEmpty(latestOrder.getCustomerName()));
        result.put("customerMobile", nullToEmpty(latestOrder.getCustomerMobile()));
        result.put("orderNumbers", transaction.getOrders().stream()
                .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt))
                .map(RestaurantOrder::getOrderNumber).toList());
        result.put("billNumbers", transaction.getOrders().stream()
                .map(order -> billRepository.findByOrderOrderNumber(order.getOrderNumber())
                        .map(Bill::getBillNumber).orElse(""))
                .filter(value -> !value.isBlank()).toList());
        result.put("tableNumber", transaction.getTable().getTableNumber());
        result.put("amount", transaction.getAmount());
        result.put("paymentMethod", transaction.getPaymentMethod());
        result.put("paymentReference", nullToEmpty(transaction.getPaymentReference()));
        result.put("paidAt", transaction.getPaidAt());
        result.put("paymentStatus", transaction.getPaymentStatus());
        return result;
    }

    private void authorizeCustomerTransaction(
            CustomerPaymentTransaction transaction, String qrToken, HttpServletRequest request) {
        RestaurantTable table = requireActiveTable(qrToken);
        if (!table.getId().equals(transaction.getTable().getId())
                || !table.getRestaurant().getId().equals(transaction.getRestaurant().getId())) {
            throw new ResourceNotFoundException("Payment transaction not found");
        }
        String currentKey = customerSessionService.getOrderSessionKey(
                request, table.getRestaurant().getId(), table.getId());
        boolean hasSessionAccess = Objects.equals(currentKey, transaction.getOrderSessionKey());
        boolean hasOrderAccess = !transaction.getOrders().isEmpty()
                && customerSessionService.getOrderNumbers(request, table.getRestaurant().getId())
                .contains(transaction.getOrders().iterator().next().getOrderNumber());
        if (!hasSessionAccess && !hasOrderAccess) {
            throw new ResourceNotFoundException("Payment transaction not found");
        }
    }

    private void authorizeCustomerAssistance(
            OfflineAssistanceRequest assistance, RestaurantTable table, HttpServletRequest request) {
        if (!table.getId().equals(assistance.getTable().getId())
                || !table.getRestaurant().getId().equals(assistance.getRestaurant().getId())) {
            throw new ResourceNotFoundException("Waiter request not found");
        }
        String currentKey = customerSessionService.getOrderSessionKey(
                request, table.getRestaurant().getId(), table.getId());
        boolean hasSessionAccess = Objects.equals(currentKey, assistance.getOrderSessionKey());
        boolean hasOrderAccess = customerSessionService
                .getOrderNumbers(request, table.getRestaurant().getId())
                .contains(assistance.getLatestOrder().getOrderNumber());
        if (!hasSessionAccess && !hasOrderAccess) {
            throw new ResourceNotFoundException("Waiter request not found");
        }
    }

    private RestaurantTable requireActiveTable(String qrToken) {
        return tableRepository.findByQrTokenAndActiveTrue(qrToken)
                .filter(table -> table.getRestaurant().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid QR code or inactive table"));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
