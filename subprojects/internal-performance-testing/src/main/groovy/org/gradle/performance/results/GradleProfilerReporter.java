/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.results;

import com.google.common.base.Strings;
import org.gradle.profiler.BenchmarkResultCollector;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.report.CsvGenerator;
import org.gradle.profiler.report.HtmlGenerator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class GradleProfilerReporter implements DataReporter<PerformanceTestResult> {
    private static final String DEBUG_ARTIFACTS_DIRECTORY_PROPERTY_NAME = "org.gradle.performance.debugArtifactsDirectory";
    private BenchmarkResultCollector resultCollector;
    private final File debugArtifactsDirectory;

    public GradleProfilerReporter(File fallbackDirectory) {
        String debugArtifactsDirectoryPath = System.getProperty(DEBUG_ARTIFACTS_DIRECTORY_PROPERTY_NAME);
        this.debugArtifactsDirectory = Strings.isNullOrEmpty(debugArtifactsDirectoryPath)
            ? fallbackDirectory
            : new File(debugArtifactsDirectoryPath);

    }

    @Override
    public void report(PerformanceTestResult results) {
        resultCollector.summarizeResults(line ->
            System.out.println("  " + line)
        );
        try {
            // TODO Properly pass in the invocation settings for this benchmark here
            InvocationSettings settings = new InvocationSettings.InvocationSettingsBuilder()
                .setBenchmarkTitle(results.getTestId())
                .build();
            resultCollector.write(settings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public BenchmarkResultCollector getResultCollector(String displayName) {
        if (resultCollector == null) {
            File baseDir = new File(debugArtifactsDirectory, displayName.replaceAll("[^a-zA-Z0-9]", "_"));
            baseDir.mkdirs();
            this.resultCollector = new BenchmarkResultCollector(
                new CsvGenerator(new File(baseDir, "benchmark.csv"), CsvGenerator.Format.LONG),
                new HtmlGenerator(new File(baseDir, "benchmark.html"))
            );
        }
        return resultCollector;
    }

    @Override
    public void close() {
    }
}
