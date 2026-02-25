/*
 * @test
 * @bug 8273563
 * @summary Test that OptimizeImplicitExceptions handles deoptimization during
 *          exception object allocation correctly. A background thread
 *          continuously deoptimizes the test methods while the main thread
 *          triggers implicit exceptions. If deopt (Unpack_deopt, mode=0)
 *          happens during the new_instance allocation for bytecodes not in
 *          bytecode_should_reexecute (e.g. iastore, invokevirtual), the
 *          exception can be silently swallowed.
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:-UseTLAB -XX:+OptimizeImplicitExceptions -XX:-OmitStackTraceInFastThrow
 *      compiler.exceptions.TestImplicitExceptionDeopt
 */

package compiler.exceptions;

import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Method;

public class TestImplicitExceptionDeopt {

    static final WhiteBox WB = WhiteBox.getWhiteBox();

    static Object nullObj = null;
    static int[] arr = new int[10];
    static int sink;
    int field;
    static TestImplicitExceptionDeopt nullInstance = null;

    static final int ITERS = Integer.getInteger("compiler.exceptions.TestImplicitExceptionDeopt.iterations", 50_000);

    static int npeMissCount;
    static int aioobMissCount;
    static int getFieldMissCount;
    static int totalExceptionCount;
    static volatile boolean done;

    // Sink — prevents EA from scalar-replacing the allocation
    static volatile Throwable lastException;

    static void testNPE() {
        try {
            nullObj.toString(); // hashCode();
            npeMissCount++; // impossible?!
            //done = true;
        } catch (NullPointerException e) {
            lastException = e;
            totalExceptionCount++;
        }
    }

    static void testAIOOB() {
        try {
            arr[100] = 1;
            aioobMissCount++; // impossible?!
            //done = true;
        } catch (ArrayIndexOutOfBoundsException e) {
            lastException = e;
            totalExceptionCount++;
        }
    }

    static void testGetFieldNPE() {
        try {
	    sink = nullInstance.field;  // getfield on null → NPE, reexecute=true
            getFieldMissCount++;
            //done = true;
        } catch (NullPointerException e) {
	    lastException = e;
            totalExceptionCount++;
        }
    }

    public static void main(String[] args) throws Exception {
        Method npeMethod = TestImplicitExceptionDeopt.class.getDeclaredMethod("testNPE");
        Method aioobMethod = TestImplicitExceptionDeopt.class.getDeclaredMethod("testAIOOB");
        Method fieldNpeMethod = TestImplicitExceptionDeopt.class.getDeclaredMethod("testGetFieldNPE");

        // Background thread: continuously deoptimize the test methods
        new Thread(() -> {
            while (!done) {
                WB.deoptimizeMethod(npeMethod);
                WB.deoptimizeMethod(aioobMethod);
                WB.deoptimizeMethod(fieldNpeMethod);
            }
        }).start();

        for (int i = 0; i < ITERS; i++) {
            testNPE();
            testAIOOB();
            testGetFieldNPE();
            if (done) {
              break;
            }
        }

        done = true;

        if (npeMissCount > 0 || aioobMissCount > 0 || getFieldMissCount > 0) {
            System.out.println(String.format("BUG: Swallowed exceptions. NPEs: %d, AIOOBs: %d, getfield NPES: %d.", npeMissCount, aioobMissCount, getFieldMissCount));
        }
        if (totalExceptionCount != ITERS * 3) {
            System.out.println(String.format("Wrong exception count. Expected: %d, actual: %d.", ITERS * 3, totalExceptionCount));
        }
        System.out.println("PASSED");
    }
}
