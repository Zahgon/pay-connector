package uk.gov.pay.connector.gatewayaccountcredentials.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;


import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class NoCredentialsExistForProviderExceptionMapper implements ExceptionMapper<NoCredentialsExistForProviderException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoCredentialsExistForProviderExceptionMapper.class);
    
    @Override
    public ResponseEntity<ErrorResponse> toResponse(NoCredentialsExistForProviderException exception) {
        LOGGER.info(exception.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, exception.getMessage());
        return ResponseEntity.status(400)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
