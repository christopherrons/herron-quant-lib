package com.herron.exchange.quantlib.parametricmodels.ivsurface;

import com.herron.exchange.common.api.common.api.referencedata.instruments.Instrument;
import com.herron.exchange.common.api.common.api.referencedata.instruments.OptionInstrument;
import com.herron.exchange.common.api.common.enums.OptionTypeEnum;
import com.herron.exchange.common.api.common.messages.common.Price;
import com.herron.exchange.common.api.common.messages.common.Timestamp;

import java.util.*;
import java.util.stream.Collectors;

public class ImpliedVolatilityFilter {

    public static List<OptionInstrument> filter(List<OptionInstrument> options,
                                                Map<Instrument, Price> instrumentToPrice,
                                                double spotPrice) {
        List<OptionInstrument> acceptedOptions = new ArrayList<>();
        Map<OptionTypeEnum, List<OptionInstrument>> typeToOption = options.stream().collect(Collectors.groupingBy(OptionInstrument::optionType));

        for (var typeToOptionEntry : typeToOption.entrySet()) {
            OptionTypeEnum optionType = typeToOptionEntry.getKey();
            List<OptionInstrument> optionsOfType = typeToOptionEntry.getValue();
            List<List<OptionData>> optionsByMaturity = buildGrid(optionsOfType, instrumentToPrice);

            int nrOfMaturities = optionsByMaturity.size();
            for (var maturity = 0; maturity < nrOfMaturities; maturity++) {
                List<OptionData> strikesAtMaturity = optionsByMaturity.get(maturity);
                int nrOfStrikes = strikesAtMaturity.size();
                for (var strike = 0; strike < nrOfStrikes; strike++) {
                    OptionData current = strikesAtMaturity.get(strike);

                    if (strike + 1 < nrOfStrikes) {
                        OptionData vertical = strikesAtMaturity.get(strike + 1);
                        if (hasVerticalSpreadArbitrage(current, vertical, optionType)) {
                            continue;
                        }
                    }

                    if (maturity + 1 < nrOfMaturities && strike < optionsByMaturity.get(maturity + 1).size()) {
                        OptionData calendar = optionsByMaturity.get(maturity + 1).get(strike);
                        if (hasCalendarSpreadArbitrage(current, calendar, optionType)) {
                            continue;
                        }
                    }

                    if (strike + 2 < nrOfStrikes) {
                        OptionData vertical = strikesAtMaturity.get(strike + 1);
                        OptionData butterfly = strikesAtMaturity.get(strike + 2);
                        if (hasButterflySpreadArbitrage(current, vertical, butterfly, optionType)) {
                            continue;
                        }
                    }

                    acceptedOptions.add(current.option);
                }
            }
        }

        return acceptedOptions;
    }

    private static boolean hasVerticalSpreadArbitrage(OptionData current, OptionData vertical, OptionTypeEnum optionType) {
        return switch (optionType) {
            case CALL -> vertical.price - current.price < 0;
            case PUT -> current.price - vertical.price < 0;
        };
    }

    private static boolean hasCalendarSpreadArbitrage(OptionData current, OptionData calendar, OptionTypeEnum optionType) {
        return switch (optionType) {
            case CALL -> current.price - calendar.price < 0;
            case PUT -> calendar.price - current.price < 0;
        };
    }

    private static boolean hasButterflySpreadArbitrage(OptionData op1, OptionData op2, OptionData op4, OptionTypeEnum optionType) {
        return switch (optionType) {
            case CALL -> op1.price - ((op4.strike - op1.strike) / (op2.strike - op1.strike)) * op2.price + op4.price < 0;
            case PUT -> op1.price - ((op4.strike - op1.strike) / (op2.strike - op1.strike)) * op2.price + op4.price > 0;
        };
    }

    private static List<List<OptionData>> buildGrid(List<OptionInstrument> options,
                                                    Map<Instrument, Price> instrumentToPrice) {
        Map<Timestamp, List<OptionInstrument>> maturityToOptions = options.stream().collect(Collectors.groupingBy(OptionInstrument::maturityDate));
        Map<Timestamp, List<OptionInstrument>> sortedMaturityToOptions = new TreeMap<>(maturityToOptions);

        List<List<OptionData>> optionDataGrid = new ArrayList<>();
        for (var optionsByAscendingMaturity : sortedMaturityToOptions.entrySet()) {
            List<OptionInstrument> optionsByAscendingStrike = optionsByAscendingMaturity.getValue();
            optionsByAscendingStrike.sort(Comparator.comparing(o -> o.strikePrice().getRealValue()));
            List<OptionData> strikesAtMaturity = new ArrayList<>();
            optionDataGrid.add(strikesAtMaturity);
            for (var option : optionsByAscendingStrike) {
                strikesAtMaturity.add(
                        new OptionData(
                                option.maturityDate(),
                                option.strikePrice().getRealValue(),
                                instrumentToPrice.get(option).getRealValue(),
                                option
                        )
                );
            }
        }

        return optionDataGrid;
    }

    private record OptionData(Timestamp maturity, double strike, double price, OptionInstrument option) {
    }
}
