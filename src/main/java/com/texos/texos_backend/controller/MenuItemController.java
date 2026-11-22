package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.MenuItemRequest;
import com.texos.texos_backend.dto.MenuItemResponse;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.service.MenuItemService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService itemService;

    @PostMapping("/categories/{categoryId}/items")
    public ResponseEntity<MenuItemResponse> createItem(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId,
            @Valid @RequestBody MenuItemRequest request
    ) {

        MenuItem item = itemService.createItem(
                restaurantId, categoryId, request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapToResponse(item));
    }

    @GetMapping("/categories/{categoryId}/items")
    public ResponseEntity<List<MenuItemResponse>> getItemsByCategory(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId
    ) {

        List<MenuItemResponse> response = itemService
                .getItemsByCategory(restaurantId, categoryId)
                .stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/items")
    public ResponseEntity<List<MenuItemResponse>> getAllItems(
            @PathVariable Long restaurantId
    ) {

        List<MenuItemResponse> response = itemService
                .getAllItems(restaurantId)
                .stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories/{categoryId}/items/{itemId}")
    public ResponseEntity<MenuItemResponse> getItemById(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId,
            @PathVariable Long itemId
    ) {

        MenuItem item = itemService.getItemById(
                restaurantId, categoryId, itemId
        );

        return ResponseEntity.ok(mapToResponse(item));
    }

    @PutMapping("/categories/{categoryId}/items/{itemId}")
    public ResponseEntity<MenuItemResponse> updateItem(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId,
            @PathVariable Long itemId,
            @Valid @RequestBody MenuItemRequest request
    ) {

        MenuItem item = itemService.updateItem(
                restaurantId, categoryId, itemId, request
        );

        return ResponseEntity.ok(mapToResponse(item));
    }

    @DeleteMapping("/categories/{categoryId}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId,
            @PathVariable Long itemId
    ) {

        itemService.deleteItem(restaurantId, categoryId, itemId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/categories/{categoryId}/items/{itemId}/availability")
    public ResponseEntity<MenuItemResponse> updateAvailability(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId,
            @PathVariable Long itemId,
            @RequestParam boolean available
    ) {

        MenuItem item = itemService.updateItemAvailability(
                restaurantId, categoryId, itemId, available
        );

        return ResponseEntity.ok(mapToResponse(item));
    }

    private MenuItemResponse mapToResponse(MenuItem item) {

        MenuItemResponse response = new MenuItemResponse();

        response.setId(item.getId());
        response.setItemName(item.getItemName());
        response.setDescription(item.getDescription());
        response.setPrice(item.getPrice());
        response.setImageUrl(item.getImageUrl());
        response.setAvailable(item.isAvailable());
        response.setDisplayOrder(item.getDisplayOrder());

        response.setCategoryId(item.getCategory().getId());
        response.setCategoryName(item.getCategory().getCategoryName());
        response.setRestaurantId(item.getCategory().getRestaurant().getId());

        return response;
    }
}
