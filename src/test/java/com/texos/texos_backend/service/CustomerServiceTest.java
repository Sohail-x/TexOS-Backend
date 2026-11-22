package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.CustomerOrderMetrics;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;

class CustomerServiceTest {

    @Test
    void listsOnlyCustomersWithRestaurantOrdersAndIncludesMetrics() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        CustomerService customerService =
                new CustomerService(customerRepository, restaurantRepository);
        Customer orderedCustomer = customer(10L, 3L);
        Customer noOrdersCustomer = customer(11L, 3L);

        when(restaurantRepository.existsById(3L)).thenReturn(true);
        when(customerRepository.findOrderMetricsByRestaurantId(
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(metrics(10L, 2L, "125.50")));
        when(customerRepository.findAllByRestaurant_IdOrderByCreatedAtDesc(3L))
                .thenReturn(List.of(orderedCustomer, noOrdersCustomer));

        var result = customerService.getCustomers(3L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getCustomerId());
        assertEquals(2L, result.get(0).getTotalOrders());
        assertEquals(new BigDecimal("125.50"), result.get(0).getTotalSpent());
    }

    @Test
    void rejectsCustomerDetailsForCustomerWithoutOrders() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        CustomerService customerService =
                new CustomerService(customerRepository, restaurantRepository);

        when(restaurantRepository.existsById(3L)).thenReturn(true);
        when(customerRepository.findByIdAndRestaurant_Id(10L, 3L))
                .thenReturn(Optional.of(customer(10L, 3L)));
        when(customerRepository.findOrderMetricsByRestaurantIdAndCustomerId(
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> customerService.getCustomer(3L, 10L));
    }

    private Customer customer(Long id, Long restaurantId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        Customer customer = new Customer();
        customer.setId(id);
        customer.setRestaurant(restaurant);
        return customer;
    }

    private CustomerOrderMetrics metrics(
            Long customerId,
            Long totalOrders,
            String totalSpent
    ) {
        return new CustomerOrderMetrics() {
            @Override
            public Long getCustomerId() {
                return customerId;
            }

            @Override
            public Long getTotalOrders() {
                return totalOrders;
            }

            @Override
            public BigDecimal getTotalSpent() {
                return new BigDecimal(totalSpent);
            }
        };
    }
}
