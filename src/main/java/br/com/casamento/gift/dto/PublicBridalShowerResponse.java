package br.com.casamento.gift.dto;

import java.util.List;

public record PublicBridalShowerResponse(String coupleNames, List<GiftItemResponse> items) {}