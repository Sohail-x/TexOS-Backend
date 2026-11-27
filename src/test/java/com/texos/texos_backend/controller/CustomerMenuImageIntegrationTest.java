package com.texos.texos_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantTable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerMenuImageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void menuJsonOmitsInlineImageDataAndImageEndpointServesItForTheActiveQrMenu()
            throws Exception {
        Owner owner = new Owner();
        owner.setFullName("Menu image test owner");
        owner.setEmail("menu-image-" + UUID.randomUUID() + "@example.test");
        owner.setPasswordHash("test-password-hash");
        entityManager.persist(owner);

        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName("Menu image test restaurant");
        restaurant.setOwner(owner);
        entityManager.persist(restaurant);

        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("T1");
        table.setQrToken(UUID.randomUUID().toString());
        table.setRestaurant(restaurant);
        entityManager.persist(table);

        MenuCategory category = new MenuCategory();
        category.setCategoryName("Mains");
        category.setRestaurant(restaurant);
        entityManager.persist(category);

        MenuItem item = new MenuItem();
        item.setItemName("Soup");
        item.setPrice(BigDecimal.TEN);
        item.setImageUrl("data:image/png;base64,aGVsbG8=");
        item.setCategory(category);
        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();

        String imageUrl = "/api/public/menu/" + table.getQrToken()
                + "/items/" + item.getId() + "/image";
        mockMvc.perform(get("/api/public/menu/{qrToken}", table.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].items[0].imageUrl").value(imageUrl));

        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes("hello".getBytes(StandardCharsets.UTF_8)));
    }
}
