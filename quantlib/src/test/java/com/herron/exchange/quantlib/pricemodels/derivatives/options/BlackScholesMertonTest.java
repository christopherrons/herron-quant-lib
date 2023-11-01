package com.herron.exchange.quantlib.pricemodels.derivatives.options;

import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.enums.OptionExerciseTyleEnum;
import com.herron.exchange.common.api.common.enums.OptionTypeEnum;
import com.herron.exchange.common.api.common.enums.SettlementTypeEnum;
import com.herron.exchange.common.api.common.messages.common.BusinessCalendar;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import com.herron.exchange.common.api.common.messages.pricing.ImmutableBlackScholesPriceModelParameters;
import com.herron.exchange.common.api.common.messages.refdata.ImmutableDefaultOptionInstrument;
import com.herron.exchange.common.api.common.messages.refdata.ImmutableMarket;
import com.herron.exchange.common.api.common.messages.refdata.ImmutableProduct;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.herron.exchange.common.api.common.enums.OptionTypeEnum.CALL;
import static com.herron.exchange.common.api.common.enums.OptionTypeEnum.PUT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BlackScholesMertonTest {

    @Test
    void test_option_put_price() {
        var option = createOption(PUT, 1001, Timestamp.from(LocalDate.of(2023, 12, 31)));
        var result = BlackScholesMerton.calculate(
                Timestamp.from(LocalDate.of(2023, 10, 31)),
                option,
                1000,
                0.01,
                0.01,
                0
        );
        assertEquals(1.31650, result.price(), 0.001);
        assertEquals(-0.43403, result.delta(), 0.001);
        assertEquals(0.09629, result.gamma(), 0.001);
        assertEquals(1.6079632, result.vega(), 0.001);
        assertEquals(-0.7270324, result.rho(), 0.001);
    }

    @Test
    void test_option_call_price() {
        var option = createOption(CALL, 1001, Timestamp.from(LocalDate.of(2023, 12, 31)));
        var result = BlackScholesMerton.calculate(
                Timestamp.from(LocalDate.of(2023, 10, 31)),
                option,
                1000,
                0.01,
                0.01,
                0
        );
        assertEquals(1.98806, result.price(), 0.001);
        assertEquals(0.56597, result.delta(), 0.001);
        assertEquals(0.09629, result.gamma(), 0.001);
        assertEquals(1.6079632, result.vega(), 0.001);
        assertEquals(0.9418482, result.rho(), 0.001);
    }


    private OptionInstrument createOption(OptionTypeEnum optionTypeEnum,
                                          double strikePrice,
                                          Timestamp maturityDate) {
        return ImmutableDefaultOptionInstrument.builder()
                .instrumentId("instrumendId")
                .underlyingInstrumentId("underlying")
                .settlementType(SettlementTypeEnum.PHYSICAL)
                .firstTradingDate(Timestamp.from(LocalDate.MIN))
                .lastTradingDate(Timestamp.from(LocalDate.MAX))
                .maturityDate(maturityDate)
                .strikePrice(Price.create(strikePrice))
                .optionType(optionTypeEnum)
                .optionExerciseStyle(OptionExerciseTyleEnum.EUROPEAN)
                .priceModelParameters(ImmutableBlackScholesPriceModelParameters.builder().build())
                .product(ImmutableProduct.builder().currency("eur").productId("product").market(ImmutableMarket.builder().marketId("market").businessCalendar(BusinessCalendar.defaultWeekendCalendar()).build()).build())
                .build();
    }
}