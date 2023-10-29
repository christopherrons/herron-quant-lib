package com.herron.exchange.quantlib.pricemodels.fixedincome.bonds;

import com.herron.exchange.common.api.common.api.pricing.PriceModelResult;
import com.herron.exchange.common.api.common.api.referencedata.instruments.BondInstrument;
import com.herron.exchange.common.api.common.curves.YieldCurve;
import com.herron.exchange.common.api.common.enums.DayCountConventionEnum;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import com.herron.exchange.common.api.common.messages.pricing.ImmutableBondDiscountPriceModelResult;
import com.herron.exchange.quantlib.pricemodels.fixedincome.bonds.model.CouponPeriod;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static com.herron.exchange.common.api.common.enums.EventType.SYSTEM;
import static com.herron.exchange.common.api.common.enums.Status.OK;
import static java.time.temporal.ChronoUnit.YEARS;

public class BondDiscountingPriceModel {

    public static PriceModelResult calculate(BondInstrument instrument, YieldCurve yieldCurve, Timestamp valuationTime) {
        return calculateBondPrice(instrument, yieldCurve::getYield, valuationTime);
    }

    public static PriceModelResult calculate(BondInstrument instrument, double yieldPerYear, Timestamp valuationTime) {
        return calculateBondPrice(instrument, timeToMaturity -> yieldPerYear, valuationTime);
    }

    private static PriceModelResult calculateBondPrice(BondInstrument bondInstrument,
                                                       DoubleUnaryOperator yieldAtMaturityExtractor,
                                                       Timestamp valuationTime) {
        if (valuationTime.isBefore(bondInstrument.startDate())) {
            valuationTime = bondInstrument.startDate();
        }

        List<CouponPeriod> periods = CouponCalculationUtils.generateCouponPeriods(bondInstrument);
        return calculateBondPrice(bondInstrument, yieldAtMaturityExtractor, valuationTime, periods);
    }

    private static PriceModelResult calculateBondPrice(BondInstrument bondInstrument,
                                                       DoubleUnaryOperator yieldAtMaturityExtractor,
                                                       Timestamp valuationTime,
                                                       List<CouponPeriod> periods) {
        double presentValue = 0;
        double accruedInterest = 0;
        Timestamp maturityDate = bondInstrument.maturityDate();
        for (CouponPeriod period : periods) {
            if (period.endDate().isBefore(valuationTime)) {
                continue;
            }

            if (period.isInPeriod(valuationTime)) {
                accruedInterest += calculateAccruedInterest(bondInstrument.couponRate(), period.startDate(), valuationTime, bondInstrument.priceModelParameters().dayCountConvention());
            }

            presentValue += period.couponRate() / calculateDiscountFactor(bondInstrument, period.startDate(), maturityDate, yieldAtMaturityExtractor);
        }

        var discountedFaceValue = 1 / calculateDiscountFactor(bondInstrument, valuationTime, maturityDate, yieldAtMaturityExtractor);
        presentValue += discountedFaceValue;

        var accruedInterestAmount = accruedInterest * bondInstrument.nominalValue().getRealValue();
        var presentValueAmount = presentValue * bondInstrument.nominalValue().getRealValue();
        return ImmutableBondDiscountPriceModelResult.builder()
                .accruedInterest(accruedInterestAmount)
                .cleanPrice(Price.create(presentValueAmount))
                .price(Price.create(presentValueAmount + accruedInterestAmount))
                .eventType(SYSTEM)
                .timeOfEvent(Timestamp.now())
                .status(OK)
                .build();
    }

    private static double calculateDiscountFactor(BondInstrument bondInstrument, Timestamp start, Timestamp end, DoubleUnaryOperator yieldAtMaturityExtractor) {
        var timeToMaturity = YEARS.between(start.toLocalDate(), end.toLocalDate());
        double yieldAtTimeToMaturity = yieldAtMaturityExtractor.applyAsDouble(timeToMaturity);
        return bondInstrument.priceModelParameters().compoundingMethod().calculateValue(yieldAtTimeToMaturity, timeToMaturity, bondInstrument.couponAnnualFrequency());
    }

    private static double calculateAccruedInterest(double annualCouponRate, Timestamp startDate, Timestamp valuationTime, DayCountConventionEnum dayCountConvention) {
        return annualCouponRate * dayCountConvention.calculateDayCountFraction(startDate, valuationTime);
    }
}
