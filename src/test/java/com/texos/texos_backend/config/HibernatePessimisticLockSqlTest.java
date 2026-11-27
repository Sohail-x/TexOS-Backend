package com.texos.texos_backend.config;

import com.texos.texos_backend.repository.LoyaltyAccountRepository;
import com.texos.texos_backend.repository.LoyaltyPointLotRepository;
import com.texos.texos_backend.repository.LoyaltyRedemptionRepository;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.texos.texos_backend.config.HibernatePessimisticLockSqlTest$CapturingStatementInspector")
@Transactional
class HibernatePessimisticLockSqlTest {

    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Autowired
    private LoyaltyRedemptionRepository redemptionRepository;

    @Autowired
    private LoyaltyPointLotRepository pointLotRepository;

    @Autowired
    private LoyaltyAccountRepository loyaltyAccountRepository;

    @Test
    void pessimisticWriteQueriesUsePlainForUpdateWithoutAlias() {
        STATEMENTS.clear();
        LocalDateTime now = LocalDateTime.now();

        redemptionRepository.lockExpiredPendingReservations(now);
        pointLotRepository.lockExpiredLots(-1L, now);
        loyaltyAccountRepository.lockAccount(-1L, -1L);

        assertPlainForUpdate("loyalty_redemptions");
        assertPlainForUpdate("loyalty_point_lots");
        assertPlainForUpdate("loyalty_accounts");
    }

    private void assertPlainForUpdate(String tableName) {
        String sql = STATEMENTS.stream()
                .map(statement -> statement.toLowerCase(Locale.ROOT))
                .filter(statement -> statement.contains("from " + tableName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SQL captured for " + tableName));

        assertTrue(sql.contains("for update"), "Expected a pessimistic write lock for " + tableName);
        assertFalse(sql.contains("for update of"), "Alias-qualified lock clause is unsupported for " + tableName);
    }

    public static class CapturingStatementInspector implements StatementInspector {
        @Override
        public String inspect(String sql) {
            STATEMENTS.add(sql);
            return sql;
        }
    }
}
