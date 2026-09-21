package com.penguinsecure.basis.sim.paper;

import com.penguinsecure.basis.core.book.BookSide;
import com.penguinsecure.basis.core.book.ExecutablePriceStatus;
import com.penguinsecure.basis.core.book.FixedDepthOrderBook;
import com.penguinsecure.basis.core.book.MutableExecutablePrice;
import com.penguinsecure.basis.core.command.OrderSide;

/** Aggressive-only fill model which never infers passive queue position. */
public final class ConservativePaperFillModel {
    private final long adverseSlippageTicks;
    private final MutableExecutablePrice executable = new MutableExecutablePrice();

    public ConservativePaperFillModel(final long adverseSlippageTicks) {
        if (adverseSlippageTicks < 0)
            throw new IllegalArgumentException("slippage must be non-negative");
        this.adverseSlippageTicks = adverseSlippageTicks;
    }

    public void fill(
            final FixedDepthOrderBook book,
            final OrderSide orderSide,
            final long requestedQuantity,
            final long maximumFillQuantity,
            final long limitPriceTicks,
            final long arrivalMonoNanos,
            final MutablePaperFill destination) {
        if (destination == null) throw new NullPointerException("destination is required");
        destination.clear();
        if (book == null
                || orderSide == null
                || requestedQuantity <= 0
                || maximumFillQuantity <= 0
                || limitPriceTicks <= 0
                || arrivalMonoNanos <= 0) return;
        final long boundedQuantity = Math.min(requestedQuantity, maximumFillQuantity);
        final BookSide restingSide = orderSide == OrderSide.BUY ? BookSide.ASK : BookSide.BID;
        final ExecutablePriceStatus status =
                book.executablePrice(
                        restingSide,
                        boundedQuantity,
                        limitPriceTicks,
                        arrivalMonoNanos,
                        executable);
        if (status != ExecutablePriceStatus.OK && status != ExecutablePriceStatus.PARTIAL) return;
        final long adversePrice;
        try {
            adversePrice =
                    orderSide == OrderSide.BUY
                            ? Math.min(
                                    limitPriceTicks,
                                    Math.addExact(
                                            executable.averagePriceTicks(), adverseSlippageTicks))
                            : Math.max(
                                    limitPriceTicks,
                                    Math.subtractExact(
                                            executable.averagePriceTicks(), adverseSlippageTicks));
        } catch (ArithmeticException ignored) {
            return;
        }
        destination.set(executable.executedQuantityLots(), adversePrice);
    }
}
