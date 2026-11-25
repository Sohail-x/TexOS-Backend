package com.texos.texos_backend.service;

public record RestaurantLiveUpdateEvent(String destination, String type) {

    public static final String PAYMENT_NOTIFICATIONS_CHANGED = "PAYMENT_NOTIFICATIONS_CHANGED";
    public static final String WAITER_REQUEST_CHANGED = "WAITER_REQUEST_CHANGED";

    public static RestaurantLiveUpdateEvent ordersChanged(Long restaurantId) {
        return new RestaurantLiveUpdateEvent(
                "/topic/restaurants/" + restaurantId + "/live-orders",
                "ORDERS_CHANGED");
    }

    public static RestaurantLiveUpdateEvent dashboardChanged(Long restaurantId, String type) {
        return new RestaurantLiveUpdateEvent(
                "/topic/restaurants/" + restaurantId + "/dashboard-updates",
                type);
    }

    public static RestaurantLiveUpdateEvent approvalChanged(Long restaurantId) {
        return new RestaurantLiveUpdateEvent(
                "/topic/restaurants/" + restaurantId + "/order-approval",
                "ORDER_APPROVAL_CHANGED");
    }

    public static RestaurantLiveUpdateEvent announcementsChanged() {
        return new RestaurantLiveUpdateEvent(
                "/topic/announcements",
                "ANNOUNCEMENTS_CHANGED");
    }
}
