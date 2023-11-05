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

public class Black76 {
    private static final double DAYS_PER_YEAR = DayCountConventionEnum.ACT365.getDaysPerYear();
    private static final double IMPLIED_VOLATILITY_VALUE_MIN = 0.0;
    private static final double IMPLIED_VOLATILITY_VALUE_MAX = 5.0;
    private static final int IMPLIED_VOLATILITY_MAX_ITERATIONS = 1000;
    private static final double IMPLIED_VOLATILITY_THRESHOLD = 0.0001;
    private static final NormalDistribution STANDARD_NORMAL_DISTRIBUTION = new NormalDistribution();

    public static double calculateTimeToMaturity(Timestamp valuationTime, OptionInstrument optionInstrument) {
        return calculateTimeToMaturity(valuationTime, optionInstrument.maturityDate());
    }

    public static double calculateTimeToMaturity(Timestamp valuationTime, Timestamp maturityDate) {
        return DAYS.between(valuationTime.toLocalDate(), maturityDate.toLocalDate()) / DAYS_PER_YEAR;
    }

    public static BlackScholesPriceModelResult calculateOptionPrice(Timestamp valuationTime,
                                                                    OptionTypeEnum optionType,
                                                                    double strikePrice,
                                                                    double forwardPrice,
                                                                    double volatility,
                                                                    double timeToMaturity,
                                                                    double riskFreeRate) {
        var commonCalculations = CommonCalculations.from(forwardPrice, strikePrice, riskFreeRate, volatility, timeToMaturity);

        double optionPrice = calculateOptionPrice(optionType, forwardPrice, strikePrice, commonCalculations);
        double delta = calculateDelta(optionType, commonCalculations);
        double theta = calculateTheta(optionType, forwardPrice, volatility, strikePrice, timeToMaturity, riskFreeRate, commonCalculations);
        double vega = calculateVega(forwardPrice, timeToMaturity, commonCalculations);
        double gamma = calculateGamma(forwardPrice, volatility, timeToMaturity, commonCalculations);
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

    public static PureNumber calculateImpliedVolatility(OptionTypeEnum optionType,
                                                        double strikePrice,
                                                        double marketPrice,
                                                        double forwardPrice,
                                                        double timeToMaturity,
                                                        double riskFreeRate) {
        double impliedVolatility = calculateInitialGuess(optionType,
                strikePrice,
                marketPrice,
                forwardPrice,
                timeToMaturity,
                riskFreeRate
        );

        for (int i = 0; i < IMPLIED_VOLATILITY_MAX_ITERATIONS; i++) {
            var commonCalculations = CommonCalculations.from(forwardPrice, strikePrice, riskFreeRate, impliedVolatility, timeToMaturity);
            double theoreticalPrice = calculateOptionPrice(optionType, forwardPrice, strikePrice, commonCalculations);
            double vega = calculateVega(forwardPrice, timeToMaturity, commonCalculations);
            double priceDifference = theoreticalPrice - marketPrice;
            double updatedImpliedVolatility = impliedVolatility - (priceDifference / (vega * 100));
            updatedImpliedVolatility = Math.max(0, Math.min(updatedImpliedVolatility, 2));
            double ivDifference = updatedImpliedVolatility - impliedVolatility;
            impliedVolatility = updatedImpliedVolatility;
            if (Math.abs(priceDifference) <= IMPLIED_VOLATILITY_THRESHOLD || Math.abs(ivDifference) <= IMPLIED_VOLATILITY_THRESHOLD) {
                break;
            }
        }

        return PureNumber.create(impliedVolatility);
    }

    private static double calculateInitialGuess(OptionTypeEnum optionType,
                                                double strikePrice,
                                                double marketPrice,
                                                double forwardPrice,
                                                double timeToMaturity,
                                                double riskFreeRate) {
        double lowerBound = IMPLIED_VOLATILITY_VALUE_MIN;
        double upperBound = IMPLIED_VOLATILITY_VALUE_MAX;

        for (int i = 0; i < 5; i++) {
            double impliedVolatility = (upperBound - lowerBound) / 2.0;
            var commonCalculations = CommonCalculations.from(forwardPrice, strikePrice, riskFreeRate, impliedVolatility, timeToMaturity);
            double theoreticalPrice = calculateOptionPrice(optionType, forwardPrice, strikePrice, commonCalculations);

            if (theoreticalPrice > marketPrice) {
                upperBound = impliedVolatility;
            } else if (theoreticalPrice < marketPrice) {
                lowerBound = impliedVolatility;
            } else {
                return impliedVolatility;
            }

        }
        return (lowerBound + upperBound) / 2.0;
    }

    private static double calculateOptionPrice(OptionTypeEnum optionTypeEnum,
                                               double forwardPrice,
                                               double strikePrice,
                                               CommonCalculations commonCalculations) {
        return switch (optionTypeEnum) {
            case CALL -> calculateCallOptionPrice(forwardPrice, strikePrice, commonCalculations);
            case PUT -> calculatePutOptionPrice(forwardPrice, strikePrice, commonCalculations);
        };
    }

    private static double calculatePutOptionPrice(double forwardPrice,
                                                  double strikePrice,
                                                  CommonCalculations commonCalculations) {
        return strikePrice * commonCalculations.compoundedRiskFreeRate * STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(-commonCalculations.d2) -
                forwardPrice * commonCalculations.compoundedRiskFreeRate * STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(-commonCalculations.d1);
    }

    private static double calculateCallOptionPrice(double forwardPrice,
                                                   double strikePrice,
                                                   CommonCalculations commonCalculations) {
        return forwardPrice * commonCalculations.compoundedRiskFreeRate * commonCalculations.cdfNormD1 -
                strikePrice * commonCalculations.compoundedRiskFreeRate * commonCalculations.cdfNormD2;
    }

    private static double calculateDelta(OptionTypeEnum optionTypeEnum,
                                         CommonCalculations commonCalculation) {
        return switch (optionTypeEnum) {
            case CALL -> commonCalculation.compoundedRiskFreeRate * commonCalculation.cdfNormD1;
            case PUT -> commonCalculation.compoundedRiskFreeRate * (commonCalculation.cdfNormD1 - 1);
        };
    }

    private static double calculateGamma(double forwardPrice,
                                         double volatility,
                                         double timeToMaturity,
                                         CommonCalculations commonCalculations) {
        var rise = commonCalculations.compoundedRiskFreeRate * commonCalculations.pdfNormD1;
        var run = forwardPrice * volatility * Math.sqrt(timeToMaturity);
        return rise / run;
    }

    private static double calculateVega(double forwardPrice,
                                        double timeToMaturity,
                                        CommonCalculations commonCalculations) {
        var rise = commonCalculations.compoundedRiskFreeRate * forwardPrice * Math.sqrt(timeToMaturity) * commonCalculations.pdfNormD1;
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
                                         double forwardPrice,
                                         double volatility,
                                         double strikePrice,
                                         double timeToMaturity,
                                         double riskFreeRate,
                                         CommonCalculations commonCalculations) {
        var sharedPart = -forwardPrice * volatility * commonCalculations.compoundedRiskFreeRate * commonCalculations.pdfNormD1 / (2 * Math.sqrt(timeToMaturity));
        var sidePart = switch (optionTypeEnum) {
            case CALL -> -riskFreeRate * strikePrice * commonCalculations.compoundedRiskFreeRate * commonCalculations.pdfNormD2 +
                    riskFreeRate * forwardPrice * commonCalculations.compoundedRiskFreeRate * commonCalculations.cdfNormD1;
            case PUT ->
                    riskFreeRate * strikePrice * commonCalculations.compoundedRiskFreeRate * STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(-commonCalculations.d2) -
                            riskFreeRate * forwardPrice * commonCalculations.compoundedRiskFreeRate * STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(-commonCalculations.d1);
        };

        return (1 / DAYS_PER_YEAR) * (sharedPart + sidePart);
    }

    private record CommonCalculations(double d1,
                                      double d2,
                                      double cdfNormD1,
                                      double cdfNormD2,
                                      double pdfNormD1,
                                      double pdfNormD2,
                                      double compoundedRiskFreeRate) {

        public static CommonCalculations from(double forwardPrice,
                                              double strikePrice,
                                              double riskFreeRate,
                                              double volatility,
                                              double timeToMaturity) {
            var d1 = d1(forwardPrice, strikePrice, volatility, timeToMaturity);
            var d2 = d2(d1, volatility, timeToMaturity);
            var compoundedRiskFreeRate = Math.exp(-riskFreeRate * timeToMaturity);
            return new CommonCalculations(
                    d1,
                    d2,
                    STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(d1),
                    STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(d2),
                    STANDARD_NORMAL_DISTRIBUTION.density(d1),
                    STANDARD_NORMAL_DISTRIBUTION.density(d2),
                    compoundedRiskFreeRate
            );
        }

        private static double d1(double forwardPrice,
                                 double strikePrice,
                                 double volatility,
                                 double timeToMaturity) {

            var rise = Math.log(forwardPrice / strikePrice) + ((volatility * volatility * 0.5) * timeToMaturity);
            var run = volatility * Math.sqrt(timeToMaturity);
            return rise / run;
        }

        private static double d2(double d1, double volatility, double timeToMaturity) {
            return d1 - volatility * Math.sqrt(timeToMaturity);
        }
    }
}
