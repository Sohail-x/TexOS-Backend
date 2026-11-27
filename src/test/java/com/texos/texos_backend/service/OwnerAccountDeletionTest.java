package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.repository.MenuCategoryRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class OwnerAccountDeletionTest {

    @Test
    void deletionRevokesOwnerTokensAndSchedulesProfileAnonymization() {
        OwnerRepository owners = mock(OwnerRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        MenuCategoryRepository categories = mock(MenuCategoryRepository.class);
        MenuItemRepository items = mock(MenuItemRepository.class);
        OwnerService service = new OwnerService(
                owners, passwordEncoder, restaurants, categories, items);

        Owner owner = new Owner();
        owner.setId(17L);
        owner.setFullName("Asha Rao");
        owner.setEmail("asha@example.test");
        owner.setPhone("9876543210");
        owner.setPasswordHash("old-password-hash");
        owner.setGoogleSub("google-sub");
        owner.setAvatarImage("data:image/png;base64,private");
        owner.setTokenVersion(4);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(28L);
        restaurant.setRestaurantName("Asha's Kitchen");
        restaurant.setAddress("Private address");
        restaurant.setPhone("9876543210");
        restaurant.setEmail("owner@example.test");
        restaurant.setUpiId("owner@upi");
        MenuCategory category = new MenuCategory();
        category.setId(39L);
        category.setCategoryName("Meals");
        MenuItem item = new MenuItem();
        item.setId(40L);
        item.setItemName("Private menu item");
        item.setDescription("Menu description");
        item.setPrice(new BigDecimal("125.00"));
        item.setImageUrl("data:image/png;base64,private");
        item.setAvailable(true);

        when(owners.findByIdForUpdate(17L)).thenReturn(Optional.of(owner));
        when(restaurants.findAllByOwnerId(17L)).thenReturn(List.of(restaurant));
        when(categories.findAllByRestaurantId(28L)).thenReturn(List.of(category));
        when(items.findAllByRestaurantId(28L)).thenReturn(List.of(item));
        service.deleteOwnerAccount(17L);

        assertFalse(owner.isActive());
        assertEquals(5, owner.getTokenVersion());
        assertEquals("Asha Rao", owner.getFullName());
        assertEquals("asha@example.test", owner.getEmail());
        assertEquals("9876543210", owner.getPhone());
        assertEquals("google-sub", owner.getGoogleSub());
        assertEquals("data:image/png;base64,private", owner.getAvatarImage());
        assertEquals(null, owner.getPasswordHash());
        assertEquals(null, owner.getAnonymizedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(owner.getDeletedAt());
        assertFalse(restaurant.isActive());
        org.junit.jupiter.api.Assertions.assertNotNull(restaurant.getDeletedAt());
        assertEquals(null, restaurant.getAddress());
        assertEquals(null, restaurant.getPhone());
        assertEquals(null, restaurant.getEmail());
        assertEquals(null, restaurant.getUpiId());
        assertFalse(category.isActive());
        assertEquals("Deleted category 39", category.getCategoryName());
        assertEquals("Deleted menu item 40", item.getItemName());
        assertEquals(null, item.getImageUrl());
        assertEquals(BigDecimal.ZERO, item.getPrice());
        assertFalse(item.isAvailable());
    }
}
