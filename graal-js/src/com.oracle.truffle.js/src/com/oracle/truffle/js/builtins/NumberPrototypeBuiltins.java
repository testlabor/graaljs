/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.builtins;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.builtins.NumberPrototypeBuiltinsFactory.JSNumberToExponentialNodeGen;
import com.oracle.truffle.js.builtins.NumberPrototypeBuiltinsFactory.JSNumberToFixedNodeGen;
import com.oracle.truffle.js.builtins.NumberPrototypeBuiltinsFactory.JSNumberToLocaleStringIntlNodeGen;
import com.oracle.truffle.js.builtins.NumberPrototypeBuiltinsFactory.JSNumberToLocaleStringNodeGen;
import com.oracle.truffle.js.builtins.NumberPrototypeBuiltinsFactory.JSNumberToPrecisionNodeGen;
import com.oracle.truffle.js.builtins.NumberPrototypeBuiltinsFactory.JSNumberToStringNodeGen;
import com.oracle.truffle.js.builtins.NumberPrototypeBuiltinsFactory.JSNumberValueOfNodeGen;
import com.oracle.truffle.js.nodes.cast.JSDoubleToStringNode;
import com.oracle.truffle.js.nodes.cast.JSToIntegerAsIntNode;
import com.oracle.truffle.js.nodes.cast.JSToNumberNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.intl.InitializeNumberFormatNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSException;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSNumber;
import com.oracle.truffle.js.runtime.builtins.JSNumberObject;
import com.oracle.truffle.js.runtime.builtins.intl.JSNumberFormat;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.Undefined;

/**
 * Contains builtins for {@linkplain JSNumber}.prototype.
 */
public final class NumberPrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<NumberPrototypeBuiltins.NumberPrototype> {

    public static final JSBuiltinsContainer BUILTINS = new NumberPrototypeBuiltins();

    protected NumberPrototypeBuiltins() {
        super(JSNumber.PROTOTYPE_NAME, NumberPrototype.class);
    }

    public enum NumberPrototype implements BuiltinEnum<NumberPrototype> {
        toExponential(1),
        toFixed(1),
        toLocaleString(0),
        toPrecision(1),
        toString(1),
        valueOf(0);

        private final int length;

        NumberPrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, NumberPrototype builtinEnum) {
        switch (builtinEnum) {
            case toExponential:
                return JSNumberToExponentialNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case toFixed:
                return JSNumberToFixedNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case toLocaleString:
                if (context.isOptionIntl402()) {
                    return JSNumberToLocaleStringIntlNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
                } else {
                    return JSNumberToLocaleStringNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
                }
            case toPrecision:
                return JSNumberToPrecisionNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case toString:
                return JSNumberToStringNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case valueOf:
                return JSNumberValueOfNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
        }
        return null;
    }

    public abstract static class JSNumberOperation extends JSBuiltinNode {

        public JSNumberOperation(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        protected Number getNumberValue(JSDynamicObject obj) {
            return JSNumber.valueOf(obj);
        }

        protected double getDoubleValue(JSDynamicObject obj) {
            return JSRuntime.doubleValue(JSNumber.valueOf(obj));
        }

        protected double getDoubleValue(InteropLibrary interop, Object obj) {
            assert JSRuntime.isForeignObject(obj);
            if (interop.fitsInDouble(obj)) {
                try {
                    return interop.asDouble(obj);
                } catch (UnsupportedMessageException ex) {
                    throw Errors.createTypeErrorUnboxException(obj, ex, this);
                }
            }
            throw Errors.createTypeErrorNotANumber(obj);
        }

    }

    @ImportStatic({JSConfig.class})
    public abstract static class JSNumberToStringNode extends JSNumberOperation {

        public JSNumberToStringNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Child private JSDoubleToStringNode doubleToStringNode;
        private final BranchProfile radixOtherBranch = BranchProfile.create();
        private final BranchProfile radixErrorBranch = BranchProfile.create();

        protected Object doubleToString(double value) {
            if (doubleToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                doubleToStringNode = insert(JSDoubleToStringNode.create());
            }
            return doubleToStringNode.executeString(value);
        }

        protected boolean isRadix10(Object radix) {
            return radix == Undefined.instance || (radix instanceof Integer && ((Integer) radix) == 10);
        }

        /**
         * Guard used to ensure that the parameter is a JSObject containing a JSNumber, that hosts
         * an Integer.
         */
        public static boolean isJSNumberInteger(JSNumberObject thisObj) {
            return JSNumber.valueOf(thisObj) instanceof Integer;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isJSNumberInteger(thisObj)", "isRadix10(radix)"})
        protected Object toStringIntRadix10(JSNumberObject thisObj, Object radix) {
            Integer i = (Integer) getNumberValue(thisObj);
            return Strings.fromInt(i.intValue());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isJSNumber(thisObj)", "isRadix10(radix)"})
        protected Object toStringRadix10(JSDynamicObject thisObj, Object radix) {
            return doubleToString(getDoubleValue(thisObj));
        }

        @Specialization(guards = {"isJSNumber(thisObj)", "!isUndefined(radix)"})
        protected Object toString(JSDynamicObject thisObj, Object radix,
                        @Shared("toInt") @Cached("create()") JSToIntegerAsIntNode toIntegerNode) {
            return toStringIntl(getDoubleValue(thisObj), radix, toIntegerNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isJavaNumber(thisObj)", "isNumberInteger(thisObj)", "isRadix10(radix)"})
        protected Object toStringPrimitiveIntRadix10(Object thisObj, Object radix) {
            Integer i = (Integer) thisObj;
            return Strings.fromInt(i.intValue());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isJavaNumber(thisObj)", "isRadix10(radix)"})
        protected Object toStringPrimitiveRadix10(Object thisObj, Object radix) {
            Number n = (Number) thisObj;
            return doubleToString(JSRuntime.doubleValue(n));
        }

        @Specialization
        protected Object toStringPrimitiveRadixInt(Number thisObj, int radix) {
            return toStringIntl(JSRuntime.doubleValue(thisObj), radix);
        }

        @Specialization(guards = {"!isUndefined(radix)"}, replaces = "toStringPrimitiveRadixInt")
        protected Object toStringPrimitive(Number thisObj, Object radix,
                        @Shared("toInt") @Cached("create()") JSToIntegerAsIntNode toIntegerNode) {
            return toStringIntl(JSRuntime.doubleValue(thisObj), radix, toIntegerNode);
        }

        @Specialization(guards = "isForeignObject(thisObj)", limit = "InteropLibraryLimit")
        protected Object toStringForeignObject(Object thisObj, Object radix,
                        @Cached("create()") JSToIntegerAsIntNode toIntegerNode,
                        @CachedLibrary("thisObj") InteropLibrary interop) {
            return toStringIntl(getDoubleValue(interop, thisObj), (radix == Undefined.instance) ? 10 : radix, toIntegerNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isJSNumber(thisObj)", "!isJavaNumber(thisObj)", "!isForeignObject(thisObj)"})
        protected String toStringNoNumber(Object thisObj, Object radix) {
            throw Errors.createTypeErrorNotANumber(thisObj);
        }

        private Object toStringIntl(double numberVal, Object radix, JSToIntegerAsIntNode toIntegerNode) {
            int radixVal = toIntegerNode.executeInt(radix);
            return toStringIntl(numberVal, radixVal);
        }

        private Object toStringIntl(double numberVal, int radixVal) {
            if (radixVal < 2 || radixVal > 36) {
                radixErrorBranch.enter();
                throw Errors.createRangeError("toString() expects radix in range 2-36");
            }
            if (radixVal == 10) {
                return doubleToString(numberVal);
            } else {
                radixOtherBranch.enter();
                return JSRuntime.doubleToString(numberVal, radixVal);
            }
        }
    }

    @ImportStatic({JSConfig.class})
    public abstract static class JSNumberToLocaleStringNode extends JSNumberOperation {

        public JSNumberToLocaleStringNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization(guards = "isJSNumber(thisObj)")
        protected Object toLocaleString(JSDynamicObject thisObj) {
            double d = getDoubleValue(thisObj);
            return toLocaleStringIntl(d);
        }

        @Specialization(guards = "isJavaNumber(thisObj)")
        protected Object toLocaleStringPrimitive(Object thisObj) {
            double d = JSRuntime.doubleValue((Number) thisObj);
            return toLocaleStringIntl(d);
        }

        private static Object toLocaleStringIntl(double d) {
            if (JSRuntime.doubleIsRepresentableAsInt(d)) {
                return Strings.fromInt((int) d);
            } else {
                return Strings.fromDouble(d);
            }
        }

        @Specialization(guards = "isForeignObject(thisObj)", limit = "InteropLibraryLimit")
        protected Object toLocaleStringForeignObject(Object thisObj,
                        @CachedLibrary("thisObj") InteropLibrary interop) {
            return toLocaleStringIntl(getDoubleValue(interop, thisObj));
        }

        @Fallback
        protected String toLocaleStringOther(Object thisNumber) {
            throw Errors.createTypeErrorNotANumber(thisNumber);
        }
    }

    /**
     * Implementation of the Number.prototype.toLocaleString() method as specified by ECMAScript.
     * Internationalization API, https://tc39.github.io/ecma402/#sup-number.prototype.tolocalestring
     */
    @ImportStatic({JSConfig.class})
    public abstract static class JSNumberToLocaleStringIntlNode extends JSNumberOperation {

        @Child InitializeNumberFormatNode initNumberFormatNode;

        public JSNumberToLocaleStringIntlNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
            this.initNumberFormatNode = InitializeNumberFormatNode.createInitalizeNumberFormatNode(context);
        }

        @TruffleBoundary
        private JSDynamicObject createNumberFormat(Object locales, Object options) {
            JSDynamicObject numberFormatObj = JSNumberFormat.create(getContext(), getRealm());
            initNumberFormatNode.executeInit(numberFormatObj, locales, options);
            return numberFormatObj;
        }

        @Specialization(guards = "isJSNumber(thisObj)")
        protected TruffleString jsNumberToLocaleString(JSDynamicObject thisObj, Object locales, Object options) {
            JSDynamicObject numberFormatObj = createNumberFormat(locales, options);
            return JSNumberFormat.format(numberFormatObj, getNumberValue(thisObj));
        }

        @Specialization(guards = "isJavaNumber(thisObj)")
        protected TruffleString javaNumberToLocaleString(Object thisObj, Object locales, Object options) {
            JSDynamicObject numberFormatObj = createNumberFormat(locales, options);
            return JSNumberFormat.format(numberFormatObj, JSRuntime.doubleValue((Number) thisObj));
        }

        @Specialization(guards = "isForeignObject(thisObj)", limit = "InteropLibraryLimit")
        protected TruffleString toLocaleStringForeignObject(Object thisObj, Object locales, Object options,
                        @CachedLibrary("thisObj") InteropLibrary interop) {
            double doubleValue = getDoubleValue(interop, thisObj);
            JSDynamicObject numberFormatObj = createNumberFormat(locales, options);
            return JSNumberFormat.format(numberFormatObj, doubleValue);
        }

        @Fallback
        @SuppressWarnings("unused")
        protected Object failForNonNumbers(Object notANumber, Object locales, Object options) {
            throw Errors.createTypeErrorNotANumber(notANumber);
        }
    }

    @ImportStatic({JSConfig.class})
    public abstract static class JSNumberValueOfNode extends JSNumberOperation {

        public JSNumberValueOfNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization(guards = "isJSNumber(thisNumber)")
        protected Number valueOf(JSDynamicObject thisNumber) {
            return getNumberValue(thisNumber);
        }

        @Specialization(guards = "isJavaNumber(thisNumber)")
        protected double valueOfPrimitive(Object thisNumber) {
            return JSRuntime.doubleValue((Number) thisNumber);
        }

        @Specialization(guards = "isForeignObject(thisNumber)", limit = "InteropLibraryLimit")
        protected double valueOfForeignObject(Object thisNumber,
                        @CachedLibrary("thisNumber") InteropLibrary interop) {
            return getDoubleValue(interop, thisNumber);
        }

        @Fallback
        protected Object valueOfOther(Object thisNumber) {
            throw Errors.createTypeErrorNotANumber(thisNumber);
        }
    }

    @ImportStatic({JSConfig.class})
    public abstract static class JSNumberToFixedNode extends JSNumberOperation {
        private final BranchProfile digitsErrorBranch = BranchProfile.create();
        private final BranchProfile nanBranch = BranchProfile.create();
        private final ConditionProfile dtoaOrString = ConditionProfile.createBinaryProfile();
        @Child protected JSDoubleToStringNode doubleToStringNode;

        protected JSNumberToFixedNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization(guards = "isJSNumber(thisNumber)")
        protected Object toFixed(JSDynamicObject thisNumber, Object fractionDigits,
                        @Shared("toInt") @Cached("create()") JSToIntegerAsIntNode toIntegerNode) {
            int digits = toIntegerNode.executeInt(fractionDigits);
            return toFixedIntl(getDoubleValue(thisNumber), digits);
        }

        @Specialization(guards = "isJavaNumber(thisNumber)")
        protected Object toFixedJava(Object thisNumber, Object fractionDigits,
                        @Shared("toInt") @Cached("create()") JSToIntegerAsIntNode toIntegerNode) {
            int digits = toIntegerNode.executeInt(fractionDigits);
            return toFixedIntl(JSRuntime.doubleValue((Number) thisNumber), digits);
        }

        @Specialization(guards = "isForeignObject(thisNumber)", limit = "InteropLibraryLimit")
        protected Object toFixedForeignObject(Object thisNumber, Object fractionDigits,
                        @Cached("create()") JSToIntegerAsIntNode toIntegerNode,
                        @CachedLibrary("thisNumber") InteropLibrary interop) {
            double doubleValue = getDoubleValue(interop, thisNumber);
            int digits = toIntegerNode.executeInt(fractionDigits);
            return toFixedIntl(doubleValue, digits);
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object toFixedGeneric(Object thisNumber, Object fractionDigits) {
            throw Errors.createTypeErrorNotANumber(thisNumber);
        }

        private Object toFixedIntl(double value, int digits) {
            if (0 > digits || digits > ((getContext().getEcmaScriptVersion() >= JSConfig.ECMAScript2018) ? 100 : 20)) {
                digitsErrorBranch.enter();
                throw Errors.createRangeError("toFixed() fraction digits need to be in range 0-100");
            }
            if (Double.isNaN(value)) {
                nanBranch.enter();
                return Strings.NAN;
            }
            if (dtoaOrString.profile(value >= 1E21 || value <= -1E21)) {
                return toString(value);
            } else {
                return JSRuntime.formatDtoAFixed(value, digits);
            }
        }

        private Object toString(double value) {
            if (doubleToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                doubleToStringNode = insert(JSDoubleToStringNode.create());
            }
            return doubleToStringNode.executeString(value);
        }
    }

    @ImportStatic({JSConfig.class})
    public abstract static class JSNumberToExponentialNode extends JSNumberOperation {

        public JSNumberToExponentialNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isJSNumber(thisNumber)", "isUndefined(fractionDigits)"})
        protected Object toExponentialUndefined(JSDynamicObject thisNumber, Object fractionDigits) {
            double doubleValue = getDoubleValue(thisNumber);
            return toExponentialStandard(doubleValue);
        }

        @Specialization(guards = {"isJSNumber(thisNumber)", "!isUndefined(fractionDigits)"})
        protected Object toExponential(JSDynamicObject thisNumber, Object fractionDigits,
                        @Cached @Shared("digitsError") BranchProfile digitsErrorBranch,
                        @Shared("toInt") @Cached("create()") JSToIntegerAsIntNode toIntegerNode) {
            double doubleValue = getDoubleValue(thisNumber);
            int digits = toIntegerNode.executeInt(fractionDigits);
            return toExponential(doubleValue, digits, digitsErrorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isJavaNumber(thisNumber)", "isUndefined(fractionDigits)"})
        protected Object toExponentialPrimitiveUndefined(Object thisNumber, Object fractionDigits) {
            double doubleValue = JSRuntime.doubleValue((Number) thisNumber);
            return toExponentialStandard(doubleValue);
        }

        @Specialization(guards = {"isJavaNumber(thisNumber)", "!isUndefined(fractionDigits)"})
        protected Object toExponentialPrimitive(Object thisNumber, Object fractionDigits,
                        @Cached @Shared("digitsError") BranchProfile digitsErrorBranch,
                        @Shared("toInt") @Cached("create()") JSToIntegerAsIntNode toIntegerNode) {
            double doubleValue = JSRuntime.doubleValue((Number) thisNumber);
            int digits = toIntegerNode.executeInt(fractionDigits);
            return toExponential(doubleValue, digits, digitsErrorBranch);
        }

        @Specialization(guards = {"isForeignObject(thisNumber)", "isUndefined(fractionDigits)"}, limit = "InteropLibraryLimit")
        protected Object toExponentialForeignObjectUndefined(Object thisNumber, @SuppressWarnings("unused") Object fractionDigits,
                        @CachedLibrary("thisNumber") InteropLibrary interop) {
            double doubleValue = getDoubleValue(interop, thisNumber);
            return toExponentialStandard(doubleValue);
        }

        @Specialization(guards = {"isForeignObject(thisNumber)", "!isUndefined(fractionDigits)"}, limit = "InteropLibraryLimit")
        protected Object toExponentialForeignObject(Object thisNumber, Object fractionDigits,
                        @Cached BranchProfile digitsErrorBranch,
                        @Cached("create()") JSToIntegerAsIntNode toIntegerNode,
                        @CachedLibrary("thisNumber") InteropLibrary interop) {
            double doubleValue = getDoubleValue(interop, thisNumber);
            int digits = toIntegerNode.executeInt(fractionDigits);
            return toExponential(doubleValue, digits, digitsErrorBranch);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isJSNumber(thisNumber)", "!isJavaNumber(thisNumber)", "!isForeignObject(thisNumber)"})
        protected Object toExponentialOther(Object thisNumber, Object fractionDigits) {
            throw Errors.createTypeErrorNotANumber(thisNumber);
        }

        private static Object toExponentialStandard(double value) {
            if (Double.isNaN(value)) {
                return Strings.NAN;
            } else if (Double.isInfinite(value)) {
                return (value < 0) ? Strings.NEGATIVE_INFINITY : Strings.INFINITY;
            }
            return JSRuntime.formatDtoAExponential(value);
        }

        private Object toExponential(double value, int digits, BranchProfile digitsErrorBranch) {
            if (Double.isNaN(value)) {
                return Strings.NAN;
            } else if (Double.isInfinite(value)) {
                return (value < 0) ? Strings.NEGATIVE_INFINITY : Strings.INFINITY;
            }
            checkDigits(digits, digitsErrorBranch);
            return JSRuntime.formatDtoAExponential(value, digits);
        }

        private void checkDigits(int digits, BranchProfile digitsErrorBranch) {
            final int maxDigits = getContext().getEcmaScriptVersion() >= JSConfig.ECMAScript2018 ? 100 : 20;
            if (0 > digits || digits > maxDigits) {
                digitsErrorBranch.enter();
                throw digitsRangeError(maxDigits);
            }
        }

        @TruffleBoundary
        private static JSException digitsRangeError(int maxDigits) {
            return Errors.createRangeError("toExponential() fraction digits need to be in range 0-" + maxDigits);
        }
    }

    @ImportStatic({JSConfig.class})
    public abstract static class JSNumberToPrecisionNode extends JSNumberOperation {
        private final BranchProfile precisionErrorBranch = BranchProfile.create();

        public JSNumberToPrecisionNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isJSNumber(thisNumber)", "isUndefined(precision)"})
        protected Object toPrecisionUndefined(JSDynamicObject thisNumber, Object precision,
                        @Shared("toString") @Cached("create()") JSToStringNode toStringNode) {
            return toStringNode.executeString(thisNumber); // ECMA 15.7.4.7 2
        }

        @Specialization(guards = {"isJSNumber(thisNumber)", "!isUndefined(precision)"})
        protected Object toPrecision(JSDynamicObject thisNumber, Object precision,
                        @Shared("toNumber") @Cached("create()") JSToNumberNode toNumberNode) {
            long lPrecision = JSRuntime.toInteger(toNumberNode.executeNumber(precision));
            double thisNumberVal = getDoubleValue(thisNumber);
            return toPrecisionIntl(thisNumberVal, lPrecision);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isJavaNumber(thisNumber)", "isUndefined(precision)"})
        protected Object toPrecisionPrimitiveUndefined(Object thisNumber, Object precision,
                        @Shared("toString") @Cached("create()") JSToStringNode toStringNode) {
            return toStringNode.executeString(thisNumber); // ECMA 15.7.4.7 2
        }

        @Specialization(guards = {"isJavaNumber(thisNumber)", "!isUndefined(precision)"})
        protected Object toPrecisionPrimitive(Object thisNumber, Object precision,
                        @Shared("toNumber") @Cached("create()") JSToNumberNode toNumberNode) {
            long lPrecision = JSRuntime.toInteger(toNumberNode.executeNumber(precision));
            double thisNumberVal = JSRuntime.doubleValue((Number) thisNumber);
            return toPrecisionIntl(thisNumberVal, lPrecision);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isForeignObject(thisNumber)", "isUndefined(precision)"}, limit = "InteropLibraryLimit")
        protected Object toPrecisionForeignObjectUndefined(Object thisNumber, Object precision,
                        @Cached("create()") JSToStringNode toStringNode,
                        @CachedLibrary("thisNumber") InteropLibrary interop) {
            return toStringNode.executeString(getDoubleValue(interop, thisNumber));
        }

        @Specialization(guards = {"isForeignObject(thisNumber)", "!isUndefined(precision)"}, limit = "InteropLibraryLimit")
        protected Object toPrecisionForeignObject(Object thisNumber, Object precision,
                        @Cached("create()") JSToNumberNode toNumberNode,
                        @CachedLibrary("thisNumber") InteropLibrary interop) {
            double thisNumberVal = getDoubleValue(interop, thisNumber);
            long lPrecision = JSRuntime.toInteger(toNumberNode.executeNumber(precision));
            return toPrecisionIntl(thisNumberVal, lPrecision);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isJSNumber(thisNumber)", "!isJavaNumber(thisNumber)", "!isForeignObject(thisNumber)"})
        protected Object toPrecisionOther(Object thisNumber, Object precision) {
            throw Errors.createTypeErrorNotANumber(thisNumber);
        }

        private Object toPrecisionIntl(double thisNumberVal, long lPrecision) {
            if (Double.isNaN(thisNumberVal)) {
                return Strings.NAN;
            } else if (Double.isInfinite(thisNumberVal)) {
                return (thisNumberVal < 0) ? Strings.NEGATIVE_INFINITY : Strings.INFINITY;
            }
            checkPrecision(lPrecision);
            return JSRuntime.formatDtoAPrecision(thisNumberVal, (int) lPrecision);
        }

        private void checkPrecision(long precision) {
            int maxPrecision = (getContext().getEcmaScriptVersion() >= JSConfig.ECMAScript2018) ? 100 : 20;
            if (precision < 1 || precision > maxPrecision) {
                precisionErrorBranch.enter();
                throw precisionRangeError(maxPrecision);
            }
        }

        @TruffleBoundary
        private static JSException precisionRangeError(int maxPrecision) {
            return Errors.createRangeError("toPrecision() argument must be between 1 and " + maxPrecision);
        }
    }
}
