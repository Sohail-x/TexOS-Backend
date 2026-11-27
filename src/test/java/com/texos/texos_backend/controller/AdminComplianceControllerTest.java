package com.texos.texos_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.AdminComplianceReport;
import com.texos.texos_backend.dto.AdminComplianceSearchRequest;
import com.texos.texos_backend.dto.AdminDeletionRequestsResponse;
import com.texos.texos_backend.entity.ComplianceExportAuditLog;
import com.texos.texos_backend.repository.ComplianceExportAuditLogRepository;
import com.texos.texos_backend.service.AdminComplianceService;
import com.texos.texos_backend.service.ComplianceExportAuditService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.mockito.ArgumentCaptor;

class AdminComplianceControllerTest {

    private final AdminComplianceService complianceService = mock(AdminComplianceService.class);
    private final ComplianceExportAuditLogRepository auditRepository =
            mock(ComplianceExportAuditLogRepository.class);
    private final AdminComplianceController controller = new AdminComplianceController(
            complianceService, new ComplianceExportAuditService(auditRepository));
    private final Authentication admin = new UsernamePasswordAuthenticationToken(
            "texos-admin",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_TEXOS_ADMIN")));
    private final AdminComplianceReport report = new AdminComplianceReport(
            Instant.parse("2026-11-27T00:00:00Z"),
            new AdminComplianceReport.Summary(1, 2, 3, 4, 5, 6, 21),
            List.of(), List.of(), List.of(), List.of(), List.of());

    @BeforeEach
    void setClientAddress() {
        when(auditRepository.save(any(ComplianceExportAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void searchWritesAdminActionAndOnlyFilterNamesToAudit() {
        AdminComplianceSearchRequest criteria = new AdminComplianceSearchRequest(
                "Sensitive Customer", "customer@example.com", null, null, null, null, null);
        when(complianceService.search(criteria)).thenReturn(report);

        var response = controller.search(criteria, admin, request());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ComplianceExportAuditLog log = savedAudit();
        assertEquals("texos-admin", log.getAdminUsername());
        assertEquals("SEARCH", log.getActionType());
        assertEquals("customerName,customerEmail", log.getSearchCriteriaSummary());
        assertEquals(21, log.getMatchedCount());
        assertEquals("203.0.113.8", log.getClientIp());
        assertTrue(log.getCreatedAt().isAfter(Instant.now().minusSeconds(10)));
    }

    @Test
    void csvExportWritesAnAuditEntryWithTheMatchedRecordCount() {
        AdminComplianceSearchRequest criteria = new AdminComplianceSearchRequest(
                null, null, null, "Owner Name", null, null, null);
        when(complianceService.search(criteria)).thenReturn(report);
        when(complianceService.exportCsv(report)).thenReturn(new byte[] {1, 2, 3});

        var response = controller.exportCsv(criteria, admin, request());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ComplianceExportAuditLog log = savedAudit();
        assertEquals("EXPORT_CSV", log.getActionType());
        assertEquals("ownerName", log.getSearchCriteriaSummary());
        assertEquals(21, log.getMatchedCount());
    }

    @Test
    void pdfExportWritesAnAuditEntryWithTheMatchedRecordCount() {
        AdminComplianceSearchRequest criteria = new AdminComplianceSearchRequest(
                null, null, null, null, null, null, "Cafe");
        when(complianceService.search(criteria)).thenReturn(report);
        when(complianceService.exportPdf(report)).thenReturn(new byte[] {4, 5, 6});

        var response = controller.exportPdf(criteria, admin, request());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ComplianceExportAuditLog log = savedAudit();
        assertEquals("EXPORT_PDF", log.getActionType());
        assertEquals("restaurantName", log.getSearchCriteriaSummary());
        assertEquals(21, log.getMatchedCount());
    }

    @Test
    void deletionRequestViewAuditsTheCombinedRequestCount() {
        AdminDeletionRequestsResponse deletionRequests =
                new AdminDeletionRequestsResponse(
                        List.of(
                                new AdminDeletionRequestsResponse.OwnerDeletionRequest(
                                        1L, "Owner", null, null, List.of(),
                                        Instant.parse("2026-11-27T00:00:00Z"), null, 0, 0)),
                        List.of(
                                new AdminDeletionRequestsResponse.CustomerDeletionRequest(
                                        2L, "Customer", null, null, "Cafe",
                                        Instant.parse("2026-11-27T00:00:00Z"), null, 0),
                                new AdminDeletionRequestsResponse.CustomerDeletionRequest(
                                        3L, "Another Customer", null, null, "Cafe",
                                        Instant.parse("2026-11-27T00:00:00Z"), null, 0)));
        when(complianceService.getDeletionRequests()).thenReturn(deletionRequests);

        var response = controller.getDeletionRequests(admin, request());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ComplianceExportAuditLog log = savedAudit();
        assertEquals("VIEW_DELETION_REQUESTS", log.getActionType());
        assertEquals("none", log.getSearchCriteriaSummary());
        assertEquals(3, log.getMatchedCount());
    }

    private ComplianceExportAuditLog savedAudit() {
        ArgumentCaptor<ComplianceExportAuditLog> captor =
                ArgumentCaptor.forClass(ComplianceExportAuditLog.class);
        verify(auditRepository).save(captor.capture());
        return captor.getValue();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");
        return request;
    }
}
