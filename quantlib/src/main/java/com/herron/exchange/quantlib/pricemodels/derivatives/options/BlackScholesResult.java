package com.herron.exchange.quantlib.pricemodels.derivatives.options;

public record BlackScholesResult(double price,
                                 double delta,
                                 double gamma,
                                 double theta,
                                 double vega,
                                 double rho) {
}
