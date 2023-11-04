package com.herron.exchange.quantlib.parametricmodels;

import com.herron.exchange.common.api.common.api.referencedata.instruments.Instrument;
import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import com.herron.exchange.common.api.common.messages.pricing.BlackScholesPriceModelParameters;
import com.herron.exchange.common.api.common.parametricmodels.forwardcurve.ForwardPriceCurve;
import com.herron.exchange.common.api.common.parametricmodels.impliedvolsurface.ImpliedVolatilitySurface;
import com.herron.exchange.common.api.common.parametricmodels.impliedvolsurface.model.ImpliedVolPoint;
import com.herron.exchange.common.api.common.parametricmodels.yieldcurve.YieldCurve;
import com.herron.exchange.quantlib.pricemodels.derivatives.options.Black76;
import com.herron.exchange.quantlib.pricemodels.derivatives.options.BlackScholesMerton;

import java.util.List;
import java.util.Map;

public class ImpliedVolatilityConstructor {

    public static ImpliedVolatilitySurface construct(Timestamp valuationTime,
                                                     Instrument underlying,
                                                     List<OptionInstrument> options,
                                                     Map<Instrument, Price> instrumentToPrice,
                                                     YieldCurve yieldCurve,
                                                     ForwardPriceCurve forwardPriceCurve) {
        double spotPrice = instrumentToPrice.get(underlying).getRealValue();
        List<ImpliedVolPoint> points = options.stream()
                .map(option -> {
                    double optionPrice = instrumentToPrice.get(option).getRealValue();
                    return calculateImpliedVolatility(valuationTime, option, spotPrice, optionPrice, yieldCurve, forwardPriceCurve);
                })
                .toList();
        return ImpliedVolatilitySurface.create("tmp", 0);
    }

    private static ImpliedVolPoint calculateImpliedVolatility(Timestamp valuationTime,
                                                              OptionInstrument option,
                                                              double spotPrice,
                                                              double marketPrice,
                                                              YieldCurve yieldCurve,
                                                              ForwardPriceCurve forwardPriceCurve) {
        double strikePrice = option.strikePrice().getRealValue();
        double logMoneyness = Math.log(strikePrice / spotPrice);
        double timeToMaturity = BlackScholesMerton.calculateTimeToMaturity(valuationTime, option);
        double riskFreeRate = yieldCurve.getYield(timeToMaturity);
        double impliedVolatility = switch (option.priceModel()) {
            case BLACK_SCHOLES -> {
                double dividendYield = ((BlackScholesPriceModelParameters) option.priceModelParameters()).dividendYield().getRealValue();
                yield BlackScholesMerton.calculateImpliedVolatility(
                        option.optionType(),
                        strikePrice,
                        marketPrice,
                        spotPrice,
                        timeToMaturity,
                        riskFreeRate,
                        dividendYield).getRealValue();
            }
            case BLACK_76 -> {
                double forwardPrice = forwardPriceCurve != null ? forwardPriceCurve.getForwardPrice(timeToMaturity) : spotPrice * Math.exp(riskFreeRate * timeToMaturity);
                yield Black76.calculateImpliedVolatility(
                        option.optionType(),
                        strikePrice,
                        marketPrice,
                        forwardPrice,
                        timeToMaturity,
                        riskFreeRate).getRealValue();
            }
            case BARONE_ADESI_WHALEY -> 0;
            default -> 0;
        };

        return new ImpliedVolPoint(timeToMaturity, logMoneyness, impliedVolatility);
    }
}
