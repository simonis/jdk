/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

 /*
 * @test
 * @bug 9999999
 * @summary Test presence of at least one stack frame in imlicit exceptions
 *
 * @requires vm.compiler2.enabled
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UseSerialGC -Xbatch -XX:-UseOnStackReplacement -XX:-TieredCompilation
 *                   -XX:+PrintCompilation -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining
 *                   -XX:CompileCommand=inline,compiler.exceptions.StackFramesInFastThrow::throwImplicitException
 *                   -XX:CompileCommand=inline,compiler.exceptions.StackFramesInFastThrow::level2
 *                   -XX:CompileCommand=option,compiler.exceptions.StackFramesInFastThrow::level1,PrintOptoAssembly
 *                   -XX:PerMethodTrapLimit=0 compiler.exceptions.StackFramesInFastThrow
 */

package compiler.exceptions;

import java.lang.reflect.Method;
import jdk.test.whitebox.WhiteBox;

public class StackFramesInFastThrow {
    public enum ImplicitException {
        NULL_POINTER_EXCEPTION,
        ARITHMETIC_EXCEPTION,
        ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION,
        ARRAY_STORE_EXCEPTION,
        CLASS_CAST_EXCEPTION
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static String[] string_a = new String[1];

    public static Object throwImplicitException(ImplicitException type, Object[] object_a) {
        switch (type) {
            case NULL_POINTER_EXCEPTION: {
                return object_a.length;
            }
            case ARITHMETIC_EXCEPTION: {
                return ((42 / (object_a.length - 1)) > 2) ? null : object_a[0];
            }
            case ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION: {
                return object_a[5];
            }
            case ARRAY_STORE_EXCEPTION: {
                return (object_a[0] = new Object());
            }
            case CLASS_CAST_EXCEPTION: {
                return (ImplicitException[])object_a;
            }
        }
        return null;
    }

    public static Object level2(ImplicitException type, Object[] object_a) {
        return throwImplicitException(type, object_a);
    }

    public static Object level1(ImplicitException type, Object[] object_a) {
        return level2(type, object_a);
    }

    public static void main(String[] args) throws Exception {
        if (!WB.getBooleanVMFlag("ProfileTraps")) {
            // The fast-throw optimzation only works if we're running with -XX:+ProfileTraps
            return;
        }
        boolean omit = WB.getBooleanVMFlag("OmitStackTraceInFastThrow");
        Method level1_m = StackFramesInFastThrow.class.getDeclaredMethod("level1", new Class[] { ImplicitException.class, Object[].class});
        for (int i = 0; i < 2; i++) {
            for (ImplicitException ex : ImplicitException.values()) {
                try {
                    level1(ex, ex == ImplicitException.NULL_POINTER_EXCEPTION ? null : string_a);
                } catch (Exception e) {
                    if (i == 0 || i == 1) {
                        e.printStackTrace(System.out);
                    }
                    if (i == 0) {
                        // Exception thrown by the interpreter should have the full stack trace
                        if (!e.getStackTrace()[3].getMethodName().equals("main")) {
                            throw new Exception("Can't see main() in interpreter stack trace");
                        }
                    }
                    if (i == 1 && omit) {
                        if (e.getStackTrace().length != 0) {
                            throw new Exception("-XX:+OmitStackTraceInFastThrow should generate an emtpy stack trace");
                        }
                    }
                    continue;
                }
                throw new Exception("Should not happen");
            }
            if (i == 0) {
                WB.enqueueMethodForCompilation(level1_m, 4);
            }
        }
    }
}