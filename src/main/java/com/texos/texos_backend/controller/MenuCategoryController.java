package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.MenuCategoryRequest;
import com.texos.texos_backend.dto.MenuCategoryResponse;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.service.MenuCategoryService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/categories")
@RequiredArgsConstructor
public class MenuCategoryController {

    private final MenuCategoryService categoryService;

    @PostMapping
    public ResponseEntity<MenuCategoryResponse> createCategory(
            @PathVariable Long restaurantId,
            @Valid @RequestBody MenuCategoryRequest request
    ) {

        MenuCategory category = categoryService.createCategory(
                restaurantId, request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapToResponse(category));
    }

    @GetMapping
    public ResponseEntity<List<MenuCategoryResponse>> getAllCategories(
            @PathVariable Long restaurantId
    ) {

        List<MenuCategoryResponse> response = categoryService
                .getAllCategories(restaurantId)
                .stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<MenuCategoryResponse> getCategoryById(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId
    ) {

        MenuCategory category = categoryService.getCategoryById(
                restaurantId, categoryId
        );

        return ResponseEntity.ok(mapToResponse(category));
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<MenuCategoryResponse> updateCategory(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId,
            @Valid @RequestBody MenuCategoryRequest request
    ) {

        MenuCategory category = categoryService.updateCategory(
                restaurantId, categoryId, request
        );

        return ResponseEntity.ok(mapToResponse(category));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId
    ) {

        categoryService.deleteCategory(restaurantId, categoryId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{categoryId}/status")
    public ResponseEntity<MenuCategoryResponse> updateCategoryStatus(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId,
            @RequestParam boolean active
    ) {

        MenuCategory category = categoryService.updateCategoryStatus(
                restaurantId, categoryId, active
        );

        return ResponseEntity.ok(mapToResponse(category));
    }

    private MenuCategoryResponse mapToResponse(MenuCategory category) {

        MenuCategoryResponse response = new MenuCategoryResponse();

        response.setId(category.getId());
        response.setCategoryName(category.getCategoryName());
        response.setDescription(category.getDescription());
        response.setActive(category.isActive());
        response.setDisplayOrder(category.getDisplayOrder());
        response.setRestaurantId(category.getRestaurant().getId());

        return response;
    }
}
