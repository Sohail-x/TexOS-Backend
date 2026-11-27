
package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.PublicMenuCategoryResponse;
import com.texos.texos_backend.dto.PublicMenuImage;
import com.texos.texos_backend.dto.PublicMenuItemResponse;
import com.texos.texos_backend.dto.PublicMenuItemView;
import com.texos.texos_backend.dto.PublicMenuResponse;

import com.texos.texos_backend.entity.LoyaltyProgram;
import com.texos.texos_backend.entity.MenuCategory;

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
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerMenuService {

    private static final Pattern INLINE_IMAGE_PATTERN = Pattern.compile(
            "^data:(image/(?:png|jpeg|webp|gif));base64,([A-Za-z0-9+/]+={0,2})$");

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

        Map<Long, List<PublicMenuItemView>> itemsByCategory = new HashMap<>();
        if (!categories.isEmpty()) {
            List<Long> categoryIds = categories.stream()
                    .map(MenuCategory::getId)
                    .toList();
            for (PublicMenuItemView item : itemRepository
                    .findAllPublicAvailableByCategoryIdInOrderByDisplayOrderAsc(categoryIds)) {
                itemsByCategory.computeIfAbsent(
                        item.categoryId(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }

        List<PublicMenuCategoryResponse> categoryResponses =
                categories.stream()
                .map(category -> mapCategoryToResponse(
                        category, itemsByCategory.getOrDefault(category.getId(), List.of()), qrToken))
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

    @Transactional(readOnly = true)
    public PublicMenuImage getCustomerMenuImage(String qrToken, Long itemId) {
        String imageUrl = itemRepository
                .findPublicImageDataByIdAndQrToken(itemId, qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Menu image not found"));
        Matcher imageMatch = INLINE_IMAGE_PATTERN.matcher(imageUrl);
        if (!imageMatch.matches()) {
            throw new ResourceNotFoundException("Menu image not found");
        }

        return new PublicMenuImage(
                imageMatch.group(1),
                Base64.getDecoder().decode(imageMatch.group(2)));
    }

    private PublicMenuCategoryResponse mapCategoryToResponse(
            MenuCategory category,
            List<PublicMenuItemView> items,
            String qrToken
    ) {

        List<PublicMenuItemResponse> itemResponses = items.stream()
                .map(item -> mapItemToResponse(item, qrToken))
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

    private PublicMenuItemResponse mapItemToResponse(PublicMenuItemView item, String qrToken) {

        PublicMenuItemResponse response =
                new PublicMenuItemResponse();

        response.setId(item.id());
        response.setItemName(item.itemName());
        response.setDescription(item.description());
        response.setPrice(item.price());
        response.setImageUrl(item.inlineImage()
                ? "/api/public/menu/" + qrToken + "/items/" + item.id() + "/image"
                : item.imageUrl());

        return response;
    }
}
