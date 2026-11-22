package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.RestaurantRegistrationRequest;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RestaurantServiceTest {
    @Test
    void savesValidGoogleMapsReviewLink() {
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        RestaurantService service = new RestaurantService(restaurants, mock(OwnerRepository.class));
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        when(restaurants.findByIdAndOwnerId(5L, 8L)).thenReturn(Optional.of(restaurant));
        when(restaurants.save(restaurant)).thenReturn(restaurant);
        RestaurantRegistrationRequest request = request("https://g.page/r/restaurant/review");

        Restaurant updated = service.updateRestaurant(5L, 8L, request);

        assertEquals("https://g.page/r/restaurant/review", updated.getGoogleMapsUrl());
        verify(restaurants).save(restaurant);
    }

    @Test
    void rejectsNonGoogleOrInsecureReviewLinks() {
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        RestaurantService service = new RestaurantService(restaurants, mock(OwnerRepository.class));
        Restaurant restaurant = new Restaurant();
        when(restaurants.findByIdAndOwnerId(5L, 8L)).thenReturn(Optional.of(restaurant));

        assertThrows(InvalidRequestException.class,
                () -> service.updateRestaurant(5L, 8L, request("https://example.com/review")));
        verify(restaurants, never()).save(any());
    }

    private RestaurantRegistrationRequest request(String mapsUrl) {
        RestaurantRegistrationRequest request = new RestaurantRegistrationRequest();
        request.setRestaurantName("Test Restaurant");
        request.setGoogleMapsUrl(mapsUrl);
        return request;
    }
}
