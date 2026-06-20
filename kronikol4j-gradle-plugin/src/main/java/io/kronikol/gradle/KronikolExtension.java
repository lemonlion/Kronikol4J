package io.kronikol.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code kronikol { }} build extension.
 *
 * <pre>
 * kronikol {
 *   reportDir = layout.buildDirectory.dir("reports/kronikol")
 *   title = "CI"
 * }
 * </pre>
 */
public interface KronikolExtension {

    /** Directory the merged HTML report is written to. */
    DirectoryProperty getReportDir();

    /** Report title. */
    Property<String> getTitle();
}
