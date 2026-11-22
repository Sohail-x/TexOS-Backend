package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.CustomerResponse;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.repository.CustomerOrderMetrics;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;

    /**
     * Creates or reuses a restaurant-scoped customer contact.
     * The mobile number is unverified and must never be used as proof of identity.
     */
    @Transactional
    public Customer getOrCreateCustomer(
            Restaurant restaurant,
            String customerName,
            String mobileNumber,
            String email
    ) {
        String cleanName = customerName.trim();
        String cleanMobile = normalizeMobile(mobileNumber);
        String cleanEmail = (email == null || email.isBlank())
                ? null
                : email.trim().toLowerCase(Locale.ROOT);

        return customerRepository
                .findByRestaurant_IdAndMobileNumber(restaurant.getId(), cleanMobile)
                .map(existing -> {
                    // Keep the existing contact record. Do not overwrite its details
                    // using an unverified phone number supplied by another visitor.
                    return existing;
                })
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setRestaurant(restaurant);
                    customer.setCustomerName(cleanName);
                    customer.setMobileNumber(cleanMobile);
                    customer.setEmail(cleanEmail);
                    customer.setMobileVerified(false);
                    return customerRepository.save(customer);
                });
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomers(Long restaurantId) {
        ensureRestaurantExists(restaurantId);
        Map<Long, CustomerOrderMetrics> metricsByCustomer =
                customerRepository.findOrderMetricsByRestaurantId(
                                restaurantId, PaymentStatus.PAID, OrderStatus.CANCELLED,
                                OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED)
                        .stream()
                        .collect(Collectors.toMap(
                                CustomerOrderMetrics::getCustomerId,
                                Function.identity()));

        if (metricsByCustomer.isEmpty()) {
            return List.of();
        }

        return customerRepository
                .findAllByRestaurant_IdOrderByCreatedAtDesc(restaurantId)
                .stream()
                .filter(customer -> metricsByCustomer.containsKey(customer.getId()))
                .map(customer -> toResponse(
                        customer, metricsByCustomer.get(customer.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long restaurantId, Long customerId) {
        ensureRestaurantExists(restaurantId);
        Customer customer = customerRepository
                .findByIdAndRestaurant_Id(customerId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        CustomerOrderMetrics metrics = customerRepository
                .findOrderMetricsByRestaurantIdAndCustomerId(
                        restaurantId, customerId, PaymentStatus.PAID, OrderStatus.CANCELLED,
                        OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return toResponse(customer, metrics);
    }

    private void ensureRestaurantExists(Long restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant not found");
        }
    }

    private String normalizeMobile(String mobileNumber) {
        String normalized = mobileNumber.trim().replaceAll("[\\s()-]", "");
        if (!normalized.matches("^\\+?[0-9]{7,15}$")) {
            throw new InvalidRequestException("Invalid mobile number format");
        }
        return normalized;
    }

    private CustomerResponse toResponse(
            Customer customer,
            CustomerOrderMetrics metrics
    ) {
        CustomerResponse response = new CustomerResponse();
        response.setCustomerId(customer.getId());
        response.setRestaurantId(customer.getRestaurant().getId());
        response.setCustomerName(customer.getCustomerName());
        response.setMobileNumber(customer.getMobileNumber());
        response.setEmail(customer.getEmail());
        response.setMobileVerified(customer.isMobileVerified());
        response.setCreatedAt(customer.getCreatedAt());
        response.setTotalOrders(metrics.getTotalOrders());
        response.setTotalSpent(metrics.getTotalSpent());
        return response;
    }
}
