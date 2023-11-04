package com.herron.exchange.quantlib.parametricmodels;

import com.herron.exchange.common.api.common.api.referencedata.instruments.Instrument;
import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.enums.OptionTypeEnum;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.PureNumber;
import com.herron.exchange.common.api.common.messages.common.Timestamp;
import com.herron.exchange.common.api.common.parametricmodels.forwardcurve.ForwardPriceCurve;
import com.herron.exchange.common.api.common.parametricmodels.forwardcurve.model.ForwardCurveModelParameters;
import com.herron.exchange.common.api.common.parametricmodels.forwardcurve.model.ForwardPricePoint;
import com.herron.exchange.common.api.common.parametricmodels.yieldcurve.YieldCurve;
import com.herron.exchange.quantlib.pricemodels.derivatives.options.Black76;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.herron.exchange.common.api.common.enums.InterpolationMethod.CUBIC_SPLINE;

public class ForwardPriceCurveConstructor {

    public static ForwardPriceCurve construct(Timestamp valuationTime,
                                              Instrument underlyingInstrument,
                                              List<OptionInstrument> options,
                                              Map<Instrument, Price> instrumentToPrice,
                                              YieldCurve yieldCurve) {
        List<ForwardPricePoint> points = new ArrayList<>();
        Map<Timestamp, List<OptionInstrument>> maturityToOptions = options.stream().collect(Collectors.groupingBy(OptionInstrument::maturityDate));
        for (var maturityEntry : maturityToOptions.entrySet()) {
            Timestamp maturityDate = maturityEntry.getKey();
            List<OptionInstrument> optionsAtMaturity = maturityEntry.getValue();
            Map<PureNumber, List<OptionInstrument>> strikePriceToOptions = optionsAtMaturity.stream().collect(Collectors.groupingBy(OptionInstrument::strikePrice));

            double timeToMaturity = Black76.calculateTimeToMaturity(valuationTime, maturityDate);
            double riskFreeRate = yieldCurve.getYield(timeToMaturity);
            double averageForwardPriceAtMaturity = calculateForwardPriceAtTimeToMaturity(timeToMaturity, riskFreeRate, strikePriceToOptions, instrumentToPrice);
            points.add(new ForwardPricePoint(timeToMaturity, averageForwardPriceAtMaturity));
        }

        return ForwardPriceCurve.create(underlyingInstrument.instrumentId(), new ForwardCurveModelParameters(points, CUBIC_SPLINE));
    }

    private static double calculateForwardPriceAtTimeToMaturity(double timeToMaturity,
                                                                double riskFreeRate,
                                                                Map<PureNumber, List<OptionInstrument>> strikePriceToOptions,
                                                                Map<Instrument, Price> instrumentToPrice) {
        List<Double> implicitForwardPriceAtMaturity = new ArrayList<>();
        for (var strikePriceEntry : strikePriceToOptions.entrySet()) {
            double strikePrice = strikePriceEntry.getKey().getRealValue();
            List<OptionInstrument> optionsAtStrike = strikePriceEntry.getValue();
            var put = optionsAtStrike.stream().filter(o -> o.optionType() == OptionTypeEnum.PUT).findFirst();
            var call = optionsAtStrike.stream().filter(o -> o.optionType() == OptionTypeEnum.CALL).findFirst();
            if (put.isEmpty() || call.isEmpty()) {
                continue;
            }
            double putPrice = instrumentToPrice.get(put.get()).getRealValue();
            double callPrice = instrumentToPrice.get(call.get()).getRealValue();
            double forwardPrice = strikePrice + (callPrice - putPrice) * Math.exp(riskFreeRate * timeToMaturity);
            implicitForwardPriceAtMaturity.add(forwardPrice);
        }

        return implicitForwardPriceAtMaturity.stream().mapToDouble(d -> d).average().orElse(0.0);
    }
}
