/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.process.internal.worker

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class DefaultWorkerProcessBuilderTest extends AbstractIntegrationSpec {
    @Unroll
    def "test"() {
        given:
        file("src/main/java/net/kautler/cdi/alternatives/test/Main.java") << '''
            package net.kautler.cdi.alternatives.test;

            import javax.enterprise.context.ApplicationScoped;
            import javax.enterprise.inject.Instance;
            import javax.inject.Inject;
            import java.util.List;

            import static java.util.stream.Collectors.toList;

            @ApplicationScoped
            public class Main {
                private List<Dependency> dependencies;

                @Inject
                private void setDependencies(Instance<Dependency> commands) {
                    dependencies = commands.stream().collect(toList());
                }

                public List<Dependency> getDependencies() {
                    return dependencies;
                }
            }
        '''
        file("src/main/java/net/kautler/cdi/alternatives/test/Dependency.java") << '''
            package net.kautler.cdi.alternatives.test;

            public interface Dependency {
            }
        '''
        file("src/test/groovy/net/kautler/cdi/alternatives/test/Test.groovy") << '''
            package net.kautler.cdi.alternatives.test

            import spock.lang.Specification

            import javax.enterprise.context.ApplicationScoped
            import javax.enterprise.inject.se.SeContainerInitializer

            import static java.lang.Boolean.TRUE

            class Test extends Specification {
                def test() {
                    given:
                        def seContainer = SeContainerInitializer.newInstance()
                                .addProperty('javax.enterprise.inject.scan.implicit', TRUE)
                                .initialize()

                    expect:
                        seContainer.select(Main).get().dependencies.size() == 1

                    cleanup:
                        seContainer?.close()
                }

                @ApplicationScoped
                static class TestDependency implements Dependency {
                }
            }
        '''

        buildFile << '''
            apply plugin: "java"
            apply plugin: "groovy"

            repositories {
                jcenter()
            }

            dependencies {
                implementation("javax.enterprise:cdi-api:2.0")

                testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")

                testRuntimeOnly("org.jboss.weld.se:weld-se-core:3.1.3.Final")
                testRuntimeOnly("org.jboss:jandex:2.1.1.Final")
            }

            //tasks.test {
            //    doFirst {
            //        classpath
            //                .filterNot { it.exists() }
            //                .forEach { it.mkdirs() }
            //    }
            //}

        '''
        settingsFile << '''
            rootProject.name = "cdi-test"
        '''
        expect:
        succeeds("test")
    }
}
