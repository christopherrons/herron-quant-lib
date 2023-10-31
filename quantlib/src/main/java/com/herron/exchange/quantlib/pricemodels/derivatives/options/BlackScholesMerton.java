package com.herron.exchange.quantlib.pricemodels.derivatives.options;

import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.time.temporal.ChronoUnit;

import static java.time.temporal.ChronoUnit.DAYS;

public class BlackScholesMerton {
    public static final NormalDistribution STANDARD_NORMAL_DISTRIBUTION = new NormalDistribution();

    public static double calculate(Timestamp valuationTime,
                                   OptionInstrument optionInstrument,
                                   double underlyingPrice,
                                   double volatility,
                                   double riskFreeRate) {
        double strikePrice = optionInstrument.strikePrice().getRealValue();
        double timeToMaturity = DAYS.between(valuationTime.toLocalDate(), optionInstrument.maturityDate().toLocalDate()) / 365.0;

        double optionPrice = switch (optionInstrument.optionType()) {
            case CALL -> calculateCallOptionPrice(underlyingPrice, strikePrice, volatility, riskFreeRate, timeToMaturity);
            case PUT -> calculatePutOptionPrice(underlyingPrice, strikePrice, volatility, riskFreeRate, timeToMaturity);
        };

        return optionPrice;
    }

    private static double calculatePutOptionPrice(double underlyingPrice,
                                                  double strikePrice,
                                                  double volatility,
                                                  double riskFreeRate,
                                                  double timeToMaturity) {
        double callOptionPrice = calculateCallOptionPrice(underlyingPrice, strikePrice, volatility, riskFreeRate, timeToMaturity);
        return callOptionPrice - underlyingPrice + strikePrice * Math.exp(-riskFreeRate * timeToMaturity);
    }

    private static double calculateCallOptionPrice(double underlyingPrice,
                                                   double strikePrice,
                                                   double volatility,
                                                   double riskFreeRate,
                                                   double timeToMaturity) {
        var d1 = d1(underlyingPrice, strikePrice, volatility, riskFreeRate, timeToMaturity);
        var d2 = d2(d1, volatility, timeToMaturity);
        return STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(d1) * underlyingPrice - STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(d2) * strikePrice * Math.exp(-riskFreeRate * timeToMaturity);
    }

    private static double d1(double underlyingPrice,
                             double strikePrice,
                             double volatility,
                             double riskFreeRate,
                             double timeToMaturity) {

        var rise = Math.log(underlyingPrice / strikePrice) + (riskFreeRate + volatility * volatility * 0.5) * timeToMaturity;
        var run = volatility * Math.sqrt(timeToMaturity);
        return rise / run;
    }

    private static double d2(double d1, double volatility, double timeToMaturity) {
        return d1 - volatility * Math.sqrt(timeToMaturity);
    }

}
