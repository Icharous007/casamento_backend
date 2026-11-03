package br.com.casamento.gift.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PixPayloadServiceTest {

    @Test
    void crc16UsesTheEmvQrCodeCrcVariant() {
        assertEquals(0x29B1, PixPayloadService.crc16("123456789"));
    }
}
