package com.penguinsecure.basis.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class ArchitectureRulesTest {
    private static final Pattern PACKAGE = Pattern.compile("package\\s+([a-zA-Z0-9_.]+);");
    private static JavaClasses basisClasses;
    private static String[] hotPackages;

    @BeforeAll
    static void importClasses() throws IOException {
        basisClasses = new ClassFileImporter().importPackages("com.penguinsecure.basis");
        hotPackages = classifiedPackages("HOT");
    }

    @Test
    void coreDoesNotDependOnTransportPersistenceOrConcreteImplementations() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("com.penguinsecure.basis.core..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "io.netty..",
                                "io.aeron..",
                                "com.fasterxml.jackson..",
                                "java.sql..",
                                "com.penguinsecure.basis.venue..",
                                "com.penguinsecure.basis.strategy.basis..");

        rule.allowEmptyShould(true).check(basisClasses);
    }

    @Test
    void venueImplementationsDoNotDependOnStrategyOrCoreRiskLogic() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(
                                "com.penguinsecure.basis.venue.bybit..",
                                "com.penguinsecure.basis.venue.deribit..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "com.penguinsecure.basis.strategy..",
                                "com.penguinsecure.basis.core.risk..",
                                "com.penguinsecure.basis.core.oems..");

        rule.allowEmptyShould(true).check(basisClasses);
    }

    @Test
    void strategyImplementationsDoNotDependOnConcreteVenues() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("com.penguinsecure.basis.strategy..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "com.penguinsecure.basis.venue.bybit..",
                                "com.penguinsecure.basis.venue.deribit..");

        rule.allowEmptyShould(true).check(basisClasses);
    }

    @Test
    void hotPackagesRejectBlockingAllocatingAndReflectiveApis() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(hotPackages)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "java.math..",
                                "java.text..",
                                "java.util.Optional",
                                "java.util.stream..",
                                "java.util.concurrent.locks..",
                                "java.util.concurrent.Future",
                                "java.util.concurrent.CompletableFuture",
                                "java.lang.reflect..",
                                "org.slf4j..",
                                "org.apache.logging..",
                                "ch.qos.logback..");

        rule.check(basisClasses);
    }

    @Test
    void hotPackagesUseInjectedClocks() {
        noClasses()
                .that()
                .resideInAnyPackage(hotPackages)
                .should()
                .callMethod(System.class, "currentTimeMillis")
                .orShould()
                .callMethod(System.class, "nanoTime")
                .check(basisClasses);
    }

    private static String[] classifiedPackages(String classification) throws IOException {
        Path root = Path.of(System.getProperty("basis.reactor.root"));
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.endsWith("package-info.java"))
                    .filter(ArchitectureRulesTest::isProductionSource)
                    .filter(
                            path -> {
                                try {
                                    return Files.readString(path)
                                            .contains("@path " + classification);
                                } catch (IOException exception) {
                                    throw new IllegalStateException(exception);
                                }
                            })
                    .map(
                            path -> {
                                try {
                                    Matcher matcher = PACKAGE.matcher(Files.readString(path));
                                    if (!matcher.find()) {
                                        throw new IllegalStateException(
                                                path + " has no package declaration");
                                    }
                                    return matcher.group(1) + "..";
                                } catch (IOException exception) {
                                    throw new IllegalStateException(exception);
                                }
                            })
                    .toArray(String[]::new);
        }
    }

    private static boolean isProductionSource(Path path) {
        return path.toString().replace(File.separatorChar, '/').contains("/src/main/java/");
    }
}
