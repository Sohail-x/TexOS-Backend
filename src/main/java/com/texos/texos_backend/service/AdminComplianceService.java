package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.AdminComplianceReport;
import com.texos.texos_backend.dto.AdminComplianceSearchRequest;
import com.texos.texos_backend.dto.AdminDeletionRequestsResponse;
import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.LoyaltyAccount;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.LoyaltyAccountRepository;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminComplianceService {

    private static final String[] CSV_HEADERS = {
            "recordType", "id", "status", "name", "email", "phone", "restaurantName",
            "restaurantId", "ownerId", "ownerName", "address", "createdAt", "deletedAt",
            "anonymizedAt", "lastActivityAt", "orderId", "orderNumber", "orderStatus",
            "approvalStatus", "paymentStatus", "subtotal", "taxMode", "taxRate", "taxAmount",
            "serviceChargeAmount", "rewardDiscountAmount", "totalAmount", "loyaltyAccountId",
            "availablePoints", "lifetimeEarnedPoints", "lifetimeRedeemedPoints", "pointsDebt",
            "billNumber", "customerId"
    };

    private final OwnerRepository ownerRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final RestaurantOrderRepository restaurantOrderRepository;
    private final BillRepository billRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;

    public AdminComplianceService(
            OwnerRepository ownerRepository,
            RestaurantRepository restaurantRepository,
            CustomerRepository customerRepository,
            RestaurantOrderRepository restaurantOrderRepository,
            BillRepository billRepository,
            LoyaltyAccountRepository loyaltyAccountRepository) {
        this.ownerRepository = ownerRepository;
        this.restaurantRepository = restaurantRepository;
        this.customerRepository = customerRepository;
        this.restaurantOrderRepository = restaurantOrderRepository;
        this.billRepository = billRepository;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
    }

    @Transactional(readOnly = true)
    public AdminComplianceReport search(AdminComplianceSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Enter at least one search field.");
        }
        String customerName = normalize(request.customerName());
        String customerEmail = normalize(request.customerEmail());
        String customerPhone = normalize(request.customerPhone());
        String ownerName = normalize(request.ownerName());
        String ownerEmail = normalize(request.ownerEmail());
        String ownerPhone = normalize(request.ownerPhone());
        String restaurantName = normalize(request.restaurantName());
        if (customerName == null && customerEmail == null && customerPhone == null
                && ownerName == null && ownerEmail == null && ownerPhone == null
                && restaurantName == null) {
            throw new IllegalArgumentException("Enter at least one search field.");
        }

        Map<Long, Owner> owners = new LinkedHashMap<>();
        Map<Long, Restaurant> restaurants = new LinkedHashMap<>();
        Map<Long, Customer> customers = new LinkedHashMap<>();
        Map<Long, RestaurantOrder> orders = new LinkedHashMap<>();
        boolean hasCustomerCriteria = customerName != null
                || customerEmail != null || customerPhone != null;
        boolean hasOwnerCriteria = ownerName != null || ownerEmail != null || ownerPhone != null;

        if (hasOwnerCriteria) {
            addAll(owners, ownerRepository.findForComplianceSearch(ownerName, ownerEmail, ownerPhone),
                    Owner::getId);
        }
        if (restaurantName != null) {
            addAll(restaurants,
                    restaurantRepository.findAllByRestaurantNameContainingIgnoreCase(restaurantName),
                    Restaurant::getId);
        }
        if (hasCustomerCriteria) {
            addAll(customers,
                    customerRepository.findForComplianceSearch(
                            customerName, customerEmail, customerPhone),
                    Customer::getId);
            addAll(orders,
                    restaurantOrderRepository.findComplianceOrdersByCustomerDetails(
                            customerName, customerEmail, customerPhone),
                    RestaurantOrder::getId);
        }

        Set<Long> restaurantWideIds = new java.util.LinkedHashSet<>();
        if (!owners.isEmpty()) {
            List<Restaurant> ownedRestaurants =
                    restaurantRepository.findAllByOwner_IdIn(owners.keySet());
            addAll(restaurants, ownedRestaurants, Restaurant::getId);
            ownedRestaurants.stream().map(Restaurant::getId).forEach(restaurantWideIds::add);
        }
        restaurants.keySet().stream().forEach(restaurantWideIds::add);

        if (!restaurantWideIds.isEmpty()) {
            addAll(customers,
                    customerRepository.findAllByRestaurant_IdIn(restaurantWideIds),
                    Customer::getId);
            addAll(orders,
                    restaurantOrderRepository.findComplianceOrdersByRestaurantIds(restaurantWideIds),
                    RestaurantOrder::getId);
        }
        if (hasCustomerCriteria && !customers.isEmpty()) {
            addAll(orders,
                    restaurantOrderRepository.findAllByCustomer_IdInOrderByCreatedAtDesc(
                            customers.keySet()),
                    RestaurantOrder::getId);
        }

        for (Customer customer : customers.values()) {
            Restaurant restaurant = customer.getRestaurant();
            restaurants.putIfAbsent(restaurant.getId(), restaurant);
        }
        for (RestaurantOrder order : orders.values()) {
            Restaurant restaurant = order.getRestaurant();
            restaurants.putIfAbsent(restaurant.getId(), restaurant);
            if (order.getCustomer() != null) {
                customers.putIfAbsent(order.getCustomer().getId(), order.getCustomer());
            }
        }
        for (Restaurant restaurant : restaurants.values()) {
            Owner owner = restaurant.getOwner();
            owners.putIfAbsent(owner.getId(), owner);
        }

        Map<Long, List<AdminComplianceReport.LoyaltyAccountRecord>> loyaltyByCustomer =
                loadLoyaltyAccounts(customers.keySet());
        List<AdminComplianceReport.OwnerRecord> ownerRecords = owners.values().stream()
                .sorted(Comparator.comparing(Owner::getId))
                .map(this::toOwnerRecord)
                .toList();
        List<AdminComplianceReport.RestaurantRecord> restaurantRecords = restaurants.values().stream()
                .sorted(Comparator.comparing(Restaurant::getId))
                .map(this::toRestaurantRecord)
                .toList();
        List<AdminComplianceReport.CustomerRecord> customerRecords = customers.values().stream()
                .sorted(Comparator.comparing(Customer::getId))
                .map(customer -> toCustomerRecord(
                        customer, loyaltyByCustomer.getOrDefault(customer.getId(), List.of())))
                .toList();
        List<AdminComplianceReport.OrderRecord> orderRecords = orders.values().stream()
                .sorted(Comparator.comparing(RestaurantOrder::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toOrderRecord)
                .toList();

        Map<Long, RestaurantOrder> orderById = orders.values().stream()
                .collect(Collectors.toMap(RestaurantOrder::getId, Function.identity()));
        List<Bill> bills = orders.isEmpty()
                ? List.of()
                : billRepository.findAllByOrder_IdIn(orders.keySet());
        List<AdminComplianceReport.BillRecord> billRecords = bills.stream()
                .sorted(Comparator.comparing(Bill::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(bill -> toBillRecord(bill, orderById.get(bill.getOrder().getId())))
                .toList();
        int loyaltyCount = loyaltyByCustomer.values().stream().mapToInt(List::size).sum();
        int matchedRecords = ownerRecords.size() + restaurantRecords.size()
                + customerRecords.size() + orderRecords.size() + billRecords.size()
                + loyaltyCount;

        return new AdminComplianceReport(
                Instant.now(),
                new AdminComplianceReport.Summary(
                        ownerRecords.size(), restaurantRecords.size(), customerRecords.size(),
                        orderRecords.size(), billRecords.size(), loyaltyCount, matchedRecords),
                ownerRecords, restaurantRecords, customerRecords, orderRecords, billRecords);
    }

    @Transactional(readOnly = true)
    public AdminDeletionRequestsResponse getDeletionRequests() {
        return getDeletionRequests(Instant.now());
    }

    AdminDeletionRequestsResponse getDeletionRequests(Instant now) {
        List<Owner> deletedOwners = ownerRepository.findAllByDeletedAtIsNotNullOrderByDeletedAtDesc();
        Map<Long, List<String>> restaurantsByOwner = new LinkedHashMap<>();
        if (!deletedOwners.isEmpty()) {
            restaurantRepository.findAllByOwner_IdIn(
                            deletedOwners.stream().map(Owner::getId).toList())
                    .forEach(restaurant -> restaurantsByOwner
                            .computeIfAbsent(restaurant.getOwner().getId(), ignored -> new ArrayList<>())
                            .add(restaurant.getRestaurantName()));
        }
        List<AdminDeletionRequestsResponse.OwnerDeletionRequest> ownerRequests =
                deletedOwners.stream()
                        .map(owner -> new AdminDeletionRequestsResponse.OwnerDeletionRequest(
                                owner.getId(),
                                owner.getFullName(),
                                owner.getEmail(),
                                owner.getPhone(),
                                restaurantsByOwner.getOrDefault(owner.getId(), List.of()),
                                owner.getDeletedAt(),
                                owner.getAnonymizedAt(),
                                daysRemaining(owner.getDeletedAt(), now, 30),
                                daysRemaining(owner.getDeletedAt(), now, 180)))
                        .toList();

        List<AdminDeletionRequestsResponse.CustomerDeletionRequest> customerRequests =
                customerRepository.findAllByDeletedAtIsNotNullOrderByDeletedAtDesc().stream()
                        .map(customer -> new AdminDeletionRequestsResponse.CustomerDeletionRequest(
                                customer.getId(),
                                customer.getCustomerName(),
                                customer.getMobileNumber(),
                                customer.getEmail(),
                                customer.getRestaurant().getRestaurantName(),
                                customer.getDeletedAt(),
                                customer.getAnonymizedAt(),
                                daysRemaining(customer.getDeletedAt(), now, 30)))
                        .toList();
        return new AdminDeletionRequestsResponse(ownerRequests, customerRequests);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(AdminComplianceSearchRequest request) {
        return exportCsv(search(request));
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(AdminComplianceReport report) {
        return toCsv(report);
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(AdminComplianceSearchRequest request) {
        return exportPdf(search(request));
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(AdminComplianceReport report) {
        return toPdf(report);
    }

    static long daysRemaining(Instant deletedAt, Instant now, long retentionDays) {
        long daysSinceDeleted = Duration.between(deletedAt, now).toDays();
        return Math.max(0, retentionDays - daysSinceDeleted);
    }

    static byte[] toCsv(AdminComplianceReport report) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsvRow(csv, List.of("TexOS Compliance Data Export"));
        appendCsvRow(csv, List.of("Generated At", report.generatedAt().toString()));
        appendCsvRow(csv, List.of(CSV_HEADERS));

        for (AdminComplianceReport.OwnerRecord owner : report.owners()) {
            appendCsvRow(csv, csvValues("Owner", owner.ownerId(), owner.retentionStatus(),
                    owner.name(), owner.email(), owner.phone(), null, null, owner.ownerId(),
                    null, null, owner.createdAt(), owner.deletedAt(), owner.anonymizedAt()));
        }
        for (AdminComplianceReport.RestaurantRecord restaurant : report.restaurants()) {
            appendCsvRow(csv, csvValues("Restaurant", restaurant.restaurantId(),
                    restaurant.retentionStatus(), restaurant.name(), restaurant.email(),
                    restaurant.phone(), restaurant.name(), restaurant.restaurantId(),
                    restaurant.ownerId(), restaurant.ownerName(), restaurant.address(),
                    restaurant.createdAt(), restaurant.deletedAt()));
        }
        for (AdminComplianceReport.CustomerRecord customer : report.customers()) {
            appendCsvRow(csv, csvValues("Customer", customer.customerId(),
                    customer.retentionStatus(), customer.name(), customer.email(),
                    customer.phone(), customer.restaurantName(), customer.restaurantId(),
                    null, null, null, customer.createdAt(), customer.deletedAt(),
                    customer.anonymizedAt(), customer.lastActivityAt()));
            for (AdminComplianceReport.LoyaltyAccountRecord account : customer.loyaltyAccounts()) {
                appendCsvRow(csv, csvValues("LoyaltyAccount", account.loyaltyAccountId(),
                        customer.retentionStatus(), customer.name(), customer.email(),
                        customer.phone(), customer.restaurantName(), customer.restaurantId(),
                        null, null, null, account.createdAt(), customer.deletedAt(),
                        customer.anonymizedAt(), customer.lastActivityAt(), null, null, null,
                        null, null, null, null, null, null, null, null, null,
                        account.loyaltyAccountId(), account.availablePoints(),
                        account.lifetimeEarnedPoints(), account.lifetimeRedeemedPoints(),
                        account.pointsDebt()));
            }
        }
        for (AdminComplianceReport.OrderRecord order : report.orders()) {
            appendCsvRow(csv, csvValues("Order", order.orderId(), null, order.customerName(),
                    order.customerEmail(), order.customerPhone(), order.restaurantName(),
                    order.restaurantId(), null, null, null, order.createdAt(), null, null, null,
                    order.orderId(), order.orderNumber(), order.orderStatus(),
                    order.approvalStatus(), order.paymentStatus(), null, null, null, null,
                    null, null, order.totalAmount(), null, null, null, null, null, null,
                    order.customerId()));
        }
        for (AdminComplianceReport.BillRecord bill : report.bills()) {
            appendCsvRow(csv, csvValues("Bill", bill.billId(), null, null, null, null,
                    bill.restaurantName(), bill.restaurantId(), null, null, null,
                    bill.createdAt(), null, null, null, bill.orderId(), bill.orderNumber(),
                    null, null, null, bill.subtotal(), bill.taxMode(), bill.taxRate(),
                    bill.taxAmount(), bill.serviceChargeAmount(), bill.rewardDiscountAmount(),
                    bill.totalAmount(), null, null, null, null, null, bill.billNumber(), null));
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private Map<Long, List<AdminComplianceReport.LoyaltyAccountRecord>> loadLoyaltyAccounts(
            Collection<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return loyaltyAccountRepository.findAllByCustomer_IdIn(customerIds).stream()
                .collect(Collectors.groupingBy(
                        account -> account.getCustomer().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toLoyaltyRecord, Collectors.toList())));
    }

    private AdminComplianceReport.OwnerRecord toOwnerRecord(Owner owner) {
        return new AdminComplianceReport.OwnerRecord(
                owner.getId(), owner.getFullName(), owner.getEmail(), owner.getPhone(),
                retentionStatus(owner.getDeletedAt(), owner.getAnonymizedAt()),
                owner.getCreatedAt(), owner.getDeletedAt(), owner.getAnonymizedAt());
    }

    private AdminComplianceReport.RestaurantRecord toRestaurantRecord(Restaurant restaurant) {
        Owner owner = restaurant.getOwner();
        return new AdminComplianceReport.RestaurantRecord(
                restaurant.getId(), restaurant.getRestaurantName(), restaurant.getAddress(),
                restaurant.getPhone(), restaurant.getEmail(), owner.getId(), owner.getFullName(),
                restaurant.getDeletedAt() == null ? "ACTIVE" : "PENDING_DELETION",
                restaurant.getCreatedAt(), restaurant.getDeletedAt());
    }

    private AdminComplianceReport.CustomerRecord toCustomerRecord(
            Customer customer, List<AdminComplianceReport.LoyaltyAccountRecord> loyaltyAccounts) {
        Restaurant restaurant = customer.getRestaurant();
        return new AdminComplianceReport.CustomerRecord(
                customer.getId(), customer.getCustomerName(), customer.getEmail(),
                customer.getMobileNumber(), restaurant.getId(), restaurant.getRestaurantName(),
                retentionStatus(customer.getDeletedAt(), customer.getAnonymizedAt()),
                customer.getCreatedAt(), customer.getLastActivityAt(), customer.getDeletedAt(),
                customer.getAnonymizedAt(), loyaltyAccounts);
    }

    private AdminComplianceReport.LoyaltyAccountRecord toLoyaltyRecord(LoyaltyAccount account) {
        return new AdminComplianceReport.LoyaltyAccountRecord(
                account.getId(), account.getAvailablePoints(), account.getLifetimeEarnedPoints(),
                account.getLifetimeRedeemedPoints(), account.getPointsDebt(),
                account.getCreatedAt(), account.getUpdatedAt());
    }

    private AdminComplianceReport.OrderRecord toOrderRecord(RestaurantOrder order) {
        Restaurant restaurant = order.getRestaurant();
        Customer customer = order.getCustomer();
        return new AdminComplianceReport.OrderRecord(
                order.getId(), order.getOrderNumber(), restaurant.getId(),
                restaurant.getRestaurantName(), customer == null ? null : customer.getId(),
                order.getCustomerName(), order.getCustomerEmail(), order.getCustomerMobile(),
                order.getOrderStatus() == null ? null : order.getOrderStatus().name(),
                order.getApprovalStatus() == null ? null : order.getApprovalStatus().name(),
                order.getPaymentStatus() == null ? null : order.getPaymentStatus().name(),
                order.getTotalAmount(), order.getCreatedAt());
    }

    private AdminComplianceReport.BillRecord toBillRecord(Bill bill, RestaurantOrder order) {
        if (order == null) {
            return new AdminComplianceReport.BillRecord(
                    bill.getId(), bill.getBillNumber(), bill.getOrder().getId(),
                    bill.getOrder().getOrderNumber(), null, null, bill.getSubtotal(),
                    bill.getTaxMode() == null ? null : bill.getTaxMode().name(),
                    bill.getTaxRate(), bill.getTaxAmount(), bill.getServiceChargeAmount(),
                    bill.getRewardDiscountAmount(), bill.getTotalAmount(), bill.getCreatedAt());
        }
        return new AdminComplianceReport.BillRecord(
                bill.getId(), bill.getBillNumber(), order.getId(), order.getOrderNumber(),
                order.getRestaurant().getId(), order.getRestaurant().getRestaurantName(),
                bill.getSubtotal(), bill.getTaxMode() == null ? null : bill.getTaxMode().name(),
                bill.getTaxRate(), bill.getTaxAmount(), bill.getServiceChargeAmount(),
                bill.getRewardDiscountAmount(), bill.getTotalAmount(), bill.getCreatedAt());
    }

    private static String retentionStatus(Instant deletedAt, Instant anonymizedAt) {
        if (anonymizedAt != null) {
            return "ANONYMIZED";
        }
        return deletedAt == null ? "ACTIVE" : "PENDING_DELETION";
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> void addAll(
            Map<Long, T> destination, Collection<T> rows, Function<T, Long> idFunction) {
        for (T row : rows) {
            destination.putIfAbsent(idFunction.apply(row), row);
        }
    }

    private static List<Object> csvValues(Object... values) {
        List<Object> row = new ArrayList<>(CSV_HEADERS.length);
        for (Object value : values) {
            row.add(value);
        }
        while (row.size() < CSV_HEADERS.length) {
            row.add(null);
        }
        return row.subList(0, CSV_HEADERS.length);
    }

    private static void appendCsvRow(StringBuilder csv, List<?> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(csvEscape(values.get(index) == null ? "" : values.get(index).toString()));
        }
        csv.append("\r\n");
    }

    static String csvEscape(String value) {
        int firstMeaningful = 0;
        while (firstMeaningful < value.length()
                && Character.isWhitespace(value.charAt(firstMeaningful))) {
            firstMeaningful++;
        }
        if (firstMeaningful < value.length()
                && "=+-@".indexOf(value.charAt(firstMeaningful)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static byte[] toPdf(AdminComplianceReport report) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (CompliancePdfWriter writer = new CompliancePdfWriter(document)) {
                writer.heading("TexOS Compliance Data Export");
                writer.line("Generated at: " + report.generatedAt());
                AdminComplianceReport.Summary summary = report.summary();
                writer.line("Records: " + summary.totalMatchedRecords()
                        + " | Owners: " + summary.ownerCount()
                        + " | Restaurants: " + summary.restaurantCount()
                        + " | Customers: " + summary.customerCount()
                        + " | Orders: " + summary.orderCount()
                        + " | Bills: " + summary.billCount());
                writePdfSection(writer, "Owners", report.owners().stream()
                        .map(row -> "Owner #" + row.ownerId() + " | " + row.name()
                                + " | " + row.email() + " | " + row.phone()
                                + " | " + row.retentionStatus()
                                + " | Created: " + row.createdAt()
                                + " | Deleted: " + row.deletedAt()
                                + " | Anonymized: " + row.anonymizedAt())
                        .toList());
                writePdfSection(writer, "Restaurants", report.restaurants().stream()
                        .map(row -> "Restaurant #" + row.restaurantId() + " | " + row.name()
                                + " | Owner: " + row.ownerName() + " (#" + row.ownerId() + ")"
                                + " | " + row.email() + " | " + row.phone()
                                + " | Address: " + row.address()
                                + " | " + row.retentionStatus()
                                + " | Created: " + row.createdAt()
                                + " | Deleted: " + row.deletedAt())
                        .toList());
                writePdfSection(writer, "Customers", report.customers().stream()
                        .map(row -> "Customer #" + row.customerId() + " | " + row.name()
                                + " | " + row.email() + " | " + row.phone()
                                + " | Restaurant: " + row.restaurantName()
                                + " | " + row.retentionStatus()
                                + " | Last activity: " + row.lastActivityAt()
                                + " | Created: " + row.createdAt()
                                + " | Deleted: " + row.deletedAt()
                                + " | Anonymized: " + row.anonymizedAt()
                                + " | Loyalty: " + row.loyaltyAccounts().stream()
                                        .map(account -> "account #" + account.loyaltyAccountId()
                                                + " points=" + account.availablePoints()
                                                + " earned=" + account.lifetimeEarnedPoints()
                                                + " redeemed=" + account.lifetimeRedeemedPoints()
                                                + " debt=" + account.pointsDebt())
                                        .collect(Collectors.joining("; ")))
                        .toList());
                writePdfSection(writer, "Orders", report.orders().stream()
                        .map(row -> "Order #" + row.orderId() + " | " + row.orderNumber()
                                + " | " + row.restaurantName() + " (#" + row.restaurantId() + ")"
                                + " | Customer: " + row.customerName() + " (#" + row.customerId()
                                + ") | " + row.customerEmail() + " | " + row.customerPhone()
                                + " | " + row.orderStatus() + " | " + row.approvalStatus()
                                + " | " + row.paymentStatus() + " | Amount: " + row.totalAmount()
                                + " | Created: " + row.createdAt())
                        .toList());
                writePdfSection(writer, "Bills", report.bills().stream()
                        .map(row -> "Bill #" + row.billId() + " | " + row.billNumber()
                                + " | Order: " + row.orderNumber() + " (#" + row.orderId() + ")"
                                + " | " + row.restaurantName() + " | Subtotal: " + row.subtotal()
                                + " | Tax mode: " + row.taxMode() + " at " + row.taxRate()
                                + " | Tax: " + row.taxAmount()
                                + " | Service charge: " + row.serviceChargeAmount()
                                + " | Total: " + row.totalAmount()
                                + " | Created: " + row.createdAt())
                        .toList());
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate compliance PDF.", exception);
        }
    }

    private static void writePdfSection(CompliancePdfWriter writer, String title, List<String> rows)
            throws IOException {
        writer.heading(title);
        if (rows.isEmpty()) {
            writer.line("No matching records.");
            return;
        }
        for (String row : rows) {
            writer.line(row);
        }
    }

    private static final class CompliancePdfWriter implements AutoCloseable {
        private static final float MARGIN = 48;
        private static final float BOTTOM_MARGIN = 42;
        private static final float FONT_SIZE = 9;
        private final PDDocument document;
        private final PDType1Font regular =
                new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold =
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        private CompliancePdfWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void heading(String text) throws IOException {
            writeWrapped(text, bold, 12);
            y -= 4;
        }

        private void line(String text) throws IOException {
            writeWrapped(text, regular, FONT_SIZE);
        }

        private void writeWrapped(String input, PDType1Font font, float fontSize)
                throws IOException {
            String safeText = toPdfText(input);
            StringBuilder line = new StringBuilder();
            for (String word : safeText.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.getStringWidth(candidate) / 1000 * fontSize
                        > PDRectangle.LETTER.getWidth() - 2 * MARGIN) {
                    writeLine(line.toString(), font, fontSize);
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
            }
            if (!line.isEmpty()) {
                writeLine(line.toString(), font, fontSize);
            }
        }

        private void writeLine(String text, PDType1Font font, float fontSize) throws IOException {
            if (y < BOTTOM_MARGIN) {
                newPage();
            }
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(text);
            stream.endText();
            y -= fontSize + 5;
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }

        private static String toPdfText(String value) {
            return Normalizer.normalize(value, Normalizer.Form.NFKD)
                    .replaceAll("\\p{M}", "")
                    .replaceAll("[^\\x20-\\x7E]", "?")
                    .replaceAll("\\s+", " ")
                    .trim();
        }
    }
}
