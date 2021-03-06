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

package org.gradle.model.internal.report.unbound

import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.util.TextUtil
import spock.lang.Specification

class UnboundRulesReporterSpec extends Specification {

    def output = new StringWriter()
    def reporter = new UnboundRulesReporter(new PrintWriter(output), "> ")

    def "reports on unbound rules"() {
        when:
        reporter.reportOn([
                UnboundRule.builder().descriptor(new SimpleModelRuleDescriptor("r1"))
                        .mutableInput(UnboundRuleInput.builder().path("parent.p1").type(String.name))
                        .mutableInput(UnboundRuleInput.builder().bound().path("parent.p2").type(Integer.name))
                        .immutableInput(UnboundRuleInput.builder().path("parent.p3").type(Number.name).suggestions(["parent.p31", "parent.p32"]))
                        .immutableInput(UnboundRuleInput.builder().type(Number.name))
                        .immutableInput(UnboundRuleInput.builder().bound().path("parent.p5").type(Number.name)).build()
        ])

        then:
        output.toString() == TextUtil.toPlatformLineSeparators("""> r1
>   Mutable:
>     - parent.p1 (java.lang.String)
>     + parent.p2 (java.lang.Integer)
>   Immutable:
>     - parent.p3 (java.lang.Number) - suggestions: parent.p31, parent.p32
>     - <unspecified> (java.lang.Number)
>     + parent.p5 (java.lang.Number)""")
    }
}
