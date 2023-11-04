package com.herron.exchange.quantlib.pricemodels.derivatives.options;

import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.enums.DayCountConventionEnum;
import com.herron.exchange.common.api.common.enums.OptionTypeEnum;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.PureNumber;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import com.herron.exchange.common.api.common.messages.pricing.BlackScholesPriceModelResult;
import com.herron.exchange.common.api.common.messages.pricing.ImmutableBlackScholesPriceModelResult;
import com.herron.exchange.common.api.common.messages.pricing.ImmutableOptionGreeks;
import org.apache.commons.math3.distribution.NormalDistribution;

import static com.herron.exchange.common.api.common.enums.EventType.SYSTEM;
import static com.herron.exchange.common.api.common.enums.Status.OK;
import static java.time.temporal.ChronoUnit.DAYS;

public class BlackScholesMerton {
    private static final double DAYS_PER_YEAR = DayCountConventionEnum.ACT365.getDaysPerYear();
    private static final double IMPLIED_VOLATILITY_START_GUESS = 0.01;
    private static final int IMPLIED_VOLATILITY_MAX_ITERATIONS = 100000;
    private static final double IMPLIED_VOLATILITY_THRESHOLD = 0.0001;
    private static final NormalDistribution STANDARD_NORMAL_DISTRIBUTION = new NormalDistribution();

    public static double calculateTimeToMaturity(Timestamp valuationTime, OptionInstrument optionInstrument) {
        return DAYS.between(valuationTime.toLocalDate(), optionInstrument.maturityDate().toLocalDate()) / DAYS_PER_YEAR;
    }

    public static BlackScholesPriceModelResult calculateOptionPrice(Timestamp valuationTime,
                                                                    OptionTypeEnum optionType,
                                                                    double strikePrice,
                                                                    double underlyingPrice,
                                                                    double volatility,
                                                                    double timeToMaturity,
                                                                    double riskFreeRate,
                                                                    double dividendYield) {
        var commonCalculations = CommonCalculations.from(underlyingPrice, strikePrice, riskFreeRate, dividendYield, volatility, timeToMaturity);

        double optionPrice = calculateOptionPrice(optionType, underlyingPrice, strikePrice, commonCalculations);
        double delta = calculateDelta(optionType, commonCalculations);
        double theta = calculateTheta(optionType, underlyingPrice, volatility, strikePrice, timeToMaturity, riskFreeRate, dividendYield, commonCalculations);
        double vega = calculateVega(underlyingPrice, timeToMaturity, commonCalculations);
        double gamma = calculateGamma(underlyingPrice, volatility, timeToMaturity, commonCalculations);
        double rho = calculateRho(optionType, strikePrice, timeToMaturity, commonCalculations);
        return ImmutableBlackScholesPriceModelResult.builder()
                .price(Price.create(optionPrice).scale(5))
                .sensitivity(ImmutableOptionGreeks.builder()
                        .delta(PureNumber.create(delta).scale(5))
                        .theta(PureNumber.create(theta).scale(5))
                        .vega(PureNumber.create(vega).scale(5))
                        .gamma(PureNumber.create(gamma).scale(5))
                        .rho(PureNumber.create(rho).scale(5))
                        .build())
                .eventType(SYSTEM)
                .timeOfEvent(Timestamp.now())
                .marketTime(valuationTime)
                .status(OK)
                .build();
    }

    public static PureNumber calculateImpliedVolatility(Timestamp valuationTime,
                                                        OptionInstrument optionInstrument,
                                                        double marketPrice,
                                                        double underlyingPrice,
                                                        double riskFreeRate,
                                                        double dividendYield) {
        double strikePrice = optionInstrument.strikePrice().getRealValue();
        double timeToMaturity = calculateTimeToMaturity(valuationTime, optionInstrument);
        double impliedVolatility = IMPLIED_VOLATILITY_START_GUESS;

        for (int i = 0; i < IMPLIED_VOLATILITY_MAX_ITERATIONS; i++) {
            var commonCalculations = CommonCalculations.from(underlyingPrice, strikePrice, riskFreeRate, dividendYield, impliedVolatility, timeToMaturity);
            double theoreticalPrice = calculateOptionPrice(optionInstrument.optionType(), underlyingPrice, strikePrice, commonCalculations);
            double difference = theoreticalPrice - marketPrice;
            if (Math.abs(difference) <= IMPLIED_VOLATILITY_THRESHOLD) {
                break;
            }
            double vega = calculateVega(underlyingPrice, timeToMaturity, commonCalculations);
            impliedVolatility = Math.max(0, impliedVolatility - (difference / (vega * 100)));
        }

        return PureNumber.create(impliedVolatility);
    }

    private static double calculateOptionPrice(OptionTypeEnum optionTypeEnum,
                                               double underlyingPrice,
                                               double strikePrice,
                                               CommonCalculations commonCalculations) {
        return switch (optionTypeEnum) {
            case CALL -> calculateCallOptionPrice(underlyingPrice, strikePrice, commonCalculations);
            case PUT -> calculatePutOptionPrice(underlyingPrice, strikePrice, commonCalculations);
        };
    }

    private static double calculatePutOptionPrice(double underlyingPrice,
                                                  double strikePrice,
                                                  CommonCalculations commonCalculations) {
        return strikePrice * commonCalculations.compoundedRiskFreeRate * STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(-commonCalculations.d2) -
                underlyingPrice * commonCalculations.compoundedYield * STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(-commonCalculations.d1);
    }

    private static double calculateCallOptionPrice(double underlyingPrice,
                                                   double strikePrice,
                                                   CommonCalculations commonCalculations) {
        return underlyingPrice * commonCalculations.compoundedYield * commonCalculations.cdfNormD1 -
                strikePrice * commonCalculations.compoundedRiskFreeRate * commonCalculations.cdfNormD2;
    }

    private static double calculateDelta(OptionTypeEnum optionTypeEnum,
                                         CommonCalculations commonCalculation) {
        return switch (optionTypeEnum) {
            case CALL -> commonCalculation.compoundedYield * commonCalculation.cdfNormD1;
            case PUT -> commonCalculation.compoundedYield * (commonCalculation.cdfNormD1 - 1);
        };
    }

    private static double calculateGamma(double underlyingPrice,
                                         double volatility,
                                         double timeToMaturity,
                                         CommonCalculations commonCalculations) {
        var rise = commonCalculations.compoundedYield * commonCalculations.pdfNormD1;
        var run = underlyingPrice * volatility * Math.sqrt(timeToMaturity);
        return rise / run;
    }

    private static double calculateVega(double underlyingPrice,
                                        double timeToMaturity,
                                        CommonCalculations commonCalculations) {
        var rise = commonCalculations.compoundedYield * underlyingPrice * Math.sqrt(timeToMaturity) * commonCalculations.pdfNormD1;
        return rise / 100;
    }

    private static double calculateRho(OptionTypeEnum optionTypeEnum,
                                       double strikePrice,
                                       double timeToMaturity,
                                       CommonCalculations commonCalculations) {
        return switch (optionTypeEnum) {
            case CALL -> strikePrice * timeToMaturity * commonCalculations.compoundedRiskFreeRate * commonCalculations.cdfNormD2 * (1 / 100.0);
            case PUT ->
                    strikePrice * timeToMaturity * commonCalculations.compoundedRiskFreeRate * STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(-commonCalculations.d2) * (-1 / 100.0);
        };
    }

    private static double calculateTheta(OptionTypeEnum optionTypeEnum,
                                         double underlyingPrice,
                                         double volatility,
                                         double strikePrice,
                                         double timeToMaturity,
                                         double riskFreeRate,
                                         double dividendYield,
                                         CommonCalculations commonCalculations) {
        var sharedPart = -underlyingPrice * volatility * Math.exp(-dividendYield * timeToMaturity) * commonCalculations.pdfNormD1 / (2 * Math.sqrt(timeToMaturity));
        var sidePart = switch (optionTypeEnum) {
            case CALL -> -riskFreeRate * strikePrice * commonCalculations.compoundedRiskFreeRate * commonCalculations.pdfNormD2 +
                    dividendYield * underlyingPrice * commonCalculations.compoundedYield * commonCalculations.cdfNormD1;
            case PUT ->
                    riskFreeRate * strikePrice * commonCalculations.compoundedRiskFreeRate * STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(-commonCalculations.d2) -
                            dividendYield * underlyingPrice * commonCalculations.compoundedYield * STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(-commonCalculations.d1);
        };

        return (1 / DAYS_PER_YEAR) * (sharedPart + sidePart);
    }

    private record CommonCalculations(double d1,
                                      double d2,
                                      double cdfNormD1,
                                      double cdfNormD2,
                                      double pdfNormD1,
                                      double pdfNormD2,
                                      double compoundedYield,
                                      double compoundedRiskFreeRate) {

        public static CommonCalculations from(double underlyingPrice,
                                              double strikePrice,
                                              double riskFreeRate,
                                              double dividendYield,
                                              double volatility,
                                              double timeToMaturity) {
            var d1 = d1(underlyingPrice, strikePrice, volatility, dividendYield, riskFreeRate, timeToMaturity);
            var d2 = d2(d1, volatility, timeToMaturity);
            var compoundedYield = Math.exp(-dividendYield * timeToMaturity);
            var compoundedRiskFreeRate = Math.exp(-riskFreeRate * timeToMaturity);
            return new CommonCalculations(
                    d1,
                    d2,
                    STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(d1),
                    STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(d2),
                    STANDARD_NORMAL_DISTRIBUTION.density(d1),
                    STANDARD_NORMAL_DISTRIBUTION.density(d2),
                    compoundedYield,
                    compoundedRiskFreeRate
            );
        }

        private static double d1(double underlyingPrice,
                                 double strikePrice,
                                 double volatility,
                                 double dividendYield,
                                 double riskFreeRate,
                                 double timeToMaturity) {

            var rise = Math.log(underlyingPrice / strikePrice) + (riskFreeRate - dividendYield + (volatility * volatility * 0.5)) * timeToMaturity;
            var run = volatility * Math.sqrt(timeToMaturity);
            return rise / run;
        }

        private static double d2(double d1, double volatility, double timeToMaturity) {
            return d1 - volatility * Math.sqrt(timeToMaturity);
        }
    }
}
