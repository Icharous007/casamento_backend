package br.com.casamento.guest.service;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.guest.dto.ImportResultResponse;
import com.opencsv.CSVReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GuestImportService {

    @Inject
    PhoneNumberService phoneNumberService;

    /**
     * Expected CSV columns: name (required), phone (optional).
     */
    @Transactional
    public ImportResultResponse importCsv(Event event, InputStream inputStream) {
        List<String[]> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String[] header = reader.readNext(); // skip header
            if (header == null) return empty();
            String[] line;
            while ((line = reader.readNext()) != null) {
                rows.add(line);
            }
        } catch (Exception e) {
            return new ImportResultResponse(0, 0, 0, List.of(
                    new ImportResultResponse.ImportError(0, "Erro ao ler CSV: " + e.getMessage())
            ));
        }
        return processRows(event, rows);
    }

    /**
     * Expected XLSX columns: col 0 = name, col 1 = phone.
     */
    @Transactional
    public ImportResultResponse importExcel(Event event, InputStream inputStream) {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean firstRow = true;
            for (Row row : sheet) {
                if (firstRow) { firstRow = false; continue; } // skip header
                String name = cellString(row.getCell(0));
                String phone = cellString(row.getCell(1));
                rows.add(new String[]{name, phone});
            }
        } catch (Exception e) {
            return new ImportResultResponse(0, 0, 0, List.of(
                    new ImportResultResponse.ImportError(0, "Erro ao ler Excel: " + e.getMessage())
            ));
        }
        return processRows(event, rows);
    }

    private ImportResultResponse processRows(Event event, List<String[]> rows) {
        int imported = 0;
        int skipped = 0;
        List<ImportResultResponse.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String name = row.length > 0 ? row[0].trim() : "";
            String rawPhone = row.length > 1 ? row[1].trim() : "";

            if (name.isBlank()) {
                skipped++;
                continue;
            }

            String phoneE164 = phoneNumberService.normalize(rawPhone);

            // Enforce unique phone per event
            if (phoneE164 != null && Guest.findByEventAndPhone(event.id, phoneE164) != null) {
                errors.add(new ImportResultResponse.ImportError(i + 2,
                        "Telefone '" + rawPhone + "' já existe para este evento (linha " + (i + 2) + ")"));
                skipped++;
                continue;
            }

            // Allow duplicate names (phone is now the identity), but warn about it
            Guest guest = new Guest();
            guest.event = event;
            guest.name = name;
            guest.phoneE164 = phoneE164;
            guest.source = "IMPORTED";
            guest.status = "INVITED";
            guest.persist();

            imported++;
        }

        return new ImportResultResponse(imported, skipped, rows.size(), errors);
    }

    private String cellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }

    private ImportResultResponse empty() {
        return new ImportResultResponse(0, 0, 0, List.of());
    }
}
