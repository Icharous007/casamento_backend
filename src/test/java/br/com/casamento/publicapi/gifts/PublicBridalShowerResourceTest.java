package br.com.casamento.publicapi.gifts;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.gift.GiftItem;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class PublicBridalShowerResourceTest {

    private UUID giftId;

    @BeforeEach
    @Transactional
    void setUp() {
        Event event = Event.findBySlug("casamento-2027");
        event.pixMerchantName = "GUSTAVO E MARIA LUIZA";
        event.pixMerchantCity = "SAO PAULO";
        GiftItem item = new GiftItem();
        item.event = event;
        item.title = "Jogo de panelas de teste";
        item.category = "BRIDAL_SHOWER";
        item.priceAmount = new BigDecimal("25.00");
        item.persist();
        giftId = item.id;
    }

    @Test
    void publicListPixAndRepeatedContributionsDoNotRequireAuthentication() {
        given()
        .when()
                .get("/api/v1/public/cha-de-panela")
        .then()
                .statusCode(200)
                .body("items.id", hasItem(giftId.toString()));

        given()
        .when()
                .get("/api/v1/public/cha-de-panela/gifts/" + giftId + "/pix")
        .then()
                .statusCode(200)
                .body("amount", notNullValue())
                .body("payload", containsString("540525.00"))
                .body("qrCodeBase64", notNullValue());

        Map<String, String> contribution = Map.of(
                "giverName", "Convidada Teste",
                "giverPhone", "11999999999"
        );
        for (int count = 0; count < 2; count++) {
            given()
                    .contentType(ContentType.JSON)
                    .body(contribution)
            .when()
                    .post("/api/v1/public/cha-de-panela/gifts/" + giftId + "/contributions")
            .then()
                    .statusCode(201)
                    .body("paymentStatus", org.hamcrest.Matchers.equalTo("REPORTED"));
        }
    }
}