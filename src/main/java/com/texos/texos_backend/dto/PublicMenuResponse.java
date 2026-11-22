
package com.texos.texos_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PublicMenuResponse {

    private String restaurantName;
    private String restaurantAddress;
    private String restaurantPhone;
    private String openingHours;
    private String googleMapsUrl;
    private boolean rewardsEnabled;

    private String tableNumber;

    private List<PublicMenuCategoryResponse> categories;
}
