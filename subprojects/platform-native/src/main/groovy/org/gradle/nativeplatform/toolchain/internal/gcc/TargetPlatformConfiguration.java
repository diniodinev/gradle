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

package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.api.Incubating;
import org.gradle.nativeplatform.platform.internal.PlatformInternal;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;

/**
 * A target platform configuration specifies whether a toolchain supports a specific target platform.
 * Furthermore it allows the target platform specific configuration of a toolchain.
 *
 * <pre>
 *     model {
 *          toolChains {
 *               gcc {
 *                   target("arm"){
 *                       cCompiler.withArguments { args ->
 *                          args << "-m32"
 *                          args << "-DFRENCH"
 *                       }
 *                   }
 *               }
 *           }
 *      }
 * </pre>
 */
@Incubating
public interface TargetPlatformConfiguration {
    /**
     * Returns whether a platform is supported or not.
     */
    boolean supportsPlatform(PlatformInternal targetPlatform);

    /**
     *  applies a platform specific toolchain configuration
     */
    void apply(GccPlatformToolChain platformToolChain);
}
