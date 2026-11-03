package br.com.casamento.gift.service;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.gift.GiftItem;
import br.com.casamento.gift.dto.PixPaymentResponse;
import br.com.casamento.guest.service.QrCodeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class PixPayloadService {

    @Inject
    QrCodeService qrCodeService;

    public PixPaymentResponse create(Event event, GiftItem item) {
        if (item.priceAmount == null || item.priceAmount.signum() <= 0) {
            throw AppException.badRequest("GIFT_PRICE_REQUIRED", "Este presente ainda não tem um valor PIX configurado.");
        }
        String receiverName = normalize(event.pixMerchantName != null
                ? event.pixMerchantName : event.coupleNames, 25);
        String receiverCity = normalize(event.pixMerchantCity, 15);
        if (receiverName.isBlank() || receiverCity.isBlank()) {
            throw AppException.badRequest("PIX_SETTINGS_INCOMPLETE", "Configure o nome e a cidade do recebedor nas configurações PIX.");
        }
        String pixKey = normalizeKey(event.pixKey, event.pixKeyType);
        BigDecimal amount = item.priceAmount.setScale(2);
        String txid = UUID.randomUUID().toString().replace("-", "").substring(0, 25).toUpperCase(Locale.ROOT);
        String accountInfo = tlv("00", "br.gov.bcb.pix") + tlv("01", pixKey);
        String additionalData = tlv("05", txid);
        String payload = tlv("00", "01")
                + tlv("26", accountInfo)
                + tlv("52", "0000")
                + tlv("53", "986")
                + tlv("54", amount.toPlainString())
                + tlv("58", "BR")
                + tlv("59", receiverName)
                + tlv("60", receiverCity)
                + tlv("62", additionalData)
                + "6304";
        payload += String.format(Locale.ROOT, "%04X", crc16(payload));
        String qr = Base64.getEncoder().encodeToString(qrCodeService.generateQrCodePng(payload));
        return new PixPaymentResponse(item.title, amount, pixKey, event.pixKeyType,
                receiverName, receiverCity, payload, qr);
    }

    static int crc16(String value) {
        int crc = 0xFFFF;
        for (byte current : value.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (current & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return crc;
    }

    private String normalizeKey(String key, String type) {
        String value = key == null ? "" : key.trim();
        if ("CPF".equals(type)) {
            value = value.replaceAll("\\D", "");
            if (value.length() != 11) {
                throw AppException.badRequest("PIX_KEY_INVALID", "A chave PIX cadastrada como CPF deve conter 11 dígitos.");
            }
        }
        if (value.isBlank()) {
            throw AppException.badRequest("PIX_KEY_INVALID", "Configure uma chave PIX válida.");
        }
        return value;
    }

    private String normalize(String value, int maxLength) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return ascii.length() > maxLength ? ascii.substring(0, maxLength).trim() : ascii;
    }

    private String tlv(String id, String value) {
        if (value.length() > 99) {
            throw AppException.badRequest("PIX_PAYLOAD_INVALID", "O conteúdo do PIX excede o limite permitido.");
        }
        return id + String.format(Locale.ROOT, "%02d", value.length()) + value;
    }
}