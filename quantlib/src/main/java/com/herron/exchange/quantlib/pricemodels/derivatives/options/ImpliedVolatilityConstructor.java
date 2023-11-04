package com.herron.exchange.quantlib.pricemodels.derivatives.options;

import com.herron.exchange.common.api.common.api.referencedata.instruments.Instrument;
import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import com.herron.exchange.common.api.common.messages.pricing.BlackScholesPriceModelParameters;
import com.herron.exchange.common.api.common.parametricmodels.impliedvolsurface.ImpliedVolatilitySurface;
import com.herron.exchange.common.api.common.parametricmodels.impliedvolsurface.model.ImpliedVolPoint;
import com.herron.exchange.common.api.common.parametricmodels.yieldcurve.YieldCurve;

import java.util.List;
import java.util.Map;

public class ImpliedVolatilityConstructor {

    public static ImpliedVolatilitySurface construct(Timestamp valuationTime,
                                              Instrument underlying,
                                              List<OptionInstrument> options,
                                              Map<Instrument, Price> instrumentToPrice,
                                              YieldCurve yieldCurve) {
        double underlyingPrice = instrumentToPrice.get(underlying).getRealValue();
        List<ImpliedVolPoint> points = options.stream()
                .map(option -> {
                    double optionPrice = instrumentToPrice.get(option).getRealValue();
                    return calculateImpliedVolatility(valuationTime, option, underlyingPrice, optionPrice, yieldCurve);
                })
                .toList();
        return ImpliedVolatilitySurface.create("tmp", 0);
    }

    private static ImpliedVolPoint calculateImpliedVolatility(Timestamp valuationTime,
                                                       OptionInstrument option,
                                                       double underlyingPrice,
                                                       double marketPrice,
                                                       YieldCurve yieldCurve) {
        double strikePrice = option.strikePrice().getRealValue();
        double logMoneyness = Math.log(strikePrice / underlyingPrice);
        double timeToMaturity = BlackScholesMerton.calculateTimeToMaturity(valuationTime, option);
        double riskFreeRate = yieldCurve.getYield(timeToMaturity);
        double impliedVolatility = switch (option.priceModel()) {
            case BLACK_SCHOLES -> {
                double dividendYield = ((BlackScholesPriceModelParameters) option.priceModelParameters()).dividendYield().getRealValue();
                yield BlackScholesMerton.calculateImpliedVolatility(
                        option.optionType(),
                        strikePrice,
                        marketPrice,
                        underlyingPrice,
                        timeToMaturity,
                        riskFreeRate,
                        dividendYield).getRealValue();
            }
            case BLACK_76 -> 0;
            case BARONE_ADESI_WHALEY -> 0;
            default -> 0;
        };

        return new ImpliedVolPoint(timeToMaturity, logMoneyness, impliedVolatility);
    }
}
