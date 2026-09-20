package com.penguinsecure.basis.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class PackageClassificationTest {
    private static final Pattern PATH = Pattern.compile("@path (HOT|WARM|COLD)");
    private static final Pattern OWNER = Pattern.compile("@owner [a-z][a-z0-9-]*");

    @Test
    void everyProductionPackageDeclaresPathAndOwner() throws IOException {
        Path root = Path.of(System.getProperty("basis.reactor.root"));
        List<Path> packageDeclarations;
        Set<Path> productionPackageDirectories;

        try (Stream<Path> paths = Files.walk(root)) {
            packageDeclarations =
                    paths.filter(path -> path.endsWith("package-info.java"))
                            .filter(PackageClassificationTest::isProductionSource)
                            .toList();
        }

        try (Stream<Path> paths = Files.walk(root)) {
            productionPackageDirectories =
                    paths.filter(path -> path.toString().endsWith(".java"))
                            .filter(PackageClassificationTest::isProductionSource)
                            .map(Path::getParent)
                            .collect(Collectors.toSet());
        }

        assertTrue(
                packageDeclarations.size() >= 11, "Expected all foundation package declarations");
        for (Path packageDirectory : productionPackageDirectories) {
            assertTrue(
                    Files.isRegularFile(packageDirectory.resolve("package-info.java")),
                    () -> packageDirectory + " has no package-info.java");
        }
        for (Path declaration : packageDeclarations) {
            String source = Files.readString(declaration);
            assertTrue(PATH.matcher(source).find(), () -> declaration + " has no valid @path");
            assertTrue(OWNER.matcher(source).find(), () -> declaration + " has no valid @owner");
        }
    }

    private static boolean isProductionSource(Path path) {
        return path.toString().replace(File.separatorChar, '/').contains("/src/main/java/");
    }
}
