package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.entity.LoyaltyProgram;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.repository.CustomerMenuContext;
import com.texos.texos_backend.repository.LoyaltyProgramRepository;
import com.texos.texos_backend.repository.MenuCategoryRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerMenuServiceTest {
    private static final String QR_TOKEN = "table-qr";
    private static final long RESTAURANT_ID = 4L;

    @Test
    void loadsAllActiveCategoryItemsInOneBulkQueryAndPreservesGrouping() {
        TestContext context = new TestContext();
        MenuCategory mains = category(11L, "Mains");
        MenuCategory drinks = category(12L, "Drinks");
        MenuItem secondMain = item(102L, mains, "Rice", 2);
        MenuItem firstMain = item(101L, mains, "Soup", 1);
        MenuItem drink = item(103L, drinks, "Tea", 1);
        when(context.categories.findAllByRestaurantIdAndActiveTrueOrderByDisplayOrderAsc(
                RESTAURANT_ID)).thenReturn(List.of(mains, drinks));
        when(context.items.findAllAvailableByCategoryIdInOrderByDisplayOrderAsc(
                List.of(11L, 12L))).thenReturn(List.of(firstMain, drink, secondMain));

        var result = context.service.getCustomerMenu(QR_TOKEN);

        assertEquals(2, result.getCategories().size());
        assertEquals("Mains", result.getCategories().get(0).getCategoryName());
        assertEquals(List.of("Soup", "Rice"), result.getCategories().get(0).getItems()
                .stream().map(item -> item.getItemName()).toList());
        assertEquals(List.of("Tea"), result.getCategories().get(1).getItems()
                .stream().map(item -> item.getItemName()).toList());
        assertEquals("T1", result.getTableNumber());
        assertTrue(result.isRewardsEnabled());
        verify(context.items).findAllAvailableByCategoryIdInOrderByDisplayOrderAsc(
                List.of(11L, 12L));
        verify(context.items, never())
                .findAllByCategoryIdAndAvailableTrueOrderByDisplayOrderAsc(11L);
    }

    @Test
    void skipsBulkItemQueryWhenRestaurantHasNoActiveCategories() {
        TestContext context = new TestContext();
        when(context.categories.findAllByRestaurantIdAndActiveTrueOrderByDisplayOrderAsc(
                RESTAURANT_ID)).thenReturn(List.of());
        when(context.loyaltyPrograms.findByRestaurant_Id(RESTAURANT_ID))
                .thenReturn(Optional.empty());

        var result = context.service.getCustomerMenu(QR_TOKEN);

        assertTrue(result.getCategories().isEmpty());
        assertFalse(result.isRewardsEnabled());
        verify(context.items, never())
                .findAllAvailableByCategoryIdInOrderByDisplayOrderAsc(org.mockito.ArgumentMatchers.anyList());
    }

    private static MenuCategory category(Long id, String name) {
        MenuCategory category = new MenuCategory();
        category.setId(id);
        category.setCategoryName(name);
        return category;
    }

    private static MenuItem item(Long id, MenuCategory category, String name, int order) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setCategory(category);
        item.setItemName(name);
        item.setPrice(BigDecimal.TEN);
        item.setAvailable(true);
        item.setDisplayOrder(order);
        return item;
    }

    private static class TestContext {
        private final RestaurantTableRepository tables = mock(RestaurantTableRepository.class);
        private final LoyaltyProgramRepository loyaltyPrograms = mock(LoyaltyProgramRepository.class);
        private final MenuCategoryRepository categories = mock(MenuCategoryRepository.class);
        private final MenuItemRepository items = mock(MenuItemRepository.class);
        private final CustomerMenuService service =
                new CustomerMenuService(tables, loyaltyPrograms, categories, items);
        private final CustomerMenuContext menuContext = mock(CustomerMenuContext.class);

        private TestContext() {
            when(tables.findCustomerMenuContextByQrToken(QR_TOKEN))
                    .thenReturn(Optional.of(menuContext));
            when(menuContext.getRestaurantId()).thenReturn(RESTAURANT_ID);
            when(menuContext.getRestaurantName()).thenReturn("TexOS Cafe");
            when(menuContext.getRestaurantAddress()).thenReturn("Main street");
            when(menuContext.getRestaurantPhone()).thenReturn("1234567890");
            when(menuContext.getOpeningHours()).thenReturn("10am-10pm");
            when(menuContext.getGoogleMapsUrl()).thenReturn("https://maps.example");
            when(menuContext.getRestaurantActive()).thenReturn(true);
            when(menuContext.getTableNumber()).thenReturn("T1");
            LoyaltyProgram program = new LoyaltyProgram();
            program.setEnabled(true);
            when(loyaltyPrograms.findByRestaurant_Id(RESTAURANT_ID))
                    .thenReturn(Optional.of(program));
        }
    }
}
