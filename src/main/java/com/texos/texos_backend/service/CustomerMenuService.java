
package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.PublicMenuCategoryResponse;
import com.texos.texos_backend.dto.PublicMenuItemResponse;
import com.texos.texos_backend.dto.PublicMenuResponse;

import com.texos.texos_backend.entity.LoyaltyProgram;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.entity.RestaurantTable;

import com.texos.texos_backend.exception.ResourceNotFoundException;

import com.texos.texos_backend.repository.LoyaltyProgramRepository;
import com.texos.texos_backend.repository.MenuCategoryRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerMenuService {

    private final RestaurantTableRepository tableRepository;
    private final LoyaltyProgramRepository loyaltyProgramRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;

    @Transactional(readOnly = true)
    public PublicMenuResponse getCustomerMenu(String qrToken) {

        RestaurantTable table = tableRepository
                .findByQrTokenAndActiveTrue(qrToken)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "Invalid QR code or inactive table"
                    )
                );

        if (!table.getRestaurant().isActive()) {
            throw new ResourceNotFoundException(
                "Restaurant is currently unavailable"
            );
        }

        Long restaurantId = table.getRestaurant().getId();

        List<MenuCategory> categories = categoryRepository
                .findAllByRestaurantIdAndActiveTrueOrderByDisplayOrderAsc(
                    restaurantId
                );

        List<PublicMenuCategoryResponse> categoryResponses =
                categories.stream()
                .map(this::mapCategoryToResponse)
                .toList();

        PublicMenuResponse response = new PublicMenuResponse();

        response.setRestaurantName(
            table.getRestaurant().getRestaurantName()
        );

        response.setRestaurantAddress(
            table.getRestaurant().getAddress()
        );

        response.setRestaurantPhone(
            table.getRestaurant().getPhone()
        );
        String hours = table.getRestaurant().getOpeningHours();
        response.setOpeningHours(
            (hours != null && !hours.isBlank()) ? hours : "Open 11am-10pm"
        );
        response.setGoogleMapsUrl(
            table.getRestaurant().getGoogleMapsUrl()
        );
        response.setRewardsEnabled(loyaltyProgramRepository.findByRestaurant_Id(table.getRestaurant().getId())
                .map(LoyaltyProgram::isEnabled).orElse(false));

        response.setTableNumber(table.getTableNumber());

        response.setCategories(categoryResponses);

        return response;
    }

    private PublicMenuCategoryResponse mapCategoryToResponse(
            MenuCategory category
    ) {

        List<MenuItem> items = itemRepository
                .findAllByCategoryIdAndAvailableTrueOrderByDisplayOrderAsc(
                    category.getId()
                );

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
