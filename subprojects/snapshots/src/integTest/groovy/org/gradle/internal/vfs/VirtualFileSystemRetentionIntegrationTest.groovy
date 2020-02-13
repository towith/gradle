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

package org.gradle.internal.vfs

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

import static org.gradle.integtests.fixtures.ToBeFixedForInstantExecution.Skip.FLAKY
import static org.gradle.internal.service.scopes.VirtualFileSystemServices.VFS_DROP_PROPERTY
import static org.gradle.internal.service.scopes.VirtualFileSystemServices.VFS_RETENTION_ENABLED_PROPERTY

// The whole test makes no sense if there isn't a daemon to retain the state.
@IgnoreIf({ GradleContextualExecuter.noDaemon || GradleContextualExecuter.vfsRetention })
class VirtualFileSystemRetentionIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def setup() {
        // Make the first build in each test drop the VFS state
        executer.withArgument("-D$VFS_DROP_PROPERTY=true")
        executer.requireIsolatedDaemons()
    }

    @ToBeFixedForInstantExecution(because = "The `run` task is not yet supported for instant execution. The next test can be deleted when this test works with instant execution")
    def "source file changes are recognized"() {
        buildFile << """
            apply plugin: "application"

            application.mainClassName = "Main"
        """

        def mainSourceFileRelativePath = "src/main/java/Main.java"
        def mainSourceFile = file(mainSourceFileRelativePath)
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withRetention().run "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        waitForChangesToBePickedUp()
        withRetention().run "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    // TODO: Delete this test when the `run` task is supported by instant execution, since the coverage is already handled by the test above.
    def "source file changes are recognized (for instant execution)"() {
        buildFile << """
            apply plugin: "application"

            application.mainClassName = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withRetention().run "classes"
        then:
        executedAndNotSkipped ":compileJava", ":classes"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        waitForChangesToBePickedUp()
        withRetention().run "classes"
        then:
        executedAndNotSkipped ":compileJava", ":classes"
    }

    @ToBeFixedForInstantExecution
    def "buildSrc changes are recognized"() {
        def taskSourceFile = file("buildSrc/src/main/java/PrinterTask.java")
        taskSourceFile.text = taskWithGreeting("Hello from original task!")

        buildFile << """
            task hello(type: PrinterTask)
        """

        when:
        withRetention().run "hello"
        then:
        outputContains "Hello from original task!"

        when:
        taskSourceFile.text = taskWithGreeting("Hello from modified task!")
        waitForChangesToBePickedUp()
        withRetention().run "hello"
        then:
        outputContains "Hello from modified task!"
    }

    @ToBeFixedForInstantExecution
    def "Groovy build script changes get recognized"() {
        when:
        buildFile.text = """
            println "Hello from the build!"
        """
        withRetention().run "help"
        then:
        outputContains "Hello from the build!"

        when:
        buildFile.text = """
            println "Hello from the modified build!"
        """
        withRetention().run "help"
        then:
        outputContains "Hello from the modified build!"
    }

    @ToBeFixedForInstantExecution
    def "Kotlin build script changes get recognized"() {
        when:
        buildKotlinFile.text = """
            println("Hello from the build!")
        """
        withRetention().run "help"
        then:
        outputContains "Hello from the build!"

        when:
        buildKotlinFile.text = """
            println("Hello from the modified build!")
        """
        withRetention().run "help"
        then:
        outputContains "Hello from the modified build!"
    }

    @ToBeFixedForInstantExecution
    def "settings script changes get recognized"() {
        when:
        settingsFile.text = """
            println "Hello from settings!"
        """
        withRetention().run "help"
        then:
        outputContains "Hello from settings!"

        when:
        settingsFile.text = """
            println "Hello from modified settings!"
        """
        withRetention().run "help"
        then:
        outputContains "Hello from modified settings!"
    }

    @ToBeFixedForInstantExecution(because = "The `run` task is not yet supported for instant execution.")
    def "source file changes are recognized when retention has just been enabled"() {
        buildFile << """
            apply plugin: "application"

            application.mainClassName = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withoutRetention().run "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        withRetention().run "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    @ToBeFixedForInstantExecution(because = "The `run` task is not yet supported for instant execution.")
    def "source file changes are recognized when retention has just been disabled"() {
        buildFile << """
            apply plugin: "application"

            application.mainClassName = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withRetention().run "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        withoutRetention().run "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    def "incubating message is shown for retention"() {
        buildFile << """
            apply plugin: "java"
        """
        def incubatingMessage = "Virtual file system retention is an incubating feature"

        when:
        withRetention().run("assemble")
        then:
        outputContains(incubatingMessage)

        when:
        withoutRetention().run("assemble")
        then:
        outputDoesNotContain(incubatingMessage)
    }

    @ToBeFixedForInstantExecution(because = "https://github.com/gradle/instant-execution/issues/165")
    def "detects when outputs are removed for tasks without sources"() {
        buildFile << """
            apply plugin: 'base'

            abstract class Producer extends DefaultTask {
                @InputDirectory
                @SkipWhenEmpty
                abstract DirectoryProperty getSources()

                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void listSources() {
                    outputDir.file("output.txt").get().asFile.text = sources.get().asFile.list().join("\\n")
                }
            }

            abstract class Consumer extends DefaultTask {
                @InputFiles
                abstract DirectoryProperty getInputDirectory()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    def input = inputDirectory.file("output.txt").get().asFile
                    if (input.file) {
                        outputFile.asFile.get().text = input.text
                    } else {
                        outputFile.asFile.get().text = "<empty>"
                    }
                }
            }

            task sourceTask(type: Producer) {
                sources = file("sources")
                outputDir = file("build/output")
            }

            task consumer(type: Consumer) {
                inputDirectory = sourceTask.outputDir
                outputFile = file("build/consumer.txt")
            }
        """

        def sourcesDir = file("sources")
        def sourceFile = sourcesDir.file("source.txt").createFile()
        def outputFile = file("build/output/output.txt")

        when:
        withRetention().run ":consumer"
        then:
        executedAndNotSkipped(":sourceTask", ":consumer")
        outputFile.assertExists()

        when:
        sourceFile.delete()
        waitForChangesToBePickedUp()
        withRetention().run ":consumer"
        then:
        executedAndNotSkipped(":sourceTask", ":consumer")
        outputFile.assertDoesNotExist()
    }

    @ToBeFixedForInstantExecution(because = "https://github.com/gradle/instant-execution/issues/165")
    def "detects when stale outputs are removed"() {
        buildFile << """
            apply plugin: 'base'

            task producer {
                inputs.files("input.txt")
                outputs.file("build/output.txt")
                doLast {
                    file("build/output.txt").text = file("input.txt").text
                }
            }
        """

        file("input.txt").text = "input"
        def outputFile = file("build/output.txt")

        when:
        withRetention().run ":producer"
        then:
        executedAndNotSkipped(":producer")
        outputFile.assertExists()

        when:
        invalidateBuildOutputCleanupState()
        waitForChangesToBePickedUp()
        withRetention().run ":producer", "--info"
        then:
        output.contains("Deleting stale output file: ${outputFile.absolutePath}")
        executedAndNotSkipped(":producer")
        outputFile.assertExists()
    }

    def "detects non-incremental cleanup of incremental tasks"() {
        buildFile << """
            abstract class IncrementalTask extends DefaultTask {
                @InputDirectory
                @Incremental
                abstract DirectoryProperty getSources()

                @Input
                abstract Property<String> getInput()

                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void processChanges(InputChanges changes) {
                    outputDir.file("output.txt").get().asFile.text = input.get()
                }
            }

            task incremental(type: IncrementalTask) {
                sources = file("sources")
                input = providers.systemProperty("outputDir")
                outputDir = file("build/\${input.get()}")
            }
        """

        file("sources/input.txt").text = "input"

        when:
        withRetention().run ":incremental", "-DoutputDir=output1"
        then:
        executedAndNotSkipped(":incremental")

        when:
        file("build/output2/overlapping.txt").text = "overlapping"
        waitForChangesToBePickedUp()
        withRetention().run ":incremental", "-DoutputDir=output2"
        then:
        executedAndNotSkipped(":incremental")
        file("build/output1").assertDoesNotExist()

        when:
        waitForChangesToBePickedUp()
        withRetention().run ":incremental", "-DoutputDir=output1"
        then:
        executedAndNotSkipped(":incremental")
        file("build/output1").assertExists()
    }

    @ToBeFixedForInstantExecution(because = "https://github.com/gradle/gradle/issues/11818")
    def "detects changes to manifest"() {
        buildFile << """
            plugins {
                id 'java'
            }

            jar {
                manifest {
                    attributes('Created-By': providers.systemProperty("creator"))
                }
            }
        """

        when:
        withRetention().run "jar", "-Dcreator=first"
        then:
        file("build/tmp/jar/MANIFEST.MF").text.contains("first")
        executedAndNotSkipped(":jar")

        when:
        withRetention().run "jar", "-Dcreator=second"
        then:
        file("build/tmp/jar/MANIFEST.MF").text.contains("second")
        executedAndNotSkipped(":jar")
    }

    @ToBeFixedForInstantExecution(skip = FLAKY, because = "https://github.com/gradle/instant-execution/issues/213")
    def "detects when local state is removed"() {
        buildFile << """
            plugins {
                id 'base'
            }

            @CacheableTask
            abstract class WithLocalStateDirectory extends DefaultTask {
                @LocalState
                abstract DirectoryProperty getLocalStateDirectory()
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doStuff() {
                    def localStateDir = localStateDirectory.get().asFile
                    localStateDir.mkdirs()
                    new File(localStateDir, "localState.txt").text = "localState"
                    outputFile.get().asFile.text = "output"
                }
            }

            abstract class WithOutputFile extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = "outputFile"
                }
            }

            task localStateTask(type: WithLocalStateDirectory) {
                localStateDirectory = file("build/overlap")
                outputFile = file("build/localStateOutput.txt")
            }

            task outputFileTask(type: WithOutputFile) {
                outputFile = file("build/overlap/outputFile.txt")
            }
        """
        def localStateOutputFile = file("build/localStateOutput.txt")

        when:
        withRetention().withBuildCache().run("localStateTask", "outputFileTask")
        then:
        executedAndNotSkipped(":localStateTask", ":outputFileTask")
        localStateOutputFile.exists()

        when:
        localStateOutputFile.delete()
        waitForChangesToBePickedUp()
        withRetention().withBuildCache().run("localStateTask", "outputFileTask")
        then:
        skipped(":localStateTask")
        executedAndNotSkipped(":outputFileTask")
    }

    // This makes sure the next Gradle run starts with a clean BuildOutputCleanupRegistry
    private void invalidateBuildOutputCleanupState() {
        file(".gradle/buildOutputCleanup/cache.properties").text = """
            gradle.version=1.0
        """
    }

    private def withRetention() {
        executer.withArgument  "-D${VFS_RETENTION_ENABLED_PROPERTY}=true"
        this
    }

    private def withoutRetention() {
        executer.withArgument  "-D${VFS_RETENTION_ENABLED_PROPERTY}=false"
        this
    }

    private static String sourceFileWithGreeting(String greeting) {
        """
            public class Main {
                public static void main(String... args) {
                    System.out.println("$greeting");
                }
            }
        """
    }

    private static String taskWithGreeting(String greeting) {
        """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class PrinterTask extends DefaultTask {
                @TaskAction
                public void execute() {
                    System.out.println("$greeting");
                }
            }
        """
    }

    private static void waitForChangesToBePickedUp() {
        Thread.sleep(1000)
    }
}
