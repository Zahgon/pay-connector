package uk.gov.pay.connector.cardtype.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;

import jakarta.inject.Inject;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import uk.gov.pay.connector.web.MediaTypes;
import org.springframework.http.ResponseEntity;

import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
public class CardTypesResource {

    private final CardTypeDao cardTypeDao;

    @Inject
    public CardTypesResource(CardTypeDao cardTypeDao) {
        this.cardTypeDao = cardTypeDao;
    }

    @GetMapping("/v1/api/card-types")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List all card types",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = CardTypesResponse.class)))
            }
    )
    public ResponseEntity<?> getCardTypes() {
        return successResponseWithEntity(CardTypesResponse.of(cardTypeDao.findAll()));
    }
}
