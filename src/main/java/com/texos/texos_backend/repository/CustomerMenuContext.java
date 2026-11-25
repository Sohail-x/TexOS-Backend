package com.texos.texos_backend.repository;

public interface CustomerMenuContext {
    Long getRestaurantId();

    String getRestaurantName();

    String getRestaurantAddress();

    String getRestaurantPhone();

    String getOpeningHours();

    String getGoogleMapsUrl();

    Boolean getRestaurantActive();

    String getTableNumber();
}
