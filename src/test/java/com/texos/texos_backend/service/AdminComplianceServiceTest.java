package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.AdminComplianceReport;
import com.texos.texos_backend.dto.AdminComplianceSearchRequest;
import com.texos.texos_backend.dto.AdminDeletionRequestsResponse;
import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.LoyaltyAccount;
import com.texos.texos_backend.entity.MenuPriceTaxMode;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.LoyaltyAccountRepository;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminComplianceServiceTest {

    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final RestaurantOrderRepository restaurantOrderRepository =
            mock(RestaurantOrderRepository.class);
    private final BillRepository billRepository = mock(BillRepository.class);
    private final LoyaltyAccountRepository loyaltyAccountRepository =
            mock(LoyaltyAccountRepository.class);
    private final AdminComplianceService service = new AdminComplianceService(
            ownerRepository, restaurantRepository, customerRepository,
            restaurantOrderRepository, billRepository, loyaltyAccountRepository);

    @Test
    void rejectsSearchWithoutAnyNonBlankField() {
        assertThrows(IllegalArgumentException.class,
                () -> service.search(new AdminComplianceSearchRequest(
                        " ", null, null, null, null, null, null)));
    }

    @Test
    void searchIncludesSoftDeletedCustomersAndTheirRelatedFinancialRecords() {
        Owner owner = owner(1L);
        Restaurant restaurant = restaurant(2L, owner);
        Customer customer = customer(3L, restaurant);
        customer.setDeletedAt(Instant.parse("2026-03-01T00:00:00Z"));
        customer.setAnonymizedAt(Instant.parse("2026-03-31T00:00:00Z"));
        RestaurantOrder order = order(4L, restaurant, customer);
        Bill bill = bill(5L, order);
        LoyaltyAccount account = loyaltyAccount(6L, restaurant, customer);

        when(ownerRepository.findForComplianceSearch("Owner", null, null))
                .thenReturn(List.of(owner));
        when(restaurantRepository.findAllByOwner_IdIn(
                org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(restaurant));
        when(customerRepository.findAllByRestaurant_IdIn(List.of(2L)))
                .thenReturn(List.of(customer));
        when(restaurantOrderRepository.findComplianceOrdersByRestaurantIds(
                org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(order));
        when(loyaltyAccountRepository.findAllByCustomer_IdIn(
                org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(account));
        when(billRepository.findAllByOrder_IdIn(
                org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(bill));

        AdminComplianceReport result = service.search(new AdminComplianceSearchRequest(
                null, null, null, "Owner", null, null, null));

        assertEquals(1, result.summary().ownerCount());
        assertEquals(1, result.summary().restaurantCount());
        assertEquals(1, result.summary().customerCount());
        assertEquals(1, result.summary().orderCount());
        assertEquals(1, result.summary().billCount());
        assertEquals(1, result.summary().loyaltyAccountCount());
        assertEquals("ANONYMIZED", result.customers().get(0).retentionStatus());
        assertEquals(5L, result.bills().get(0).billId());
    }

    @Test
    void exportsCsvWithUtf8BomAndSpreadsheetFormulaEscaping() {
        AdminComplianceReport report = new AdminComplianceReport(
                Instant.parse("2026-04-01T00:00:00Z"),
                new AdminComplianceReport.Summary(1, 0, 0, 0, 0, 0, 1),
                List.of(new AdminComplianceReport.OwnerRecord(
                        1L, "=SUM(1,1)", "owner@example.com", null, "ACTIVE",
                        LocalDateTime.of(2026, 1, 1, 0, 0), null, null)),
                List.of(), List.of(), List.of(), List.of());

        String csv = new String(AdminComplianceService.toCsv(report),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("\"'=SUM(1,1)\""));
        assertTrue(csv.contains("\"recordType\",\"id\",\"status\""));
    }

    @Test
    void deletionRequestCountdownsUseThirtyAndOneHundredEightyDayWindows() {
        Instant now = Instant.parse("2026-06-30T12:00:00Z");
        Owner owner = owner(1L);
        owner.setDeletedAt(now.minusSeconds(10 * 86_400L + 23 * 3_600L));
        Restaurant restaurant = restaurant(2L, owner);
        Customer customer = customer(3L, restaurant);
        customer.setDeletedAt(now.minusSeconds(30 * 86_400L));

        when(ownerRepository.findAllByDeletedAtIsNotNullOrderByDeletedAtDesc())
                .thenReturn(List.of(owner));
        when(restaurantRepository.findAllByOwner_IdIn(
                org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(restaurant));
        when(customerRepository.findAllByDeletedAtIsNotNullOrderByDeletedAtDesc())
                .thenReturn(List.of(customer));

        AdminDeletionRequestsResponse result = service.getDeletionRequests(now);

        assertEquals(20, result.ownerDeletions().get(0).daysRemainingPiiErasure());
        assertEquals(170, result.ownerDeletions().get(0).daysRemainingMetadataPurge());
        assertEquals(0, result.customerDeletions().get(0).daysRemainingPiiErasure());
        assertEquals(List.of("Cafe"), result.ownerDeletions().get(0).restaurantNames());
    }

    @Test
    void exportsAReadablePdfDocument() throws Exception {
        byte[] pdf = service.exportPdf(new AdminComplianceSearchRequest(
                "No matching customer", null, null, null, null, null, null));

        assertTrue(pdf.length > 100);
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                .startsWith("%PDF-"));
        assertFalse(pdf.length == 0);
    }

    private static Owner owner(Long id) {
        Owner owner = new Owner();
        owner.setId(id);
        owner.setFullName("Owner Name");
        owner.setEmail("owner@example.com");
        owner.setPhone("9000000000");
        return owner;
    }

    private static Restaurant restaurant(Long id, Owner owner) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(id);
        restaurant.setRestaurantName("Cafe");
        restaurant.setOwner(owner);
        return restaurant;
    }

    private static Customer customer(Long id, Restaurant restaurant) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerName("Customer Name");
        customer.setEmail("customer@example.com");
        customer.setMobileNumber("9111111111");
        customer.setRestaurant(restaurant);
        return customer;
    }

    private static RestaurantOrder order(Long id, Restaurant restaurant, Customer customer) {
        RestaurantOrder order = new RestaurantOrder();
        order.setId(id);
        order.setOrderNumber("ORD-1");
        order.setRestaurant(restaurant);
        order.setCustomer(customer);
        order.setCustomerName(customer.getCustomerName());
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerMobile(customer.getMobileNumber());
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        return order;
    }

    private static Bill bill(Long id, RestaurantOrder order) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setBillNumber("BILL-1");
        bill.setOrder(order);
        bill.setSubtotal(new BigDecimal("100.00"));
        bill.setTaxMode(MenuPriceTaxMode.EXCLUSIVE);
        bill.setTaxRate(BigDecimal.ZERO);
        bill.setTaxAmount(BigDecimal.ZERO);
        bill.setServiceChargeAmount(BigDecimal.ZERO);
        bill.setRewardDiscountAmount(BigDecimal.ZERO);
        bill.setTotalAmount(new BigDecimal("100.00"));
        bill.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        return bill;
    }

    private static LoyaltyAccount loyaltyAccount(
            Long id, Restaurant restaurant, Customer customer) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setId(id);
        account.setRestaurant(restaurant);
        account.setCustomer(customer);
        account.setAvailablePoints(10L);
        return account;
    }
}
