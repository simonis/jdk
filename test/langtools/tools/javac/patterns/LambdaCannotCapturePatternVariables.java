/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8267610
 * @summary LambdaToMethod cannot capture pattern variables. So the TransPatterns should
 *          transform the pattern variables and symbols to normal variables and symbols.
 * @compile --enable-preview -source ${jdk.version} LambdaCannotCapturePatternVariables.java
 * @run main/othervm --enable-preview LambdaCannotCapturePatternVariables
 */

import java.util.function.Supplier;

public class LambdaCannotCapturePatternVariables {

    public static void main(String[] args) {
        var testVar = new LambdaCannotCapturePatternVariables();
        testVar.testInstanceOfPatternVariable(Integer.valueOf(1));
        testVar.testSwitchPatternVariable(Integer.valueOf(1));
        testVar.test(Integer.valueOf(1));
    }

    public Integer testInstanceOfPatternVariable(Object x) {
        if(x instanceof Number y) {
            return ((Supplier<Integer>) (() -> {
                return ((y instanceof Integer z) ? z : 1);
            })).get();
        }
        return null;
    }

    public Integer testSwitchPatternVariable(Object x) {
        switch (x) {
            case Number n: {
                return ((Supplier<Integer>) (() -> {
                    return ((n instanceof Integer i) ? i : 1);
                })).get();
            }
            default: return null;
        }
    }

    // Provided by the user
    public Integer test(Object x) {
        Integer bar = 1;
        return ((x instanceof Number y) ?
                ((Supplier<Integer>) (() -> {
                    return ((y instanceof Integer z) ? z : bar);
                })).get() : bar);
    }
}
