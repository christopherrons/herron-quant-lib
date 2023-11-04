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
                                              String underlyingInstrumentId,
                                              double strikePrice,
                                              Map<Timestamp, List<OptionInstrument>> maturityDateToOptions,
                                              Map<Instrument, Price> instrumentToPrice,
                                              YieldCurve yieldCurve) {
        List<ForwardPricePoint> points = new ArrayList<>();
        for (var entry : maturityDateToOptions.entrySet()) {
            Timestamp maturityDate = entry.getKey();
            Map<OptionTypeEnum, List<OptionInstrument>> optionTypeToOption = entry.getValue().stream().collect(Collectors.groupingBy(OptionInstrument::optionType));
            var put = optionTypeToOption.get(OptionTypeEnum.PUT);
            var call = optionTypeToOption.get(OptionTypeEnum.CALL);
            if (put == null || call == null) {
                continue;
            }
            double putPrice = instrumentToPrice.get(put.get(0)).getRealValue();
            double callPrice = instrumentToPrice.get(call.get(0)).getRealValue();
            double timeToMaturity = Black76.calculateTimeToMaturity(valuationTime, maturityDate);
            double riskFreeRate = yieldCurve.getYield(timeToMaturity);
            double forwardPrice = strikePrice + (callPrice - putPrice) * Math.exp(riskFreeRate * timeToMaturity);
            points.add(new ForwardPricePoint(timeToMaturity, forwardPrice));
        }

        return ForwardPriceCurve.create(underlyingInstrumentId, PureNumber.create(strikePrice), new ForwardCurveModelParameters(points, CUBIC_SPLINE));
    }
}
