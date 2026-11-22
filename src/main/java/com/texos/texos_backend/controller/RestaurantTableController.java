package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.RestaurantTableRequest;
import com.texos.texos_backend.dto.RestaurantTableResponse;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.service.RestaurantTableService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Base64;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/tables")
@RequiredArgsConstructor
public class RestaurantTableController {

    private final RestaurantTableService tableService;

    // Create a new table
    @PostMapping
    public ResponseEntity<RestaurantTableResponse> createTable(
            @PathVariable Long restaurantId,
            @Valid @RequestBody RestaurantTableRequest request) {

        RestaurantTable table = tableService.createTable(
                restaurantId,
                request.getTableNumber()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(table));
    }

    // Get all tables of a restaurant
    @GetMapping
    public ResponseEntity<List<RestaurantTableResponse>> getAllTables(
            @PathVariable Long restaurantId) {

        List<RestaurantTableResponse> response =
                tableService.getAllTables(restaurantId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(response);
    }

    // Get a specific table
    @GetMapping("/{tableId}")
    public ResponseEntity<RestaurantTableResponse> getTableById(
            @PathVariable Long restaurantId,
            @PathVariable Long tableId) {

        RestaurantTable table =
                tableService.getTableById(restaurantId, tableId);

        return ResponseEntity.ok(toResponse(table));
    }

    // Activate or deactivate a table
    @PatchMapping("/{tableId}/status")
    public ResponseEntity<RestaurantTableResponse> updateTableStatus(
            @PathVariable Long restaurantId,
            @PathVariable Long tableId,
            @RequestParam boolean active) {

        RestaurantTable table = tableService.updateTableStatus(
                restaurantId,
                tableId,
                active
        );

        return ResponseEntity.ok(toResponse(table));
    }

    private RestaurantTableResponse toResponse(RestaurantTable table) {

        RestaurantTableResponse response =
                new RestaurantTableResponse();

        response.setTableId(table.getId());
        response.setTableNumber(table.getTableNumber());
        response.setQrToken(table.getQrToken());
        if (table.getQrImage() != null && table.getQrImage().length > 0) {
            response.setQrImageBase64(Base64.getEncoder().encodeToString(table.getQrImage()));
        }
        response.setActive(table.isActive());
        response.setRestaurantId(table.getRestaurant().getId());
        response.setCreatedAt(table.getCreatedAt());

        return response;
    }
}
