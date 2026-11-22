package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.repository.MenuCategoryRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MenuImportServiceTest {

    private final MenuImportService menuImportService = new MenuImportService(
            mock(RestaurantRepository.class),
            mock(MenuCategoryRepository.class),
            mock(MenuItemRepository.class));

    @Test
    void parsesSpreadsheetItemsAndPricesWithoutExternalServices() throws Exception {
        byte[] fileBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Menu");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Item Name");
            header.createCell(1).setCellValue("Price");
            header.createCell(2).setCellValue("Category");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Masala Dosa");
            row.createCell(1).setCellValue(125.50);
            row.createCell(2).setCellValue("Breakfast");
            workbook.write(output);
            fileBytes = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "menu.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                fileBytes);

        var items = menuImportService.parseFile(file);

        assertEquals(1, items.size());
        assertEquals("Masala Dosa", items.get(0).getItemName());
        assertEquals(new BigDecimal("125.5"), items.get(0).getPrice());
        assertEquals("Breakfast", items.get(0).getCategoryName());
    }

    @Test
    void rejectsUnsupportedFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "menu.csv", "text/csv", "Masala Dosa,125".getBytes());

        assertThrows(InvalidRequestException.class, () -> menuImportService.parseFile(file));
    }
}
