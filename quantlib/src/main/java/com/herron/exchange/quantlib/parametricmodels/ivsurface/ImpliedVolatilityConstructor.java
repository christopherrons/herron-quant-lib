package com.herron.exchange.quantlib.parametricmodels.ivsurface;

import com.herron.exchange.common.api.common.api.referencedata.instruments.Instrument;
import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.enums.SurfaceConstructionMethod;
import com.herron.exchange.common.api.common.math.MathUtils;
import com.herron.exchange.common.api.common.math.parametricmodels.forwardcurve.ForwardPriceCurve;
import com.herron.exchange.common.api.common.math.parametricmodels.impliedvolsurface.ImpliedVolatilitySurface;
import com.herron.exchange.common.api.common.math.parametricmodels.impliedvolsurface.model.ImpliedVolPoint;
import com.herron.exchange.common.api.common.math.parametricmodels.impliedvolsurface.model.ImpliedVolatilitySurfaceModelParameters;
import com.herron.exchange.common.api.common.math.parametricmodels.yieldcurve.YieldCurve;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import com.herron.exchange.common.api.common.messages.pricing.Black76PriceModelParameters;
import com.herron.exchange.common.api.common.messages.pricing.BlackScholesPriceModelParameters;
import com.herron.exchange.quantlib.pricemodels.derivatives.options.Black76;
import com.herron.exchange.quantlib.pricemodels.derivatives.options.BlackScholesMerton;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ImpliedVolatilityConstructor {

    public static ImpliedVolatilitySurface construct(Timestamp valuationTime,
                                                     Instrument underlying,
                                                     List<OptionInstrument> options,
                                                     Map<Instrument, Price> instrumentToPrice,
                                                     YieldCurve yieldCurve,
                                                     ForwardPriceCurve forwardPriceCurve) {
        double spotPrice = instrumentToPrice.get(underlying).getRealValue();
        List<OptionInstrument> filteredOptions = ImpliedVolatilityFilter.filter(options, instrumentToPrice, spotPrice);
        List<ImpliedVolPoint> points = filteredOptions.stream()
                .map(option -> {
                    double optionPrice = instrumentToPrice.get(option).getRealValue();
                    try {
                        return calculateImpliedVolatility(valuationTime, option, spotPrice, optionPrice, yieldCurve, forwardPriceCurve);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        return ImpliedVolatilitySurface.create(
                underlying.instrumentId(),
                spotPrice,
                new ImpliedVolatilitySurfaceModelParameters(SurfaceConstructionMethod.HERMITE_BICUBIC, points)
        );
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
                double dividendYield = ((Black76PriceModelParameters) option.priceModelParameters()).dividendYield().getRealValue();
                double forwardPrice = forwardPriceCurve != null ? forwardPriceCurve.getForwardPrice(timeToMaturity) : spotPrice * Math.exp((riskFreeRate - dividendYield) * timeToMaturity);
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

        return new ImpliedVolPoint(
                MathUtils.roundDouble(timeToMaturity, 5),
                MathUtils.roundDouble(logMoneyness, 5),
                MathUtils.roundDouble(impliedVolatility, 5)
        );
    }
}
