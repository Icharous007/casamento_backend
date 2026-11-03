package br.com.casamento.gift.dto;

import br.com.casamento.domain.event.Event;

public record PixSettingsResponse(String pixKey, String pixKeyType, String merchantName, String merchantCity) {
    public static PixSettingsResponse from(Event event) {
        return new PixSettingsResponse(event.pixKey, event.pixKeyType,
                event.pixMerchantName, event.pixMerchantCity);
    }
}