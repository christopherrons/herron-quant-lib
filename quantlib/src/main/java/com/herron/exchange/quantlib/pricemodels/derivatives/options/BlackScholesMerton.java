package com.herron.exchange.quantlib.pricemodels.derivatives.options;

import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.enums.DayCountConventionEnum;
import com.herron.exchange.common.api.common.enums.OptionTypeEnum;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import org.apache.commons.math3.distribution.NormalDistribution;

import static java.time.temporal.ChronoUnit.DAYS;

public class BlackScholesMerton {
    private static final double DAYS_PER_YEAR = DayCountConventionEnum.ACT365.getDaysPerYear();
    private static final NormalDistribution STANDARD_NORMAL_DISTRIBUTION = new NormalDistribution();

    public static BlackScholesResult calculate(Timestamp valuationTime,
                                               OptionInstrument optionInstrument,
                                               double underlyingPrice,
                                               double volatility,
                                               double riskFreeRate,
                                               double dividendYield) {
        double strikePrice = optionInstrument.strikePrice().getRealValue();
        double timeToMaturity = DAYS.between(valuationTime.toLocalDate(), optionInstrument.maturityDate().toLocalDate()) / DAYS_PER_YEAR;
        var commonCalculations = CommonCalculations.from(underlyingPrice, strikePrice, riskFreeRate, dividendYield, volatility, timeToMaturity);

        return new BlackScholesResult(
                calculateOptionPrice(optionInstrument.optionType(), underlyingPrice, strikePrice, commonCalculations),
                calculateDelta(optionInstrument.optionType(), commonCalculations),
                calculateGamma(underlyingPrice, volatility, timeToMaturity, commonCalculations),
                0,
                calculateVega(underlyingPrice, timeToMaturity, commonCalculations),
                calculateRho(optionInstrument.optionType(), strikePrice, timeToMaturity, commonCalculations)
        );
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
        double callOptionPrice = calculateCallOptionPrice(underlyingPrice, strikePrice, commonCalculations);
        return callOptionPrice - underlyingPrice + strikePrice * commonCalculations.compoundedRiskFreeRate;
    }

    private static double calculateCallOptionPrice(double underlyingPrice,
                                                   double strikePrice,
                                                   CommonCalculations commonCalculation) {
        return commonCalculation.cdfNormD1 * underlyingPrice * commonCalculation.compoundedYield() - commonCalculation.cdfNormD2 * strikePrice * commonCalculation.compoundedRiskFreeRate;
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

    private record CommonCalculations(double d1,
                                      double d2,
                                      double cdfNormD1,
                                      double cdfNormD2,
                                      double pdfNormD1,
                                      double pdfNormD,
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

            var rise = Math.log(underlyingPrice / strikePrice) + (riskFreeRate - dividendYield + volatility * volatility * 0.5) * timeToMaturity;
            var run = volatility * Math.sqrt(timeToMaturity);
            return rise / run;
        }

        private static double d2(double d1, double volatility, double timeToMaturity) {
            return d1 - volatility * Math.sqrt(timeToMaturity);
        }
    }
}
