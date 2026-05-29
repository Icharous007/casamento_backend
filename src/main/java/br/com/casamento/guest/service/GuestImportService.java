package br.com.casamento.guest.service;

import br.com.casamento.auth.service.GuestTokenService;
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
    GuestTokenService tokenService;

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
        return processRows(event, rows, "CSV");
    }

    @Transactional
    public ImportResultResponse importExcel(Event event, InputStream inputStream) {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean firstRow = true;
            for (Row row : sheet) {
                if (firstRow) { firstRow = false; continue; } // skip header
                String name = cellString(row.getCell(0));
                String email = cellString(row.getCell(1));
                rows.add(new String[]{name, email});
            }
        } catch (Exception e) {
            return new ImportResultResponse(0, 0, 0, List.of(
                    new ImportResultResponse.ImportError(0, "Erro ao ler Excel: " + e.getMessage())
            ));
        }
        return processRows(event, rows, "Excel");
    }

    private ImportResultResponse processRows(Event event, List<String[]> rows, String source) {
        int imported = 0;
        int skipped = 0;
        List<ImportResultResponse.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String name = row.length > 0 ? row[0].trim() : "";
            if (name.isBlank()) {
                skipped++;
                continue;
            }

            // Check for duplicate name in this event
            long count = Guest.count("event.id = ?1 AND LOWER(name) = LOWER(?2)", event.id, name);
            if (count > 0) {
                errors.add(new ImportResultResponse.ImportError(i + 2,
                        "Convidado '" + name + "' já existe (linha " + (i + 2) + ")"));
                skipped++;
                continue;
            }

            Guest guest = new Guest();
            guest.event = event;
            guest.name = name;
            guest.status = "INVITED";
            guest.persist();

            tokenService.createToken(guest);
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
