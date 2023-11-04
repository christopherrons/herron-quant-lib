package com.herron.exchange.quantlib.pricemodels.derivatives.options;

import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.enums.OptionExerciseTyleEnum;
import com.herron.exchange.common.api.common.enums.OptionSubTypeEnum;
import com.herron.exchange.common.api.common.enums.OptionTypeEnum;
import com.herron.exchange.common.api.common.enums.SettlementTypeEnum;
import com.herron.exchange.common.api.common.messages.common.BusinessCalendar;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.PureNumber;
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

class Black76Test {

    @Test
    void test_option_put_implied_volatility() {
        var option = createOption(PUT, 1001, Timestamp.from(LocalDate.of(2023, 12, 31)));
        var iv = Black76.calculateImpliedVolatility(
                option.optionType(),
                option.strikePrice().getRealValue(),
                5.39332,
                1000,
                Black76.calculateTimeToMaturity(Timestamp.from(LocalDate.of(2023, 10, 31)), option),
                0.02
        );

        assertEquals(PureNumber.create(0.03), iv.scale(2));
    }

    @Test
    void test_option_put_price() {
        var vt = Timestamp.from(LocalDate.of(2023, 10, 31));
        var option = createOption(PUT, 1001, Timestamp.from(LocalDate.of(2023, 12, 31)));
        var result = Black76.calculateOptionPrice(
                vt,
                option.optionType(),
                option.strikePrice().getRealValue(),
                1000,
                0.03,
                Black76.calculateTimeToMaturity(vt, option),
                0.02
        );
        assertEquals(Price.create(5.39332).scale(5), result.price());
        assertEquals(PureNumber.create(-0.52827), result.sensitivity().delta());
        assertEquals(PureNumber.create(0.03233), result.sensitivity().gamma());
        assertEquals(PureNumber.create(1.62085), result.sensitivity().vega());
        assertEquals(PureNumber.create(-0.03956), result.sensitivity().theta());
        assertEquals(PureNumber.create(-0.89187), result.sensitivity().rho());
    }

    @Test
    void test_option_call_implied_volatility() {
        var option = createOption(CALL, 1001, Timestamp.from(LocalDate.of(2023, 12, 31)));
        var iv = Black76.calculateImpliedVolatility(
                option.optionType(),
                option.strikePrice().getRealValue(),
                4.39666,
                1000,
                Black76.calculateTimeToMaturity(Timestamp.from(LocalDate.of(2023, 10, 31)), option),
                0.02
        );

        assertEquals(PureNumber.create(0.03), iv.scale(2));
    }

    @Test
    void test_option_call_price() {
        var vt = Timestamp.from(LocalDate.of(2023, 10, 31));
        var option = createOption(CALL, 1001, Timestamp.from(LocalDate.of(2023, 12, 31)));
        var result = Black76.calculateOptionPrice(
                vt,
                option.optionType(),
                option.strikePrice().getRealValue(),
                1000,
                0.03,
                Black76.calculateTimeToMaturity(vt, option),
                0.02
        );
        assertEquals(Price.create(4.39666), result.price());
        assertEquals(PureNumber.create(0.46839), result.sensitivity().delta());
        assertEquals(PureNumber.create(0.03233), result.sensitivity().gamma());
        assertEquals(PureNumber.create(1.62085), result.sensitivity().vega());
        assertEquals(PureNumber.create(-0.03592).scale(5), result.sensitivity().theta());
        assertEquals(PureNumber.create(0.77545).scale(5), result.sensitivity().rho());
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
                .strikePrice(PureNumber.create(strikePrice))
                .optionType(optionTypeEnum)
                .optionSubType(OptionSubTypeEnum.OOE)
                .optionExerciseStyle(OptionExerciseTyleEnum.EUROPEAN)
                .priceModelParameters(ImmutableBlackScholesPriceModelParameters.builder().yieldCurveId("").build())
                .product(ImmutableProduct.builder().currency("eur").productId("product").market(ImmutableMarket.builder().marketId("market").businessCalendar(BusinessCalendar.defaultWeekendCalendar()).build()).build())
                .build();
    }
}