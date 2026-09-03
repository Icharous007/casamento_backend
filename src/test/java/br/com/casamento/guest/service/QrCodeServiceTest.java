package br.com.casamento.guest.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService();

    @Test
    void generateQrCodePngShouldReturnNonEmptyPngBytes() {
        byte[] png = service.generateQrCodePng("https://example.com/save-the-date?event=abc");

        assertNotNull(png);
        assertTrue(png.length > 8);

        byte[] signature = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < signature.length; i++) {
            assertEquals(signature[i], png[i]);
        }
    }

    @Test
    void generateQrCodePngShouldSupportLongButReasonableContent() {
        String longUrl = "https://example.com/" + "a".repeat(400);
        byte[] png = service.generateQrCodePng(longUrl);

        assertNotNull(png);
        assertTrue(png.length > 8);
    }
}
