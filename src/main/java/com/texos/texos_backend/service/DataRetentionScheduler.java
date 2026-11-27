package com.texos.texos_backend.service;

import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.SubscriptionPayment;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.DataRetentionRepository;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.PasswordResetTokenRepository;
import com.texos.texos_backend.repository.SubscriptionPaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);
    private static final ZoneId INDIAN_FINANCIAL_YEAR_ZONE = ZoneId.of("Asia/Kolkata");

    private final OwnerRepository ownerRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAuthService customerAuthService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final DataRetentionRepository dataRetentionRepository;
    private final BillRepository billRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final boolean enabled;
    private final TransactionTemplate transactionTemplate;

    public DataRetentionScheduler(
            OwnerRepository ownerRepository,
            CustomerRepository customerRepository,
            CustomerAuthService customerAuthService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            DataRetentionRepository dataRetentionRepository,
            BillRepository billRepository,
            SubscriptionPaymentRepository subscriptionPaymentRepository,
            PlatformTransactionManager transactionManager,
            @Value("${app.retention.enabled:true}") boolean enabled) {
        this.ownerRepository = ownerRepository;
        this.customerRepository = customerRepository;
        this.customerAuthService = customerAuthService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.dataRetentionRepository = dataRetentionRepository;
        this.billRepository = billRepository;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;
        this.enabled = enabled;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void runDailyRetention() {
        if (!enabled) {
            return;
        }

        // Compliance access logs are append-only and intentionally excluded from every purge.
        Instant now = Instant.now();
        int anonymizedOwners = anonymizeDeletedOwners(now.minusSeconds(30L * 24 * 60 * 60), now);
        int anonymizedDeletedCustomers = anonymizeDeletedCustomers(
                now.minusSeconds(30L * 24 * 60 * 60), now);

        LocalDateTime utcNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        int anonymizedInactiveCustomers = anonymizeInactiveCustomers(
                utcNow.minusDays(730), now);

        int expiredTokens = passwordResetTokenRepository.deleteLongExpiredTokens(
                utcNow.minusDays(7));
        int expiredGrants = dataRetentionRepository.deleteOldInactiveKitchenGrants(
                utcNow.minusDays(90));
        int expiredAccessCodes = dataRetentionRepository.deleteOldInactiveOrderApprovalCodes(
                utcNow.minusDays(90));
        int processedWebhooks = dataRetentionRepository.deleteOldProcessedWebhookEvents(
                utcNow.minusDays(180));
        int processedPhonePeWebhooks =
                dataRetentionRepository.deleteOldProcessedPhonePeWebhookEvents(
                        utcNow.minusDays(180));
        int closedNotifications = dataRetentionRepository.deleteOldClosedPaymentNotifications(
                utcNow.minusDays(180));
        int oldRegistrationMetadata = dataRetentionRepository
                .purgeDeletedRestaurantRegistrationMetadata(utcNow.minusDays(180));

        log.info(
                "Data retention daily run completed: anonymizedOwners={}, "
                        + "anonymizedDeletedCustomers={}, anonymizedInactiveCustomers={}, "
                        + "expiredTokens={}, expiredGrants={}, expiredAccessCodes={}, "
                        + "processedWebhooks={}, processedPhonePeWebhooks={}, "
                        + "closedNotifications={}, purgedRegistrationMetadata={}",
                anonymizedOwners,
                anonymizedDeletedCustomers,
                anonymizedInactiveCustomers,
                expiredTokens,
                expiredGrants,
                expiredAccessCodes,
                processedWebhooks,
                processedPhonePeWebhooks,
                closedNotifications,
                oldRegistrationMetadata);
    }

    @Scheduled(cron = "0 0 3 1 * *", zone = "UTC")
    public void runMonthlyFinancialRetention() {
        if (!enabled) {
            return;
        }
        Instant cutoffInstant = indianFinancialYearCutoff(Instant.now());
        LocalDateTime cutoff = LocalDateTime.ofInstant(cutoffInstant, ZoneOffset.UTC);
        int batchSize;
        do {
            batchSize = archiveExpiredFinancialRecords(cutoff);
        } while (batchSize >= 500);
    }

    static Instant indianFinancialYearCutoff(Instant now) {
        int year = now.atZone(INDIAN_FINANCIAL_YEAR_ZONE).getYear();
        int month = now.atZone(INDIAN_FINANCIAL_YEAR_ZONE).getMonthValue();
        int currentFyStartYear = month <= 3 ? year - 1 : year;
        return LocalDate.of(currentFyStartYear - 8, Month.APRIL, 1)
                .atStartOfDay(INDIAN_FINANCIAL_YEAR_ZONE)
                .toInstant();
    }

    @Transactional
    public int anonymizeDeletedOwners(Instant deletedBefore, Instant anonymizedAt) {
        List<Owner> owners = ownerRepository
                .findTop500ByDeletedAtLessThanEqualAndAnonymizedAtIsNullOrderByIdAsc(
                        deletedBefore);
        for (Owner owner : owners) {
            owner.setFullName("Deleted User");
            owner.setEmail("deleted_" + owner.getId() + "@anonymized.local");
            owner.setPhone(null);
            owner.setPasswordHash(null);
            owner.setGoogleSub(null);
            owner.setAuthProvider("DELETED");
            owner.setAvatarIcon(null);
            owner.setAvatarImage(null);
            owner.setActive(false);
            owner.setAnonymizedAt(anonymizedAt);
        }
        return owners.size();
    }

    @Transactional
    public int anonymizeDeletedCustomers(Instant deletedBefore, Instant anonymizedAt) {
        List<Customer> customers = customerRepository
                .findTop500ByDeletedAtLessThanEqualAndAnonymizedAtIsNullOrderByIdAsc(
                        deletedBefore);
        int anonymized = 0;
        for (Customer customer : customers) {
            if (customerAuthService.anonymizeForRetention(
                    customer.getId(), anonymizedAt)) {
                anonymized++;
            }
        }
        return anonymized;
    }

    @Transactional
    public int anonymizeInactiveCustomers(LocalDateTime lastActivityBefore, Instant anonymizedAt) {
        List<Customer> customers = customerRepository
                .findTop500ByActiveTrueAndDeletedAtIsNullAndAnonymizedAtIsNullAndLastActivityAtLessThanEqualOrderByIdAsc(
                        lastActivityBefore);
        int anonymized = 0;
        for (Customer customer : customers) {
            if (customerAuthService.anonymizeForRetention(
                    customer.getId(), anonymizedAt)) {
                anonymized++;
            }
        }
        return anonymized;
    }

    public int archiveExpiredFinancialRecords(LocalDateTime cutoff) {
        return transactionTemplate.execute(
                status -> archiveExpiredFinancialRecordsBatch(cutoff));
    }

    private int archiveExpiredFinancialRecordsBatch(LocalDateTime cutoff) {
        List<Bill> bills = billRepository.findTop500ByCreatedAtBeforeOrderByCreatedAtAsc(cutoff);
        List<SubscriptionPayment> subscriptionPayments =
                subscriptionPaymentRepository.findTop500ByCreatedAtBeforeOrderByCreatedAtAsc(
                        cutoff);

        BigDecimal billAmount = bills.stream()
                .map(Bill::getTotalAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subscriptionPaymentAmount = subscriptionPayments.stream()
                .map(SubscriptionPayment::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info(
                "Expired financial archive summary before purge: cutoff={}, bills={}, "
                        + "billTotalAmount={}, subscriptionPayments={}, "
                        + "subscriptionPaymentTotalAmount={}",
                cutoff,
                bills.size(),
                billAmount,
                subscriptionPayments.size(),
                subscriptionPaymentAmount);

        billRepository.deleteAllInBatch(bills);
        subscriptionPaymentRepository.deleteAllInBatch(subscriptionPayments);
        return bills.size() + subscriptionPayments.size();
    }
}
