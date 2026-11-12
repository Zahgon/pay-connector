package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.cardtype.dao.CardTypeEntityBuilder;
import uk.gov.pay.connector.cardtype.resource.CardTypesResource;
import uk.gov.pay.connector.rules.ResourceTestExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class PayersCardTypesResourceIT {
    private static final CardTypeDao mockedDao = mock(CardTypeDao.class);
    @RegisterExtension
    public static ResourceTestExtension resources = ResourceTestExtension.withResources(new CardTypesResource(mockedDao));
    private CardTypeEntity cardTypeEntity;

    @BeforeEach
    public void setUp() {
        cardTypeEntity = CardTypeEntityBuilder.aCardTypeEntity().build();
        when(mockedDao.findAll()).thenReturn(Collections.singletonList(cardTypeEntity));
    }

    @AfterEach
    public void tearDown() {
        reset(mockedDao);
    }

    @Test
    public void shouldGetAllCardTypesWhenCardTypesExist() {
        final Map<String, List<CardTypeEntity>> response = resources.target("/v1/api/card-types")
                .request()
                .get()
                .readEntity(new TypeReference<Map<String, List<CardTypeEntity>>>() {
                });
        List<CardTypeEntity> cardTypeEntities = response.get("card_types");
        assertThat(cardTypeEntities, hasSize(1));
        CardTypeEntity visaCard = cardTypeEntities.getFirst();
        assertThat(visaCard, is(cardTypeEntity));
    }
}
