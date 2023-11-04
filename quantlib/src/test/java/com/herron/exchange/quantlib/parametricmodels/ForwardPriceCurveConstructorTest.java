package com.herron.exchange.quantlib.parametricmodels;

import com.herron.exchange.common.api.common.api.referencedata.instruments.Instrument;
import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.enums.*;
import com.herron.exchange.common.api.common.messages.common.BusinessCalendar;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.PureNumber;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import com.herron.exchange.common.api.common.messages.pricing.ImmutableBlackScholesPriceModelParameters;
import com.herron.exchange.common.api.common.messages.refdata.*;
import com.herron.exchange.common.api.common.parametricmodels.yieldcurve.YieldCurve;
import com.herron.exchange.common.api.common.parametricmodels.yieldcurve.model.YieldCurveModelParameters;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.herron.exchange.common.api.common.enums.DayCountConventionEnum.ACT365;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ForwardPriceCurveConstructorTest {
    private static final Timestamp VT = Timestamp.from(LocalDate.of(2023, 11, 3));

    @Test
    void test_forward_curve_construction() {
        var underlying = createUnderlying();
        var call_4_2023_11_17 = createOption(OptionTypeEnum.CALL, 4.00, Timestamp.from(LocalDate.of(2023, 11, 17)));
        var put_4_2023_11_17 = createOption(OptionTypeEnum.PUT, 4.00, Timestamp.from(LocalDate.of(2023, 11, 17)));
        var call_4_2023_12_15 = createOption(OptionTypeEnum.CALL, 4.00, Timestamp.from(LocalDate.of(2023, 12, 15)));
        var put_4_2023_12_15 = createOption(OptionTypeEnum.PUT, 4.00, Timestamp.from(LocalDate.of(2023, 12, 15)));
        var call_4_2024_01_19 = createOption(OptionTypeEnum.CALL, 4.00, Timestamp.from(LocalDate.of(2024, 1, 19)));
        var put_4_2024_01_19 = createOption(OptionTypeEnum.PUT, 4.00, Timestamp.from(LocalDate.of(2024, 1, 19)));
        var options = List.of(
                call_4_2023_11_17,
                put_4_2023_11_17,
                call_4_2023_12_15,
                put_4_2023_12_15,
                call_4_2024_01_19,
                put_4_2024_01_19
        );
        Map<Instrument, Price> instrumentPriceMap = Map.of(
                underlying, Price.create(4.67),
                call_4_2023_11_17, Price.create(0.73),
                put_4_2023_11_17, Price.create(0.05),
                call_4_2023_12_15, Price.create(0.64),
                put_4_2023_12_15, Price.create(0.08),
                call_4_2024_01_19, Price.create(0.53),
                put_4_2024_01_19, Price.create(0.40)
        );

        var curve = ForwardPriceCurveConstructor.construct(
                VT,
                underlying,
                options,
                instrumentPriceMap,
                createTestCurve()
        );

        assertEquals(4.598, curve.getForwardPrice(0.1), 0.001);
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

    public Instrument createUnderlying() {
        return ImmutableDefaultEquityInstrument.builder()
                .instrumentId("instrumentId")
                .product(ImmutableProduct.builder().currency("eur").productId("product").market(ImmutableMarket.builder().marketId("market").businessCalendar(BusinessCalendar.defaultWeekendCalendar()).build()).build())
                .firstTradingDate(Timestamp.from(LocalDate.MIN))
                .lastTradingDate(Timestamp.from(LocalDate.MAX))
                .priceModelParameters(ImmutableIntangiblePriceModelParameters.builder().build())
                .build();
    }

    private YieldCurve createTestCurve() {
        LocalDate startDate = VT.toLocalDate();
        var dayCountConvention = ACT365;
        List<LocalDate> maturityDates = new ArrayList<>();
        maturityDates.add(startDate.plusDays((long) dayCountConvention.getDaysPerYear()));
        maturityDates.add(startDate.plusDays((long) dayCountConvention.getDaysPerYear() * 2));
        maturityDates.add(startDate.plusDays((long) dayCountConvention.getDaysPerYear() * 3));
        maturityDates.add(startDate.plusDays((long) dayCountConvention.getDaysPerYear() * 4));
        maturityDates.add(startDate.plusDays((long) dayCountConvention.getDaysPerYear() * 5));
        maturityDates.add(startDate.plusDays((long) dayCountConvention.getDaysPerYear() * 10));
        maturityDates.add(startDate.plusDays((long) dayCountConvention.getDaysPerYear() * 20));
        maturityDates.add(startDate.plusDays((long) dayCountConvention.getDaysPerYear() * 30));
        maturityDates.add(startDate.plusDays((long) dayCountConvention.getDaysPerYear() * 50));
        double[] yields = new double[]{0.01, 0.015, 0.02, 0.03, 0.035, 0.035, 0.04, 0.04, 0.045};
        var parameters = YieldCurveModelParameters.create(dayCountConvention,
                InterpolationMethod.CUBIC_SPLINE,
                VT.toLocalDate(),
                maturityDates.get(0),
                maturityDates.toArray(new LocalDate[0]),
                yields
        );
        return YieldCurve.create("id", parameters);
    }
}