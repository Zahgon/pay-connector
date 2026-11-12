package uk.gov.pay.connector.web;

import org.eclipse.jetty.io.EofException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * A request entity that ends before its declared length (the client went away) is a bad request without an entity.
 */
@Component
public class EarlyEofExceptionMapper implements ExceptionMapper<EofException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EarlyEofExceptionMapper.class);

    @Override
    public ResponseEntity<?> toResponse(EofException exception) {
        LOGGER.debug("EOF Exception encountered - client disconnected during stream processing.", exception);
        return ResponseEntity.badRequest().build();
    }
}
