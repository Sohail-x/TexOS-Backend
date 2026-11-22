package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.MenuImportItem;
import com.texos.texos_backend.dto.MenuImportRequest;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.MenuCategoryRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MenuImportService {

    private static final Pattern PDF_MENU_LINE = Pattern.compile(
            "^(.+?)(?:\\s*[-–—:]\\s*|\\s+)(?:₹\\s*|INR\\s*|Rs\\.?\\s*)?(\\d+(?:\\.\\d{1,2})?)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final RestaurantRepository restaurantRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;

    public MenuImportService(
            RestaurantRepository restaurantRepository,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository itemRepository) {
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    public List<MenuImportItem> parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Choose a non-empty Excel or PDF file.");
        }
        String filename = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            List<MenuImportItem> parsed;
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                parsed = parseSpreadsheet(file.getInputStream());
            } else if (filename.endsWith(".pdf")) {
                parsed = parsePdf(file.getBytes());
            } else {
                throw new InvalidRequestException("Only .xlsx, .xls and text-based .pdf files are supported.");
            }
            if (parsed.isEmpty()) {
                throw new InvalidRequestException(
                        "No menu items could be read. Use columns for item name and price, or a text-based PDF with one item and price per line.");
            }
            if (parsed.size() > 500) {
                throw new InvalidRequestException("Import is limited to 500 menu items at a time.");
            }
            return parsed;
        } catch (IOException ex) {
            throw new InvalidRequestException("The selected menu file could not be read.");
        }
    }

    private List<MenuImportItem> parseSpreadsheet(InputStream input) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : header) {
                columns.put(normalizeHeader(formatter.formatCellValue(cell)), cell.getColumnIndex());
            }
            Integer nameColumn = firstColumn(columns, "item name", "item", "name", "menu item");
            Integer priceColumn = firstColumn(columns, "price", "amount", "rate");
            if (nameColumn == null || priceColumn == null) {
                throw new InvalidRequestException(
                        "Excel needs a header row with an Item Name (or Item) and Price (or Amount) column.");
            }
            Integer categoryColumn = firstColumn(columns, "category", "category name", "section");
            Integer descriptionColumn = firstColumn(columns, "description", "details");
            List<MenuImportItem> items = new ArrayList<>();
            for (int rowIndex = header.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String name = cellValue(row, nameColumn, formatter).trim();
                if (name.isEmpty()) {
                    continue;
                }
                String priceText = cellValue(row, priceColumn, formatter).trim()
                        .replace(",", "").replace("₹", "").replaceAll("(?i)INR|Rs\\.?", "").trim();
                MenuImportItem item = new MenuImportItem();
                item.setItemName(name);
                item.setCategoryName(categoryColumn == null ? "" : cellValue(row, categoryColumn, formatter).trim());
                item.setDescription(descriptionColumn == null ? "" : cellValue(row, descriptionColumn, formatter).trim());
                try {
                    item.setPrice(new BigDecimal(priceText));
                } catch (NumberFormatException ex) {
                    throw new InvalidRequestException("Excel row " + (rowIndex + 1) + " has an invalid price.");
                }
                items.add(item);
            }
            return items;
        }
    }

    private List<MenuImportItem> parsePdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            List<MenuImportItem> items = new ArrayList<>();
            for (String line : text.split("\\R")) {
                String normalized = line.replaceAll("\\s{2,}", " ").trim();
                Matcher matcher = PDF_MENU_LINE.matcher(normalized);
                if (!matcher.matches()) {
                    continue;
                }
                String name = matcher.group(1).replaceAll("[|•·]+$", "").trim();
                if (name.isEmpty() || name.length() > 150) {
                    continue;
                }
                MenuImportItem item = new MenuImportItem();
                item.setItemName(name);
                item.setDescription("");
                try {
                    item.setPrice(new BigDecimal(matcher.group(2)));
                } catch (NumberFormatException ex) {
                    continue;
                }
                items.add(item);
            }
            return items;
        }
    }

    @Transactional
    public int importItems(Long restaurantId, MenuImportRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidRequestException("Add at least one valid menu item to import.");
        }
        if (request.getItems().size() > 500) {
            throw new InvalidRequestException("Import is limited to 500 menu items at a time.");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        MenuCategory selectedCategory = null;
        if (request.getDefaultCategoryId() != null) {
            selectedCategory = categoryRepository.findByIdAndRestaurantId(
                    request.getDefaultCategoryId(), restaurantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Selected menu category not found"));
        }
        String defaultCategoryName = request.getDefaultCategoryName() == null
                ? "" : request.getDefaultCategoryName().trim();
        if (selectedCategory == null && defaultCategoryName.isEmpty()
                && request.getItems().stream().anyMatch(item -> item.getCategoryName() == null
                        || item.getCategoryName().isBlank())) {
            throw new InvalidRequestException("Choose a default category for imported rows without a category.");
        }

        Map<String, MenuCategory> categories = new HashMap<>();
        List<MenuItem> items = new ArrayList<>();
        for (MenuImportItem imported : request.getItems()) {
            if (imported.getPrice() == null || imported.getPrice().signum() < 0
                    || imported.getItemName() == null || imported.getItemName().isBlank()) {
                throw new InvalidRequestException("Each imported item needs a name and a non-negative price.");
            }
            String categoryName = imported.getCategoryName() == null
                    ? "" : imported.getCategoryName().trim();
            MenuCategory category;
            if (!categoryName.isEmpty()) {
                category = getOrCreateCategory(restaurant, categoryName, categories);
            } else if (selectedCategory != null) {
                category = selectedCategory;
            } else {
                category = getOrCreateCategory(restaurant, defaultCategoryName, categories);
            }

            MenuItem item = new MenuItem();
            item.setItemName(imported.getItemName().trim());
            item.setDescription(imported.getDescription() == null ? "" : imported.getDescription().trim());
            item.setPrice(imported.getPrice());
            item.setAvailable(true);
            item.setDisplayOrder(0);
            item.setCategory(category);
            items.add(item);
        }
        itemRepository.saveAll(items);
        return items.size();
    }

    private MenuCategory getOrCreateCategory(
            Restaurant restaurant,
            String name,
            Map<String, MenuCategory> categories) {
        String key = name.toLowerCase(Locale.ROOT);
        return categories.computeIfAbsent(key, ignored -> categoryRepository
                .findByRestaurantIdAndCategoryNameIgnoreCase(restaurant.getId(), name)
                .orElseGet(() -> {
                    MenuCategory category = new MenuCategory();
                    category.setRestaurant(restaurant);
                    category.setCategoryName(name);
                    category.setDescription("");
                    category.setDisplayOrder(0);
                    return categoryRepository.save(category);
                }));
    }

    private String cellValue(Row row, Integer column, DataFormatter formatter) {
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private Integer firstColumn(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(name);
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    private String normalizeHeader(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ");
    }
}
