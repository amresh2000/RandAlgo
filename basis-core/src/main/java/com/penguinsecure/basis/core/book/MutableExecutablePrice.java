package com.penguinsecure.basis.core.book;

/** Caller-owned result slot for executable quantity, weighted price, and worst price. */
public final class MutableExecutablePrice {
    private ExecutablePriceStatus status = ExecutablePriceStatus.INVALID_ARGUMENT;
    private long requestedQuantityLots;
    private long executedQuantityLots;
    private long priceQuantityProduct;
    private long averagePriceTicks;
    private long worstPriceTicks;

    public ExecutablePriceStatus status() {
        return status;
    }

    public long requestedQuantityLots() {
        return requestedQuantityLots;
    }

    public long executedQuantityLots() {
        return executedQuantityLots;
    }

    public long priceQuantityProduct() {
        return priceQuantityProduct;
    }

    public long averagePriceTicks() {
        return averagePriceTicks;
    }

    public long worstPriceTicks() {
        return worstPriceTicks;
    }

    void set(
            final ExecutablePriceStatus newStatus,
            final long requested,
            final long executed,
            final long product,
            final long average,
            final long worst) {
        status = newStatus;
        requestedQuantityLots = requested;
        executedQuantityLots = executed;
        priceQuantityProduct = product;
        averagePriceTicks = average;
        worstPriceTicks = worst;
    }
}
