/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.gcc.version;

import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;
import org.gradle.nativeplatform.platform.internal.DefaultArchitecture;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given a File pointing to an (existing) gcc/g++/clang/clang++ binary, extracts the version number by running with -dM -E and scraping the output.
 */
public class GccVersionDeterminer {
    private static final Pattern DEFINE_PATTERN = Pattern.compile("\\s*#define\\s+(\\S+)\\s+(.*)");

    private final Transformer<String, File> outputProducer;
    private final boolean clang;

    public static GccVersionDeterminer forGcc(ExecActionFactory execActionFactory) {
        return new GccVersionDeterminer(execActionFactory, false);
    }

    public static GccVersionDeterminer forClang(ExecActionFactory execActionFactory) {
        return new GccVersionDeterminer(execActionFactory, true);
    }

    GccVersionDeterminer(ExecActionFactory execActionFactory, boolean expectClang) {
        this.clang = expectClang;
        this.outputProducer = new GccVersionOutputProducer(execActionFactory);
    }

    public GccVersionResult getGccMetaData(File gccBinary) {
        String output = outputProducer.transform(gccBinary);
        if (output == null) {
            return new BrokenResult(String.format("Could not determine %s version: failed to execute %s -v.", getDescription(), gccBinary.getName()));
        }
        return transform(output, gccBinary);
    }

    private String getDescription() {
        return clang ? "Clang" : "GCC";
    }

    private GccVersionResult transform(String output, File gccBinary) {
        BufferedReader reader = new BufferedReader(new StringReader(output));
        String line;
        Map<String, String> defines = new HashMap<String, String>();
        try {
            while ((line = reader.readLine()) != null) {
                Matcher matcher = DEFINE_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    return new BrokenResult(String.format("Could not determine %s version: %s produced unexpected output.", getDescription(), gccBinary.getName()));
                }
                defines.put(matcher.group(1), matcher.group(2));
            }
        } catch (IOException e) {
            // Should not happen reading from a StringReader
            throw new UncheckedIOException(e);
        }
        if (!defines.containsKey("__GNUC__")) {
            return new BrokenResult(String.format("Could not determine %s version: %s produced unexpected output.", getDescription(), gccBinary.getName()));
        }
        int major;
        int minor;
        int patch;
        if (clang) {
            if (!defines.containsKey("__clang__")) {
                return new BrokenResult(String.format("%s appears to be GCC rather than Clang. Treating it as GCC.", gccBinary.getName()));
            }
            major = toInt(defines.get("__clang_major__"));
            minor = toInt(defines.get("__clang_minor__"));
            patch = toInt(defines.get("__clang_patchlevel__"));
        } else {
            if (defines.containsKey("__clang__")) {
                return new BrokenResult(String.format("XCode %s is a wrapper around Clang. Treating it as Clang and not GCC.", gccBinary.getName()));
            }
            major = toInt(defines.get("__GNUC__"));
            minor = toInt(defines.get("__GNUC_MINOR__"));
            patch = toInt(defines.get("__GNUC_PATCHLEVEL__"));
        }
        boolean i386 = defines.containsKey("__i386__");
        boolean amd64 = defines.containsKey("__amd64__");
        ArchitectureInternal architecture = i386 ? new DefaultArchitecture("i386", ArchitectureInternal.InstructionSet.X86, 32)
                : amd64 ? new DefaultArchitecture("amd64", ArchitectureInternal.InstructionSet.X86, 64)
                : ArchitectureInternal.TOOL_CHAIN_DEFAULT;
        return new DefaultGccVersionResult(new VersionNumber(major, minor, patch, null), architecture, clang);
    }

    private int toInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static class DefaultGccVersionResult implements GccVersionResult {
        private final VersionNumber scrapedVersion;
        private final ArchitectureInternal architecture;
        private final boolean clang;

        public DefaultGccVersionResult(VersionNumber scrapedVersion, ArchitectureInternal architecture, boolean clang) {
            this.scrapedVersion = scrapedVersion;
            this.architecture = architecture;
            this.clang = clang;
        }

        public VersionNumber getVersion() {
            return scrapedVersion;
        }

        public boolean isClang() {
            return clang;
        }

        public ArchitectureInternal getDefaultArchitecture() {
            return architecture;
        }

        public boolean isAvailable() {
            return true;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    static class GccVersionOutputProducer implements Transformer<String, File> {
        
        private final ExecActionFactory execActionFactory;

        GccVersionOutputProducer(ExecActionFactory execActionFactory) {
            this.execActionFactory = execActionFactory;
        }

        public String transform(File gccBinary) {
            ExecAction exec = execActionFactory.newExecAction();
            exec.executable(gccBinary.getAbsolutePath());
            exec.setWorkingDir(gccBinary.getParentFile());
            exec.args("-dM", "-E", "-");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exec.setStandardOutput(baos);
            exec.setIgnoreExitValue(true);
            ExecResult result = exec.execute();

            int exitValue = result.getExitValue();
            if (exitValue == 0) {
                return new String(baos.toByteArray());
            } else {
                return null;
            }
        }
    }

    private static class BrokenResult implements GccVersionResult {
        private final String message;

        private BrokenResult(String message) {
            this.message = message;
        }

        public VersionNumber getVersion() {
            throw new UnsupportedOperationException();
        }

        public boolean isClang() {
            throw new UnsupportedOperationException();
        }

        public ArchitectureInternal getDefaultArchitecture() {
            throw new UnsupportedOperationException();
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }
}
