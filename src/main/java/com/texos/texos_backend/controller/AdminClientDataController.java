package com.texos.texos_backend.controller;

import com.texos.texos_backend.service.AdminClientDataService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/client-data")
public class AdminClientDataController {
    private final AdminClientDataService service;

    public AdminClientDataController(AdminClientDataService service) {
        this.service = service;
    }

    @GetMapping("/restaurants/{restaurantId}")
    public Map<String, Object> getRestaurantClientData(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getClientData(restaurantId, from, to);
    }
}
