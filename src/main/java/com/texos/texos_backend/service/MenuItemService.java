package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.MenuItemRequest;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;

import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceNotFoundException;

import com.texos.texos_backend.repository.MenuCategoryRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.OrderItemRepository;
import com.texos.texos_backend.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository itemRepository;
    private final MenuCategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public MenuItem createItem(
            Long restaurantId,
            Long categoryId,
            MenuItemRequest request
    ) {

        MenuCategory category = getCategory(restaurantId, categoryId);

        validateDisplayOrder(request.getDisplayOrder());

        MenuItem item = new MenuItem();
        item.setItemName(request.getItemName().trim());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setImageUrl(request.getImageUrl());
        item.setDisplayOrder(
            request.getDisplayOrder() == null ? 0 : request.getDisplayOrder()
        );
        item.setCategory(category);

        return itemRepository.save(item);
    }

    public List<MenuItem> getItemsByCategory(
            Long restaurantId,
            Long categoryId
    ) {

        getCategory(restaurantId, categoryId);

        return itemRepository
                .findAllByCategoryIdOrderByDisplayOrderAsc(categoryId);
    }

    public List<MenuItem> getAllItems(Long restaurantId) {

        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant not found");
        }

        return itemRepository.findAllByRestaurantId(restaurantId);
    }

    public MenuItem getItemById(
            Long restaurantId,
            Long categoryId,
            Long itemId
    ) {

        getCategory(restaurantId, categoryId);

        return itemRepository.findByIdAndCategoryId(itemId, categoryId)
                .orElseThrow(() ->
                    new ResourceNotFoundException("Menu item not found")
                );
    }

    @Transactional
    public MenuItem updateItem(
            Long restaurantId,
            Long categoryId,
            Long itemId,
            MenuItemRequest request
    ) {

        MenuItem item = getItemById(restaurantId, categoryId, itemId);

        validateDisplayOrder(request.getDisplayOrder());

        item.setItemName(request.getItemName().trim());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setImageUrl(request.getImageUrl());

        if (request.getDisplayOrder() != null) {
            item.setDisplayOrder(request.getDisplayOrder());
        }

        return itemRepository.save(item);
    }

    @Transactional
    public void deleteItem(
            Long restaurantId,
            Long categoryId,
            Long itemId
    ) {

        MenuItem item = getItemById(restaurantId, categoryId, itemId);
        orderItemRepository.findAllByMenuItem_Id(itemId).forEach(orderItem -> {
            orderItem.setMenuItem(null);
        });
        orderItemRepository.flush();
        itemRepository.delete(item);
    }

    @Transactional
    public MenuItem updateItemAvailability(
            Long restaurantId,
            Long categoryId,
            Long itemId,
            boolean available
    ) {

        MenuItem item = getItemById(restaurantId, categoryId, itemId);
        item.setAvailable(available);

        return itemRepository.save(item);
    }

    private MenuCategory getCategory(Long restaurantId, Long categoryId) {

        return categoryRepository.findByIdAndRestaurantId(
                categoryId, restaurantId
        ).orElseThrow(() ->
            new ResourceNotFoundException("Menu category not found")
        );
    }

    private void validateDisplayOrder(Integer displayOrder) {

        if (displayOrder != null && displayOrder < 0) {
            throw new InvalidRequestException(
                "Display order cannot be negative"
            );
        }
    }
}
