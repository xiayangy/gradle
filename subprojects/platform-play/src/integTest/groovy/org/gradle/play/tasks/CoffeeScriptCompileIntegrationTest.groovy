/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks

class CoffeeScriptCompileIntegrationTest extends AbstractCoffeeScriptCompileIntegrationTest {
    @Override
    String getDefaultSourceSet() {
        return "CoffeeScriptAssets"
    }

    def setup() {
        buildFile << """
            plugins {
                id 'play'
                id 'play-coffeescript'
            }

            repositories{
                jcenter()
                maven{
                    name = "typesafe-maven-release"
                    url = "https://repo.typesafe.com/typesafe/maven-releases"
                }
                maven {
                    name = "gradle-js"
                    url = "https://repo.gradle.org/gradle/javascript-public"
                }
            }
        """
    }

    def "compiles default coffeescript source set as part of play application build" () {
        when:
        withCoffeeScriptSource(assets("test.coffee"))
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        matchesExpectedRaw("test.js")
        matchesExpected("test.min.js")
        assetsJar.containsDescendants(
                "public/test.js",
                "public/test.min.js"
        )
    }

    def "compiles multiple coffeescript source sets as part of play application build" () {
        given:
        withCoffeeScriptSource(assets("test1.coffee"))
        withCoffeeScriptSource("src/play/extraCoffeeScript/xxx/test2.coffee")
        withCoffeeScriptSource("extra/a/b/c/test3.coffee")
        withJavaScriptSource('src/play/extraJavaScript/test/test4.js')
        withJavaScriptSource(assets("test5.js"))

        when:
        buildFile << """
            model {
                components {
                    play {
                        sources {
                            extraCoffeeScript(CoffeeScriptSourceSet)
                            anotherCoffeeScript(CoffeeScriptSourceSet) {
                                source.srcDir "extra"
                            }
                            extraJavaScript(JavaScriptSourceSet)
                        }
                    }
                }
            }
        """
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":compilePlayBinaryExtraCoffeeScript",
                ":minifyPlayBinaryExtraCoffeeScript",
                ":compilePlayBinaryAnotherCoffeeScript",
                ":minifyPlayBinaryAnotherCoffeeScript",
                ":minifyPlayBinaryJavaScriptAssets",
                ":minifyPlayBinaryExtraJavaScript",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        matchesExpectedRaw("test1.js")
        matchesExpectedRaw("ExtraCoffeeScript", "xxx/test2.js")
        matchesExpectedRaw("AnotherCoffeeScript", "a/b/c/test3.js")
        matchesExpected("test1.min.js")
        matchesExpected("ExtraCoffeeScript", "xxx/test2.min.js")
        matchesExpected("AnotherCoffeeScript", "a/b/c/test3.min.js")
        assetsJar.containsDescendants(
                "public/test1.js",
                "public/xxx/test2.js",
                "public/a/b/c/test3.js",
                "public/test/test4.js",
                "public/test5.js",
                "public/test1.min.js",
                "public/xxx/test2.min.js",
                "public/a/b/c/test3.min.js",
                "public/test/test4.min.js",
                "public/test5.min.js"
        )
    }

    def "does not recompile when inputs and outputs are unchanged" () {
        given:
        withCoffeeScriptSource(assets("test.coffee"))
        succeeds "assemble"

        when:
        succeeds "assemble"

        then:
        skipped(":compilePlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
    }

    def "recompiles when inputs are changed" () {
        given:
        withCoffeeScriptSource(assets("test.coffee"))
        succeeds "assemble"

        when:
        assets("test.coffee") << '\nalert "this is a change!"'
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
    }

    def "recompiles when outputs are removed" () {
        given:
        withCoffeeScriptSource(assets("test.coffee"))
        succeeds "assemble"

        when:
        compiled("test.js").delete()
        minified("test.js").delete()
        minified("test.min.js").delete()
        assetsJar.file.delete()
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        compiled("test.js").exists()
    }

    def "cleans removed source file on compile" () {
        given:
        withCoffeeScriptSource(assets("test1.coffee"))
        def source2 = withCoffeeScriptSource(assets("test2.coffee"))

        when:
        succeeds "assemble"

        then:
        assetsJar.containsDescendants(
                "public/test1.js",
                "public/test2.js",
                "public/test1.min.js",
                "public/test2.min.js"
        )

        when:
        source2.delete()
        succeeds "assemble"

        then:
        ! compiled("test2.js").exists()
        ! minified("test2.js").exists()
        ! minified("test2.js").exists()
        assetsJar.countFiles("public/test2.js") == 0
        assetsJar.countFiles("public/test2.min.js") == 0
    }

    def "produces sensible error on compile failure" () {
        given:
        assets("test1.coffee") << "if"

        when:
        fails "assemble"

        then:
        failure.assertHasDescription "Execution failed for task ':compilePlayBinaryCoffeeScriptAssets'."
        failure.assertHasCause "Failed to compile coffeescript file: test1.coffee"
        failure.assertHasCause "SyntaxError: unexpected if (coffee-script-js-1.8.0.js#10)"
    }
}
