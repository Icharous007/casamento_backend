package br.com.casamento.guest.party.resource;

import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.guest.party.dto.AddPartyMemberRequest;
import br.com.casamento.support.GuestAuthTestSupport;
import br.com.casamento.support.TestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@DisplayName("GuestPartyResource API Tests")
class GuestPartyResourceTest {

    @Inject
    GuestAuthTestSupport authSupport;

    private GuestAuthTestSupport.GuestTokenData guestAuth;

    @BeforeEach
    @Transactional
    void setUp() {
        // Note: This test requires V011__guest_party_management.sql migration to be applied
        // Run: mvn quarkus:dev  (to apply migrations)
        // Then run: mvn test
        guestAuth = authSupport.createAuthenticatedGuest();
    }

    @Test
    @DisplayName("Should list party members (self only initially)")
    @Transactional
    void shouldListPartyMembers() {
        given()
                .header(guestAuth.headerName(), guestAuth.rawToken())
                .contentType(ContentType.JSON)
        .when()
                .get("/api/v1/me/party")
        .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].isSelf", equalTo(true));
    }

    @Test
    @DisplayName("Should add child without phone")
    @Transactional
    void shouldAddChild() {
        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "João Silva",
                null,
                "CHILD",
                (short) 7
        );

        given()
                .header(guestAuth.headerName(), guestAuth.rawToken())
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/api/v1/me/party")
        .then()
                .statusCode(201)
                .body("name", equalTo("João Silva"))
                .body("guestType", equalTo("CHILD"))
                .body("age", equalTo(7))
                .body("rsvpStatus", equalTo("PENDING"))
                .body("managedByMe", equalTo(true));
    }

    @Test
    @DisplayName("Should reject empty name")
    @Transactional
    void shouldRejectEmptyName() {
        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "  ",
                null,
                "CHILD",
                null
        );

        given()
                .header(guestAuth.headerName(), guestAuth.rawToken())
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/api/v1/me/party")
        .then()
                .statusCode(400)
                .body("code", equalTo("NAME_EMPTY"));
    }

    @Test
    @DisplayName("Should reject invalid guest type")
    @Transactional
    void shouldRejectInvalidGuestType() {
        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "João",
                null,
                "INVALID",
                null
        );

        given()
                .header(guestAuth.headerName(), guestAuth.rawToken())
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/api/v1/me/party")
        .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_GUEST_TYPE"));
    }

    @Test
    @DisplayName("Should require authentication")
    void shouldRequireAuthentication() {
        AddPartyMemberRequest request = new AddPartyMemberRequest(
                "João",
                null,
                "CHILD",
                null
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/api/v1/me/party")
        .then()
                .statusCode(401);
    }
}
