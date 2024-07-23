package com.herron.exchange.quantlib.pricemodels.fixedincome.bonds;

import com.herron.exchange.common.api.common.api.pricing.PriceModelResult;
import com.herron.exchange.common.api.common.api.referencedata.instruments.BondInstrument;
import com.herron.exchange.common.api.common.enums.DayCountConventionEnum;
import com.herron.exchange.common.api.common.math.parametricmodels.yieldcurve.YieldCurve;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.PureNumber;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import com.herron.exchange.common.api.common.messages.pricing.BondDiscountPriceModelResult;
import com.herron.exchange.common.api.common.messages.pricing.ImmutableBondDiscountPriceModelResult;
import com.herron.exchange.common.api.common.messages.pricing.ImmutableDiscountedPaymentResult;
import com.herron.exchange.quantlib.pricemodels.fixedincome.bonds.model.CouponPeriod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static com.herron.exchange.common.api.common.enums.EventType.SYSTEM;
import static com.herron.exchange.common.api.common.enums.Status.OK;
import static java.time.temporal.ChronoUnit.DAYS;

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

        List<CouponPeriod> coupons = CouponCalculationUtils.generateCouponPeriods(bondInstrument);
        return calculateBondPrice(bondInstrument, yieldAtMaturityExtractor, valuationTime, coupons);
    }

    private static PriceModelResult calculateBondPrice(BondInstrument bondInstrument,
                                                       DoubleUnaryOperator yieldAtMaturityExtractor,
                                                       Timestamp valuationTime,
                                                       List<CouponPeriod> coupons) {
        double presentValue = 0;
        double accruedInterest = 0;
        List<BondDiscountPriceModelResult.DiscountedPaymentResult> discountPaymentResults = new ArrayList<>();
        Timestamp maturityDate = bondInstrument.maturityDate();
        for (CouponPeriod coupon : coupons) {
            if (coupon.endDate().isBefore(valuationTime)) {
                continue;
            }

            if (coupon.isInPeriod(valuationTime)) {
                accruedInterest += calculateAccruedInterest(bondInstrument.couponRate(), coupon.startDate(), valuationTime, bondInstrument.priceModelParameters().dayCountConvention());
            }

            var discountedCouponPayment = calculatePaymentValue(bondInstrument, coupon, maturityDate, yieldAtMaturityExtractor);
            discountPaymentResults.add(discountedCouponPayment);
            presentValue += discountedCouponPayment.couponValuePercentage();
        }

        var nominalValuePeriod = new CouponPeriod(valuationTime, maturityDate, 1);
        var discountedNominalPayment = calculatePaymentValue(bondInstrument, nominalValuePeriod, maturityDate, yieldAtMaturityExtractor);
        discountPaymentResults.add(discountedNominalPayment);
        presentValue += discountedNominalPayment.couponValuePercentage();

        var accruedInterestAmount = bondInstrument.nominalValue().multiply(accruedInterest).getRealValue();
        var presentValueAmount = bondInstrument.nominalValue().multiply(presentValue).getRealValue();
        return ImmutableBondDiscountPriceModelResult.builder()
                .accruedInterest(PureNumber.create(accruedInterestAmount))
                .cleanPrice(Price.create(presentValueAmount))
                .dirtyPrice(Price.create(presentValueAmount + accruedInterestAmount))
                .discountedPaymentResult(discountPaymentResults)
                .dayCountConvention(bondInstrument.priceModelParameters().dayCountConvention())
                .eventType(SYSTEM)
                .timeOfEvent(Timestamp.now())
                .marketTime(valuationTime)
                .status(OK)
                .build();
    }

    private static BondDiscountPriceModelResult.DiscountedPaymentResult calculatePaymentValue(BondInstrument bondInstrument,
                                                                                              CouponPeriod period,
                                                                                              Timestamp maturityDate,
                                                                                              DoubleUnaryOperator yieldAtMaturityExtractor) {
        double timeToMaturity = DAYS.between(period.startDate().toLocalDate(), maturityDate.toLocalDate()) / bondInstrument.priceModelParameters().dayCountConvention().getDaysPerYear();
        double yieldAtTimeToMaturity = yieldAtMaturityExtractor.applyAsDouble(timeToMaturity);
        double discountFactor = bondInstrument.priceModelParameters().compoundingMethod().calculateValue(yieldAtTimeToMaturity, timeToMaturity, bondInstrument.couponAnnualFrequency());
        double couponValuePercentage = period.couponRate() / discountFactor;
        return ImmutableDiscountedPaymentResult.builder()
                .start(period.startDate())
                .end(period.endDate())
                .discountFactor(discountFactor)
                .yieldToMaturity(yieldAtTimeToMaturity)
                .timeToMaturity(timeToMaturity)
                .couponValuePercentage(couponValuePercentage)
                .build();
    }

    private static double calculateAccruedInterest(PureNumber annualCouponRate, Timestamp startDate, Timestamp valuationTime, DayCountConventionEnum dayCountConvention) {
        return annualCouponRate.multiply(dayCountConvention.calculateDayCountFraction(startDate, valuationTime)).getRealValue();
    }
}
