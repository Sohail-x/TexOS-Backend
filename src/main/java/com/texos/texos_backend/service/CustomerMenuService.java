
package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.PublicMenuCategoryResponse;
import com.texos.texos_backend.dto.PublicMenuItemResponse;
import com.texos.texos_backend.dto.PublicMenuResponse;

import com.texos.texos_backend.entity.LoyaltyProgram;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;

import com.texos.texos_backend.exception.ResourceNotFoundException;

import com.texos.texos_backend.repository.LoyaltyProgramRepository;
import com.texos.texos_backend.repository.MenuCategoryRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerMenuService {

    private final RestaurantTableRepository tableRepository;
    private final LoyaltyProgramRepository loyaltyProgramRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;

    @Transactional(readOnly = true)
    public PublicMenuResponse getCustomerMenu(String qrToken) {

        var menuContext = tableRepository
                .findCustomerMenuContextByQrToken(qrToken)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "Invalid QR code or inactive table"
                    )
                );

        if (!Boolean.TRUE.equals(menuContext.getRestaurantActive())) {
            throw new ResourceNotFoundException(
                "Restaurant is currently unavailable"
            );
        }

        Long restaurantId = menuContext.getRestaurantId();

        List<MenuCategory> categories = categoryRepository
                .findAllByRestaurantIdAndActiveTrueOrderByDisplayOrderAsc(
                    restaurantId
                );

        Map<Long, List<MenuItem>> itemsByCategory = new HashMap<>();
        if (!categories.isEmpty()) {
            List<Long> categoryIds = categories.stream()
                    .map(MenuCategory::getId)
                    .toList();
            for (MenuItem item : itemRepository
                    .findAllAvailableByCategoryIdInOrderByDisplayOrderAsc(categoryIds)) {
                itemsByCategory.computeIfAbsent(
                        item.getCategory().getId(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }

        List<PublicMenuCategoryResponse> categoryResponses =
                categories.stream()
                .map(category -> mapCategoryToResponse(
                        category, itemsByCategory.getOrDefault(category.getId(), List.of())))
                .toList();

        PublicMenuResponse response = new PublicMenuResponse();

        response.setRestaurantName(menuContext.getRestaurantName());
        response.setRestaurantAddress(menuContext.getRestaurantAddress());
        response.setRestaurantPhone(menuContext.getRestaurantPhone());
        String hours = menuContext.getOpeningHours();
        response.setOpeningHours(
            (hours != null && !hours.isBlank()) ? hours : "Open 11am-10pm"
        );
        response.setGoogleMapsUrl(menuContext.getGoogleMapsUrl());
        response.setRewardsEnabled(loyaltyProgramRepository.findByRestaurant_Id(restaurantId)
                .map(LoyaltyProgram::isEnabled).orElse(false));

        response.setTableNumber(menuContext.getTableNumber());
        response.setCategories(categoryResponses);

        return response;
    }

    private PublicMenuCategoryResponse mapCategoryToResponse(
            MenuCategory category,
            List<MenuItem> items
    ) {

        List<PublicMenuItemResponse> itemResponses = items.stream()
                .map(this::mapItemToResponse)
                .toList();

        PublicMenuCategoryResponse response =
                new PublicMenuCategoryResponse();

        response.setId(category.getId());
        response.setCategoryName(category.getCategoryName());
        response.setDescription(category.getDescription());
        response.setDisplayOrder(category.getDisplayOrder());
        response.setItems(itemResponses);

        return response;
    }

    private PublicMenuItemResponse mapItemToResponse(MenuItem item) {

        PublicMenuItemResponse response =
                new PublicMenuItemResponse();

        response.setId(item.getId());
        response.setItemName(item.getItemName());
        response.setDescription(item.getDescription());
        response.setPrice(item.getPrice());
        response.setImageUrl(item.getImageUrl());

        return response;
    }
}
