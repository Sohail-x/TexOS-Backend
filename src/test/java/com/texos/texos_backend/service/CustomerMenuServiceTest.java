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
import com.texos.texos_backend.dto.PublicMenuItemView;
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
        PublicMenuItemView firstMain =
                new PublicMenuItemView(11L, 101L, "Soup", null, BigDecimal.TEN, null, false);
        PublicMenuItemView secondMain =
                new PublicMenuItemView(11L, 102L, "Rice", null, BigDecimal.TEN, null, false);
        PublicMenuItemView drink =
                new PublicMenuItemView(12L, 103L, "Tea", null, BigDecimal.TEN, null, false);
        when(context.categories.findAllByRestaurantIdAndActiveTrueOrderByDisplayOrderAsc(
                RESTAURANT_ID)).thenReturn(List.of(mains, drinks));
        when(context.items.findAllPublicAvailableByCategoryIdInOrderByDisplayOrderAsc(
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
        verify(context.items).findAllPublicAvailableByCategoryIdInOrderByDisplayOrderAsc(
                List.of(11L, 12L));
        verify(context.items, never())
                .findAllByCategoryIdAndAvailableTrueOrderByDisplayOrderAsc(11L);
    }

    @Test
    void servesInlineMenuImagesSeparatelyFromTheMenuResponse() {
        TestContext context = new TestContext();
        MenuCategory category = category(11L, "Mains");
        PublicMenuItemView itemView =
                new PublicMenuItemView(11L, 101L, "Soup", null, BigDecimal.TEN, null, true);
        when(context.categories.findAllByRestaurantIdAndActiveTrueOrderByDisplayOrderAsc(
                RESTAURANT_ID)).thenReturn(List.of(category));
        when(context.items.findAllPublicAvailableByCategoryIdInOrderByDisplayOrderAsc(List.of(11L)))
                .thenReturn(List.of(itemView));
        when(context.items.findPublicImageDataByIdAndQrToken(101L, QR_TOKEN))
                .thenReturn(Optional.of("data:image/png;base64,aGVsbG8="));

        var menu = context.service.getCustomerMenu(QR_TOKEN);
        var responseItem = menu.getCategories().get(0).getItems().get(0);
        var image = context.service.getCustomerMenuImage(QR_TOKEN, 101L);

        assertEquals("/api/public/menu/table-qr/items/101/image", responseItem.getImageUrl());
        assertEquals("image/png", image.contentType());
        assertEquals("hello", new String(image.bytes(), java.nio.charset.StandardCharsets.UTF_8));
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
                .findAllPublicAvailableByCategoryIdInOrderByDisplayOrderAsc(org.mockito.ArgumentMatchers.anyList());
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
