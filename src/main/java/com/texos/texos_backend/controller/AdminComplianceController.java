package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.AdminComplianceReport;
import com.texos.texos_backend.dto.AdminComplianceSearchRequest;
import com.texos.texos_backend.dto.AdminDeletionRequestsResponse;
import com.texos.texos_backend.service.ComplianceExportAuditService;
import com.texos.texos_backend.service.AdminComplianceService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.Supplier;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/compliance")
public class AdminComplianceController {

    private static final String CSV_FILENAME = "texos-compliance-export.csv";
    private static final String PDF_FILENAME = "texos-compliance-export.pdf";
    private static final MediaType CSV_UTF8 =
            MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final AdminComplianceService service;
    private final ComplianceExportAuditService auditService;

    public AdminComplianceController(
            AdminComplianceService service,
            ComplianceExportAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping("/search")
    public ResponseEntity<AdminComplianceReport> search(
            @RequestBody(required = false) AdminComplianceSearchRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        AdminComplianceReport report = audited(
                authentication,
                httpRequest,
                "SEARCH",
                criteriaSummary(request),
                () -> service.search(request),
                result -> result.summary().totalMatchedRecords());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(report);
    }

    @PostMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestBody(required = false) AdminComplianceSearchRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        ExportResult result = audited(
                authentication,
                httpRequest,
                "EXPORT_CSV",
                criteriaSummary(request),
                () -> {
                    AdminComplianceReport report = service.search(request);
                    return new ExportResult(
                            service.exportCsv(report),
                            report.summary().totalMatchedRecords());
                },
                ExportResult::matchedCount);
        return export(CSV_UTF8, CSV_FILENAME, result.bytes());
    }

    @PostMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestBody(required = false) AdminComplianceSearchRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        ExportResult result = audited(
                authentication,
                httpRequest,
                "EXPORT_PDF",
                criteriaSummary(request),
                () -> {
                    AdminComplianceReport report = service.search(request);
                    return new ExportResult(
                            service.exportPdf(report),
                            report.summary().totalMatchedRecords());
                },
                ExportResult::matchedCount);
        return export(MediaType.APPLICATION_PDF, PDF_FILENAME, result.bytes());
    }

    @GetMapping("/deletion-requests")
    public ResponseEntity<AdminDeletionRequestsResponse> getDeletionRequests(
            Authentication authentication,
            HttpServletRequest httpRequest) {
        AdminDeletionRequestsResponse requests = audited(
                authentication,
                httpRequest,
                "VIEW_DELETION_REQUESTS",
                "none",
                service::getDeletionRequests,
                result -> result.ownerDeletions().size() + result.customerDeletions().size());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(requests);
    }

    private <T> T audited(
            Authentication authentication,
            HttpServletRequest request,
            String actionType,
            String criteriaSummary,
            Supplier<T> operation,
            ToIntFunction<T> matchedCount) {
        String adminUsername = authentication.getName();
        String clientIp = request.getRemoteAddr();
        T result;
        int count;
        try {
            result = operation.get();
            count = matchedCount.applyAsInt(result);
        } catch (RuntimeException requestFailure) {
            try {
                auditService.record(
                        adminUsername, actionType, criteriaSummary, 0, clientIp);
            } catch (RuntimeException auditFailure) {
                auditFailure.addSuppressed(requestFailure);
                throw auditFailure;
            }
            throw requestFailure;
        }
        auditService.record(adminUsername, actionType, criteriaSummary, count, clientIp);
        return result;
    }

    private static String criteriaSummary(AdminComplianceSearchRequest request) {
        if (request == null) {
            return "none";
        }
        List<String> keys = new ArrayList<>();
        addIfPresent(keys, "customerName", request.customerName());
        addIfPresent(keys, "customerEmail", request.customerEmail());
        addIfPresent(keys, "customerPhone", request.customerPhone());
        addIfPresent(keys, "ownerName", request.ownerName());
        addIfPresent(keys, "ownerEmail", request.ownerEmail());
        addIfPresent(keys, "ownerPhone", request.ownerPhone());
        addIfPresent(keys, "restaurantName", request.restaurantName());
        return keys.isEmpty() ? "none" : String.join(",", keys);
    }

    private static void addIfPresent(List<String> keys, String key, String value) {
        if (value != null && !value.isBlank()) {
            keys.add(key);
        }
    }

    private ResponseEntity<byte[]> export(MediaType mediaType, String filename, byte[] bytes) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(mediaType)
                .body(bytes);
    }

    private record ExportResult(byte[] bytes, int matchedCount) {
    }
}
