package com.texos.texos_backend.service;

import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.repository.AdminClientDataRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminClientDataService {
    private final AdminClientDataRepository orderRepository;
    private final RestaurantRepository restaurantRepository;

    public AdminClientDataService(AdminClientDataRepository orderRepository, RestaurantRepository restaurantRepository) {
        this.orderRepository = orderRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClientData(Long restaurantId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("A valid start and end date are required.");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));
        List<RestaurantOrder> orders = orderRepository.findClientDataOrders(
                restaurantId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        Map<String, Map<String, Object>> customers = new LinkedHashMap<>();
        long totalItems = 0;
        BigDecimal totalBills = BigDecimal.ZERO;
        for (RestaurantOrder order : orders) {
            String key;
            String name = order.getCustomerName();
            String mobile = order.getCustomerMobile();
            if (order.getCustomer() != null) {
                key = "customer:" + order.getCustomer().getId();
                if (name == null || name.isBlank()) name = order.getCustomer().getCustomerName();
                if (mobile == null || mobile.isBlank()) mobile = order.getCustomer().getMobileNumber();
            } else if (mobile != null && !mobile.isBlank()) {
                key = "mobile:" + mobile.trim();
            } else {
                key = "guest:" + (name == null || name.isBlank() ? "Unknown" : name.trim().toLowerCase());
            }
            final String displayName = name == null || name.isBlank() ? "Guest customer" : name;
            final String displayMobile = mobile == null ? "" : mobile;
            Map<String, Object> row = customers.computeIfAbsent(key, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("customerName", displayName);
                value.put("mobileNumber", displayMobile);
                value.put("orders", 0L);
                value.put("items", 0L);
                value.put("totalBillAmount", BigDecimal.ZERO);
                return value;
            });
            long itemCount = order.getItems().stream().mapToLong(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
            BigDecimal amount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
            row.put("orders", (Long) row.get("orders") + 1);
            row.put("items", (Long) row.get("items") + itemCount);
            row.put("totalBillAmount", ((BigDecimal) row.get("totalBillAmount")).add(amount));
            totalItems += itemCount;
            totalBills = totalBills.add(amount);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("restaurantId", restaurantId);
        response.put("restaurantName", restaurant.getRestaurantName());
        response.put("fromDate", from);
        response.put("toDate", to);
        response.put("totalCustomers", customers.size());
        response.put("totalOrders", orders.size());
        response.put("totalItems", totalItems);
        response.put("totalBillAmount", totalBills);
        response.put("customers", customers.values());
        return response;
    }
}
