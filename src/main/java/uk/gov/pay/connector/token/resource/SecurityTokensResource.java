package uk.gov.pay.connector.token.resource;

import org.springframework.transaction.annotation.Transactional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.exception.TokenNotFoundException;
import uk.gov.pay.connector.token.model.domain.TokenResponse;

import jakarta.inject.Inject;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.web.MediaTypes;
import uk.gov.pay.connector.web.BaseUri;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import static uk.gov.pay.connector.util.ResponseUtil.noContentResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Secure token")
@RestController
public class SecurityTokensResource {

    private final Logger logger = LoggerFactory.getLogger(SecurityTokensResource.class);
    private final TokenDao tokenDao;
    private final ChargeService chargeService;

    @Inject
    public SecurityTokensResource(TokenDao tokenDao, ChargeService chargeService) {
        this.tokenDao = tokenDao;
        this.chargeService = chargeService;
    }

    @GetMapping("/v1/frontend/tokens/{chargeTokenId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Retrieve secure token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TokenResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public TokenResponse getToken(@Parameter(example = "a69a2cf3-d5d1-408f-b196-4b716767b507")
                                  @PathVariable("chargeTokenId") String chargeTokenId,
                                  HttpServletRequest request) {
        return tokenDao.findByTokenId(chargeTokenId)
                .filter(tokenEntity -> tokenEntity.getChargeEntity().getAuthorisationMode() != MOTO_API)
                .map(tokenEntity -> new TokenResponse(tokenEntity.isUsed(), chargeService.buildChargeResponse(BaseUri.from(request), tokenEntity.getChargeEntity())))
                .orElseThrow(() -> new TokenNotFoundException("Token invalid!"));
    }
    
    @PostMapping("/v1/frontend/tokens/{chargeTokenId}/used")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Transactional
    @Operation(
            summary = "Mark secure token as used",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public ResponseEntity<?> markTokenUsed(@Parameter(example = "a69a2cf3-d5d1-408f-b196-4b716767b507")
                                  @PathVariable("chargeTokenId") String chargeTokenId) {
        logger.debug("mark token used for token {}", chargeTokenId);
        return tokenDao.findByTokenId(chargeTokenId)
                .map(tokenEntity -> {
                    tokenEntity.setUsed(true);
                    return noContentResponse();
                })
                .orElseGet(() -> notFoundResponse("Token invalid!"));
    }

    @DeleteMapping("/v1/frontend/tokens/{chargeTokenId}")
    @Transactional
    @Operation(
            summary = "Delete secure token",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public ResponseEntity<?> deleteToken(@Parameter(example = "a69a2cf3-d5d1-408f-b196-4b716767b507")
                                @PathVariable("chargeTokenId") String chargeTokenId) {
        logger.debug("delete({})", chargeTokenId);
        tokenDao.findByTokenId(chargeTokenId)
                .ifPresent(tokenDao::remove);
        return noContentResponse();
    }
}
