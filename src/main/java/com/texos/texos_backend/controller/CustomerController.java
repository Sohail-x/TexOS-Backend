package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.CustomerResponse;
import com.texos.texos_backend.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public List<CustomerResponse> getCustomers(@PathVariable Long restaurantId) {
        return customerService.getCustomers(restaurantId);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse getCustomer(
            @PathVariable Long restaurantId,
            @PathVariable Long customerId
    ) {
        return customerService.getCustomer(restaurantId, customerId);
    }
}
