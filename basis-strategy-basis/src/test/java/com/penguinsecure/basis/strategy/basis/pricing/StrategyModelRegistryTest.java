package com.penguinsecure.basis.strategy.basis.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penguinsecure.basis.core.product.ProductFamily;
import com.penguinsecure.basis.strategy.api.definition.BasisStrategyDefinitionValidator;
import com.penguinsecure.basis.strategy.api.definition.StrategyValidationStatus;
import com.penguinsecure.basis.strategy.api.model.StrategyModelRegistry;
import com.penguinsecure.basis.strategy.basis.model.FundingSettlementCarryModel;
import org.junit.jupiter.api.Test;

final class StrategyModelRegistryTest {
    @Test
    void validatesReferenceLinearAndDatedModelCombinationsWithoutReflection() {
        StrategyModelRegistry registry = StrategyTestFixtures.registry();

        assertEquals(
                StrategyValidationStatus.VALID,
                BasisStrategyDefinitionValidator.validate(
                        StrategyTestFixtures.definition(1, 1, 1, 1), registry));
        assertEquals(
                StrategyValidationStatus.VALID,
                BasisStrategyDefinitionValidator.validate(
                        StrategyTestFixtures.definition(2, 2, 2, 1), registry));
        assertEquals(
                StrategyValidationStatus.VALID,
                BasisStrategyDefinitionValidator.validate(
                        StrategyTestFixtures.definition(3, 3, 4, 2), registry));
    }

    @Test
    void rejectsMissingDuplicateAndPostFreezeRegistration() {
        StrategyModelRegistry missing = new StrategyModelRegistry(4).freeze();
        assertEquals(
                StrategyValidationStatus.INVALID_MODEL_ID,
                BasisStrategyDefinitionValidator.validate(
                        StrategyTestFixtures.linearDefinition(), missing));

        StrategyModelRegistry registry = new StrategyModelRegistry(4);
        registry.registerPayoff(
                new com.penguinsecure.basis.strategy.basis.model.LinearPayoffModel(2));
        assertThrows(
                IllegalStateException.class,
                () ->
                        registry.registerPayoff(
                                new com.penguinsecure.basis.strategy.basis.model.LinearPayoffModel(
                                        2)));
        registry.freeze();
        assertThrows(
                IllegalStateException.class,
                () -> registry.registerCarryModel(new FundingSettlementCarryModel(1)));
    }

    @Test
    void constructsReferenceAndTwoSyntheticStrategiesThroughOnlyRegisteredModels() {
        StrategyModelRegistry registry = StrategyTestFixtures.registry();

        new CrossVenueBasisStrategy(
                StrategyTestFixtures.definition(1, 1, 1, 1),
                StrategyTestFixtures.instrument(1, 101, ProductFamily.INVERSE_PERPETUAL, 0),
                StrategyTestFixtures.instrument(2, 201, ProductFamily.INVERSE_PERPETUAL, 0),
                registry,
                8,
                2);
        new CrossVenueBasisStrategy(
                StrategyTestFixtures.linearDefinition(),
                StrategyTestFixtures.linearInstrument(1, 101),
                StrategyTestFixtures.linearInstrument(2, 201),
                registry,
                0,
                2);
        new CrossVenueBasisStrategy(
                StrategyTestFixtures.definition(3, 3, 4, 2),
                StrategyTestFixtures.instrument(1, 101, ProductFamily.LINEAR_FUTURE, 1_000),
                StrategyTestFixtures.instrument(2, 201, ProductFamily.INVERSE_FUTURE, 1_000),
                registry,
                8,
                2);
    }
}
