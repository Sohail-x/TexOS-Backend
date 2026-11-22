package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.MenuCategoryRequest;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.Restaurant;

import com.texos.texos_backend.exception.DuplicateResourceException;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceConflictException;
import com.texos.texos_backend.exception.ResourceNotFoundException;

import com.texos.texos_backend.repository.MenuCategoryRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuCategoryService {

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public MenuCategory createCategory(
            Long restaurantId,
            MenuCategoryRequest request
    ) {

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() ->
                    new ResourceNotFoundException("Restaurant not found")
                );

        String name = request.getCategoryName().trim();

        if (categoryRepository.existsByRestaurantIdAndCategoryNameIgnoreCase(
                restaurantId, name)) {
            throw new DuplicateResourceException(
                "Category already exists in this restaurant"
            );
        }

        MenuCategory category = new MenuCategory();
        category.setCategoryName(name);
        category.setDescription(request.getDescription());
        category.setDisplayOrder(
            request.getDisplayOrder() == null ? 0 : request.getDisplayOrder()
        );
        category.setRestaurant(restaurant);

        return categoryRepository.save(category);
    }

    public List<MenuCategory> getAllCategories(Long restaurantId) {

        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant not found");
        }

        return categoryRepository
                .findAllByRestaurantIdOrderByDisplayOrderAsc(restaurantId);
    }

    public MenuCategory getCategoryById(Long restaurantId, Long categoryId) {

        return categoryRepository.findByIdAndRestaurantId(
                categoryId, restaurantId
        ).orElseThrow(() ->
            new ResourceNotFoundException("Menu category not found")
        );
    }

    @Transactional
    public MenuCategory updateCategory(
            Long restaurantId,
            Long categoryId,
            MenuCategoryRequest request
    ) {

        MenuCategory category = getCategoryById(restaurantId, categoryId);

        String name = request.getCategoryName().trim();

        if (categoryRepository
                .existsByRestaurantIdAndCategoryNameIgnoreCaseAndIdNot(
                    restaurantId, name, categoryId
                )) {
            throw new DuplicateResourceException(
                "Category name already exists"
            );
        }

        if (request.getDisplayOrder() != null &&
                request.getDisplayOrder() < 0) {
            throw new InvalidRequestException(
                "Display order cannot be negative"
            );
        }

        category.setCategoryName(name);
        category.setDescription(request.getDescription());

        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long restaurantId, Long categoryId) {

        MenuCategory category = getCategoryById(restaurantId, categoryId);

        if (itemRepository.existsByCategoryId(categoryId)) {
            throw new ResourceConflictException(
                "Cannot delete category because it contains menu items"
            );
        }

        categoryRepository.delete(category);
    }

    @Transactional
    public MenuCategory updateCategoryStatus(
            Long restaurantId,
            Long categoryId,
            boolean active
    ) {

        MenuCategory category = getCategoryById(restaurantId, categoryId);
        category.setActive(active);

        return categoryRepository.save(category);
    }
}
