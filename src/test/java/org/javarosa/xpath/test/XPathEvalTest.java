/*
 * Copyright 2019 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.javarosa.xpath.test;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.javarosa.test.utils.SystemHelper.withLocaleAndTimeZone;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_ADD;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_CHECK_TYPES;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_CONCAT;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_CONVERTIBLE;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_GET_CUSTOM;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_INCONVERTIBLE;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_NULL_PROTO;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_PROTO;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_RAW;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_REGEX;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_STATEFUL_READ;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_STATEFUL_WRITE;
import static org.javarosa.xpath.test.IFunctionHandlerHelpers.HANDLER_TESTFUNC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.xpath.XPathNodeset;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.XPathUnhandledException;
import org.javarosa.xpath.XPathUnsupportedException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class XPathEvalTest {
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    @Parameterized.Parameter(value = 0)
    public Locale locale;

    private EvaluationContext ec;

    @Parameterized.Parameters(name = "Locale {0}")
    public static Iterable<Object[]> testParametersProvider() {
        return Arrays.asList(new Object[][]{
            {Locale.forLanguageTag("en")},
            {Locale.forLanguageTag("pl")}
        });
    }

    @Before
    public void setUp() {
        ec = new EvaluationContext(null);
    }

    @Test
    public void counting_functions() {
        testEval("count(/data/path)", buildInstance(), null, 5.0);
        testEval("count-non-empty(/data/path)", buildInstance(), null, 3.0);
    }

    @Test
    public void unsupported_functions() {
        testEval("/union | /expr", new XPathUnsupportedException());
        testEval("/descendant::blah", new XPathUnsupportedException());
        testEval("/cant//support", new XPathUnsupportedException());
        testEval("/text()", new XPathUnsupportedException());
        testEval("/namespace:*", new XPathUnsupportedException());
        testEval("(filter-expr)[5]", buildInstance(), null, new XPathUnsupportedException());
        testEval("(filter-expr)/data", buildInstance(), null, new XPathUnsupportedException());
    }

    @Test
    public void numeric_literals() {
        testEval("5", 5.0);
        testEval("555555.555", 555555.555);
        testEval(".000555", 0.000555);
        testEval("0", 0.0);
        testEval("-5", -5.0);
        testEval("-0", -0.0);
        testEval("1230000000000000000000", 1.23e21);
        testEval("0.00000000000000000123", 1.23e-18);
    }

    @Test
    public void string_literals() {
        testEval("''", "");
        testEval("'\"'", "\"");
        testEval("\"test string\"", "test string");
        testEval("'   '", "   ");
    }

    @Test
    public void type_conversions() {
        ec.addFunctionHandler(HANDLER_CONVERTIBLE);
        ec.addFunctionHandler(HANDLER_INCONVERTIBLE);
        testEval("true()", TRUE);
        testEval("false()", FALSE);
        testEval("boolean(true())", TRUE);
        testEval("boolean(false())", FALSE);
        testEval("boolean(1)", TRUE);
        testEval("boolean(-1)", TRUE);
        testEval("boolean(0.0001)", TRUE);
        testEval("boolean(0)", FALSE);
        testEval("boolean(-0)", FALSE);
        testEval("boolean(number('NaN'))", FALSE);
        testEval("boolean(1 div 0)", TRUE);
        testEval("boolean(-1 div 0)", TRUE);
        testEval("boolean('')", FALSE);
        testEval("boolean('asdf')", TRUE);
        testEval("boolean('  ')", TRUE);
        testEval("boolean('false')", TRUE);
        testEval("boolean(date('2000-01-01'))", TRUE);
        testEval("boolean(convertible())", null, ec, TRUE);
        testEval("boolean(inconvertible())", null, ec, new XPathTypeMismatchException());
        testEval("number(true())", 1.0);
        testEval("number(false())", 0.0);
        testEval("number('100')", 100.0);
        testEval("number('100.001')", 100.001);
        testEval("number('.1001')", 0.1001);
        testEval("number('1230000000000000000000')", 1.23e21);
        testEval("number('0.00000000000000000123')", 1.23e-18);
        testEval("number('0')", 0.0);
        testEval("number('-0')", -0.0);
        testEval("number(' -12345.6789  ')", -12345.6789);
        testEval("number('NaN')", NaN);
        testEval("number('not a number')", NaN);
        testEval("number('- 17')", NaN);
        testEval("number('  ')", NaN);
        testEval("number('')", NaN);
        testEval("number('Infinity')", NaN);
        testEval("number('1.1e6')", NaN);
        testEval("number('34.56.7')", NaN);
        testEval("number(10)", 10.0);
        testEval("number(0)", 0.0);
        testEval("number(-0)", -0.0);
        testEval("number(-123.5)", -123.5);
        testEval("number(number('NaN'))", NaN);
        testEval("number(1 div 0)", POSITIVE_INFINITY);
        testEval("number(-1 div 0)", NEGATIVE_INFINITY);
        testEval("number(date('1970-01-01'))", 0.0);
        testEval("number(date('1970-01-02'))", 1.0);
        testEval("number(date('1969-12-31'))", -1.0);
        testEval("number(date('2008-09-05'))", 14127.0);
        testEval("number(date('1941-12-07'))", -10252.0);
        testEval("number(convertible())", null, ec, 5.0);
        testEval("number(inconvertible())", null, ec, new XPathTypeMismatchException());
        testEval("string(true())", "true");
        testEval("string(false())", "false");
        testEval("string(number('NaN'))", "NaN");
        testEval("string(1 div 0)", "Infinity");
        testEval("string(-1 div 0)", "-Infinity");
        testEval("string(0)", "0");
        testEval("string(-0)", "0");
        testEval("string(123456.0000)", "123456");
        testEval("string(-123456)", "-123456");
        testEval("string(1)", "1");
        testEval("string(-1)", "-1");
        testEval("string(.557586)", "0.557586");
        //broken testEval("string(1230000000000000000000)", "1230000000000000000000");
        //broken testEval("string(0.00000000000000000123)", "0.00000000000000000123");
        testEval("string('')", "");
        testEval("string('  ')", "  ");
        testEval("string('a string')", "a string");
        //For #637
        testEval("string(date('1989-11-09'))", "1989-11-09T00:00:00.000Z");
        testEval("string(convertible())", null, ec, "hi");
        testEval("string(inconvertible())", null, ec, new XPathTypeMismatchException());
        testEval("int('100')", 100.0);
        testEval("int('100.001')", 100.0);
        testEval("int('.1001')", 0.0);
        testEval("int('1230000000000000000000')", 1.23e21);
        testEval("int('0.00000000000000000123')", 1.23e-18);
        testEval("int('0')", 0.0);
        testEval("int('-0')", -0.0);
        testEval("int(' -12345.6789  ')", -12345.0);
        testEval("int('NaN')", NaN);
        testEval("int('not a number')", NaN);
        testEval("int('- 17')", NaN);
        testEval("int('  ')", NaN);
        testEval("int('')", NaN);
        testEval("int('Infinity')", NaN);
        testEval("int('1.1e6')", NaN);
        testEval("int('34.56.7')", NaN);
    }

    @Test
    public void substring_functions() {
        testEval("substr('hello',0)", "hello");
        testEval("substr('hello',0,5)", "hello");
        testEval("substr('hello',1)", "ello");
        testEval("substr('hello',1,5)", "ello");
        testEval("substr('hello',1,4)", "ell");
        testEval("substr('hello',-2)", "lo");
        testEval("substr('hello',0,-1)", "hell");
        testEval("substring-before('hello','l')", "he");
        testEval("substring-before('hello','q')", "");
        testEval("substring-before('hello','')", "");
        testEval("substring-before('','')", "");
        testEval("substring-before('','q')", "");
        testEval("substring-after('hello','l')", "lo");
        testEval("substring-after('hello','q')", "");
        testEval("substring-after('hello','')", "hello");
        testEval("substring-after('','')", "");
        testEval("substring-after('','q')", "");
        testEval("translate('hello','l','L')", "heLLo");
        testEval("translate('hello','q','Q')", "hello");
        testEval("translate('hello','','L')", "hello");
        testEval("translate('hello','l','')", "heo");
        testEval("translate('','l','L')", "");
        testEval("translate('hello','lo','LO')", "heLLO");
        testEval("translate('hello world','hello','')", " wrd");
        testEval("translate('hello wor,ld',' ,',', ')", "hello,wor ld");
        testEval("translate('2019/01/02','/','-')", "2019-01-02");
        testEval("contains('a', 'a')", true);
        testEval("contains('a', 'b')", false);
        testEval("contains('abc', 'b')", true);
        testEval("contains('abc', 'bcd')", false);
        testEval("not(contains('a', 'b'))", true);
        testEval("starts-with('abc', 'a')", true);
        testEval("starts-with('', 'a')", false);
        testEval("starts-with('', '')", true);
        testEval("ends-with('abc', 'a')", false);
        testEval("ends-with('abc', 'c')", true);
        testEval("ends-with('', '')", true);
    }

    @Test
    public void geo_functions() {
        testEval("geofence('')", new XPathUnhandledException());
        testEval("geofence('', '')", new XPathUnhandledException());
        testEval("geofence('0 0 0 0', '')", new XPathUnhandledException());
        testEval("geofence('0.5 0.5 0 0', /data/geoshape)", buildInstance(), null, true); // inside
        testEval("geofence('-1 0.5 0 0', /data/geoshape)", buildInstance(), null, false); // outside left
        testEval("geofence('2 0.5 0 0', /data/geoshape)", buildInstance(), null, false); // outside right
        testEval("geofence('0.5 2 0 0', /data/geoshape)", buildInstance(), null, false); // outside above
        testEval("geofence('0.5 -1 0 0', /data/geoshape)", buildInstance(), null, false); // outside below
        testEval("geofence('-1 0 0 0', /data/geoshape)", buildInstance(), null, false); // outside co-linear w/ bottom edge
        testEval("geofence('-1 1 0 0', /data/geoshape)", buildInstance(), null, false); // outside co-linear w/ top edge
        testEval("geofence('0 -1 0 0', /data/geoshape)", buildInstance(), null, false); // outside below vertex ("...They were carefully chosen to make the program work correctly when the point is vertically below a vertex.")
    }

    @Test
    public void other_string_functions() {
        testEval("normalize-space('')", "");
        testEval("normalize-space('  ')", "");
        testEval("normalize-space(' \t\n ')", "");
        testEval("normalize-space(' a')", "a");
        testEval("normalize-space(' a   ')", "a");
        testEval("normalize-space(' ab ')", "ab");
        testEval("normalize-space(' a    b ')", "a b");
        testEval("normalize-space(' a\nb\n')", "a b");
        testEval("normalize-space('\na\nb\n')", "a b");
        testEval("normalize-space('\nab')", "ab");
        testEval("normalize-space(' \ta\n\t  b \n\t c   \n')", "a b c");
        testEval("normalize-space()", new XPathException());
        testEval("string-length('cocotero')", 8.0);
        testEval("string-length()", new XPathException());
    }

    @Test
    public void other_string_functions_with_context() {
        FormInstance instance1 = buildInstance();
        testEval("/data/path[normalize-space()='some value']", instance1, null,
            createExpectedNodesetFromInstance(instance1, "path", 2));

        testEval("/data/path[string-length()=17]", instance1, null,
            createExpectedNodesetFromInstance(instance1, "path", 2));
    }

    @Test
    public void date_functions() {
        /*
        note: there are lots of time and timezone-like issues with dates that
        should be tested (particularly DST changes), but it's just too hard and
        client-dependent, so not doing it now.

        basically:
        dates cannot reliably be compared/used across time zones (an issue with the code)
        any time-of-day or DST should be ignored when comparing/using a date (an issue with testing)
        */
        withLocaleAndTimeZone(locale, GMT, () -> {
            ec.addFunctionHandler(HANDLER_CONVERTIBLE);
            testEval("date('2000-01-01')", DateUtils.getDate(2000, 1, 1));
            testEval("date('1945-04-26')", DateUtils.getDate(1945, 4, 26));
            testEval("date('1996-02-29')", DateUtils.getDate(1996, 2, 29));
            testEval("date('1983-09-31')", new XPathTypeMismatchException());
            testEval("date('not a date')", new XPathTypeMismatchException());
            testEval("date(0)", DateUtils.getDate(1970, 1, 1));
            testEval("date(6.5)", DateUtils.getDate(1970, 1, 7));
            testEval("date(1)", DateUtils.getDate(1970, 1, 2));
            testEval("date(-1)", DateUtils.getDate(1969, 12, 31));
            testEval("date(14127)", DateUtils.getDate(2008, 9, 5));
            testEval("date(-10252)", DateUtils.getDate(1941, 12, 7));
            testEval("date(date('1989-11-09'))", DateUtils.getDate(1989, 11, 9));
            testEval("date(true())", new XPathTypeMismatchException());
            testEval("date(convertible())", null, ec, new XPathTypeMismatchException());
            testEval("format-date('2018-01-02T10:20:30.123', \"%Y-%m-%e %H:%M:%S\")", "2018-01-2 10:20:30");
            testEval("date-time('2000-01-01T10:20:30.000')", DateUtils.getDateTimeFromString("2000-01-01T10:20:30.000"));
            testEval("decimal-date-time('2000-01-01T10:20:30.000')", 10957.430902777778);
            testEval("decimal-time('2000-01-01T10:20:30.000+03:00')", .30590277777810115);
            testEval("decimal-date-time('-1000')", new XPathTypeMismatchException());
            testEval("decimal-date-time('-01-2019')", new XPathTypeMismatchException());
        });
    }

    @Test
    public void boolean_functions() {
        testEval("not(true())", FALSE);
        testEval("not(false())", TRUE);
        testEval("not('')", TRUE);
        testEval("boolean-from-string('true')", TRUE);
        testEval("boolean-from-string('false')", FALSE);
        testEval("boolean-from-string('whatever')", FALSE);
        testEval("boolean-from-string('1')", TRUE);
        testEval("boolean-from-string('0')", FALSE);
        testEval("boolean-from-string(1)", TRUE);
        testEval("boolean-from-string(1.0)", TRUE);
        testEval("boolean-from-string(1.0001)", FALSE);
        testEval("boolean-from-string(true())", TRUE);
        testEval("if(true(), 5, 'abc')", 5.0);
        testEval("if(false(), 5, 'abc')", "abc");
        testEval("if(6 > 7, 5, 'abc')", "abc");
        testEval("if('', 5, 'abc')", "abc");
        testEval("selected('apple baby crimson', 'apple')", TRUE);
        testEval("selected('apple baby crimson', 'baby')", TRUE);
        testEval("selected('apple baby crimson', 'crimson')", TRUE);
        testEval("selected('apple baby crimson', '  baby  ')", TRUE);
        testEval("selected('apple baby crimson', 'babby')", FALSE);
        testEval("selected('apple baby crimson', 'bab')", FALSE);
        testEval("selected('apple', 'apple')", TRUE);
        testEval("selected('apple', 'ovoid')", FALSE);
        testEval("selected('', 'apple')", FALSE);
        testEval("count-selected('apple baby crimson')", 3.0);
        testEval("count-selected('')", 0.0);
        testEval("selected-at('apple baby crimson', 2)", "crimson");
        testEval("selected-at('apple baby', 2)", "");
        testEval("checklist(1, 3, 'foo', 'bar')", true);
        testEval("checklist(-1, 1, 'foo', 'bar')", false);
        testEval("checklist(3, -1, 'foo', 'bar')", false);
        testEval("checklist(3, 5, 'foo', 'bar')", false);
        testEval("checklist(1, 2, 'foo', 'bar', 'baz')", false);
    }

    @Test
    public void math_operators() {
        testEval("5.5 + 5.5", 11.0);
        testEval("0 + 0", 0.0);
        testEval("6.1 - 7.8", -1.7);
        testEval("-3 + 4", 1.0);
        testEval("3 + -4", -1.0);
        testEval("1 - 2 - 3", -4.0);
        testEval("1 - (2 - 3)", 2.0);
        testEval("-(8*5)", -40.0);
        testEval("-'19'", -19.0);
        testEval("1.1 * -1.1", -1.21);
        testEval("-10 div -4", 2.5);
        testEval("2 * 3 div 8 * 2", 1.5);
        testEval("3 + 3 * 3", 12.0);
        testEval("1 div 0", POSITIVE_INFINITY);
        testEval("-1 div 0", NEGATIVE_INFINITY);
        testEval("0 div 0", NaN);
        testEval("3.1 mod 3.1", 0.0);
        testEval("5 mod 3.1", 1.9);
        testEval("2 mod 3.1", 2.0);
        testEval("0 mod 3.1", 0.0);
        testEval("5 mod -3", 2.0);
        testEval("-5 mod 3", -2.0);
        testEval("-5 mod -3", -2.0);
        testEval("5 mod 0", NaN);
        testEval("5 * (6 + 7)", 65.0);
        testEval("'123' * '456'", 56088.0);
    }

    @Test
    public void math_functions() {
        testEval("abs(-3.5)", 3.5);
        // round, with a single argument
        testEval("round('14.29123456789')", 14.0);
        testEval("round('14.6')", 15.0);
        // round, with two arguments
        testEval("round('14.29123456789', 0)", 14.0);
        testEval("round('14.29123456789', 1)", 14.3);
        testEval("round('14.29123456789', 1.5)", 14.3);
        testEval("round('14.29123456789', 2)", 14.29);
        testEval("round('14.29123456789', 3)", 14.291);
        testEval("round('14.29123456789', 4)", 14.2912);
        testEval("round('12345.14', 1)", 12345.1);
        testEval("round('-12345.14', 1)", -12345.1);
        testEval("round('12345.12345', 0)", 12345.0);
        testEval("round('12345.12345', -1)", 12350.0);
        testEval("round('12345.12345', -2)", 12300.0);
        testEval("round('12350.12345', -2)", 12400.0);
        testEval("round('12345.12345', -3)", 12000.0);
        // round, with a comma instead of a decimal point
        testEval("round('4,6')", 5.0);
        // XPath specification tests
        testEval("round('1 div 0', 0)", NaN);
        testEval("round('14.5')", 15.0);
        testEval("round('NaN')", NaN);
        testEval("round('-NaN')", -NaN);
        testEval("round('0')", 0.0);
        testEval("round('-0')", -0.0);
        testEval("round('-0.5')", -0.0);
        // non US format
        testEval("round('14,6')", 15.0);
        // Java 8 tests deprecated by XPath 3.0 specification
        // See discussion at https://github.com/getodk/javarosa/pull/42#issuecomment-299527754
        testEval("round('12345.15', 1)", 12345.2);
        testEval("round('-12345.15', 1)", -12345.1);
        testEval("pow(2, 2)", 4.0);
        testEval("pow(2, 0)", 1.0);
        testEval("pow(0, 4)", 0.0);
        testEval("pow(2.5, 2)", 6.25);
        testEval("pow(0.5, 2)", .25);
        testEval("pow(-1, 2)", 1.0);
        testEval("pow(-1, 3)", -1.0);
        //So raising things to decimal powers is.... very hard
        //to evaluated exactly due to double floating point
        //precision. We'll try for things with clean answers
        testEval("pow(4, 0.5)", 2.0);
        testEval("pow(16, 0.25)", 2.0);
        testEval("cos(0)", 1.0);
        testEval("cos(" + (Math.PI / 2) + ")", 0.0);
        testEval("acos(0)", Math.PI / 2);
        testEval("acos(1)", 0.0);
        testEval("sin(0)", 0.0);
        testEval("sin(" + (Math.PI / 2) + ")", 1.0);
        testEval("asin(0)", 0.0);
        testEval("asin(1)", Math.PI / 2);
        testEval("tan(0)", 0.0);
        testEval("atan(0)", 0.0);
        testEval("atan2(0, 0)", 0.0);
        testEval("exp(1)", Math.E);
        testEval("exp10(2)", 100.0);
        testEval("log(" + Math.exp(2) + ")", 2.0);
        testEval("log10(100)", 2.0);
        testEval("pi()", Math.PI);
        testEval("sqrt(9)", 3.0);
    }

    @Test
    public void strange_operators() {
        testEval("true() + 8", 9.0);
        testEval("date('2008-09-08') - date('1983-10-06')", 9104.0);
        testEval("true() and true()", TRUE);
        testEval("true() and false()", FALSE);
        testEval("false() and false()", FALSE);
        testEval("true() or true()", TRUE);
        testEval("true() or false()", TRUE);
        testEval("false() or false()", FALSE);
        testEval("true() or true() and false()", TRUE);
        testEval("(true() or true()) and false()", FALSE);
        testEval("true() or date('')", TRUE); //short-circuiting
        testEval("false() and date('')", FALSE); //short-circuiting
        testEval("'' or 17", TRUE);
        testEval("false() or 0 + 2", TRUE);
        testEval("(false() or 0) + 2", 2.0);
        testEval("4 < 5", TRUE);
        testEval("5 < 5", FALSE);
        testEval("6 < 5", FALSE);
        testEval("4 <= 5", TRUE);
        testEval("5 <= 5", TRUE);
        testEval("6 <= 5", FALSE);
        testEval("4 > 5", FALSE);
        testEval("5 > 5", FALSE);
        testEval("6 > 5", TRUE);
        testEval("4 >= 5", FALSE);
        testEval("5 >= 5", TRUE);
        testEval("6 >= 5", TRUE);
        testEval("-3 > -6", TRUE);
    }

    @Test
    public void odd_comparisons() {
        testEval("true() > 0.9999", TRUE);
        testEval("'-17' > '-172'", TRUE); //no string comparison: converted to number
        testEval("'abc' < 'abcd'", FALSE); //no string comparison: converted to NaN
        testEval("date('2001-12-26') > date('2001-12-25')", TRUE);
        testEval("date('1969-07-20') < date('1969-07-21')", TRUE);
        testEval("false() and false() < true()", FALSE);
        testEval("(false() and false()) < true()", TRUE);
        testEval("6 < 7 - 4", FALSE);
        testEval("(6 < 7) - 4", -3.0);
        testEval("3 < 4 < 5", TRUE);
        testEval("3 < (4 < 5)", FALSE);
        testEval("true() = true()", TRUE);
        testEval("true() = false()", FALSE);
        testEval("true() != true()", FALSE);
        testEval("true() != false()", TRUE);
        testEval("3 = 3", TRUE);
        testEval("3 = 4", FALSE);
        testEval("3 != 3", FALSE);
        testEval("3 != 4", TRUE);
        testEval("6.1 - 7.8 = -1.7", TRUE); //handle floating point rounding
        testEval("'abc' = 'abc'", TRUE);
        testEval("'abc' = 'def'", FALSE);
        testEval("'abc' != 'abc'", FALSE);
        testEval("'abc' != 'def'", TRUE);
        testEval("'' = ''", TRUE);
        testEval("true() = 17", TRUE);
        testEval("0 = false()", TRUE);
        testEval("true() = 'true'", TRUE);
        testEval("17 = '17.0000000'", TRUE);
        testEval("'0017.' = 17", TRUE);
        testEval("'017.' = '17.000'", FALSE);
        testEval("date('2004-05-01') = date('2004-05-01')", TRUE);
        testEval("true() != date('1999-09-09')", FALSE);
        testEval("false() and true() != true()", FALSE);
        testEval("(false() and true()) != true()", TRUE);
        testEval("-3 < 3 = 6 >= 6", TRUE);
    }

    @Test
    public void functions_and_custom_function_handlers() {
        ec.addFunctionHandler(HANDLER_TESTFUNC);
        ec.addFunctionHandler(HANDLER_ADD);
        testEval("true(5)", new XPathUnhandledException());
        testEval("number()", new XPathUnhandledException());
        testEval("string('too', 'many', 'args')", new XPathUnhandledException());
        testEval("not-a-function()", new XPathUnhandledException());
        testEval("testfunc()", null, ec, TRUE);
        testEval("add(3, 5)", null, ec, 8.0);
        testEval("add('17', '-14')", null, ec, 3.0);
    }

    @Test
    public void proto() {
        ec.addFunctionHandler(HANDLER_INCONVERTIBLE);
        ec.addFunctionHandler(HANDLER_PROTO);
        ec.addFunctionHandler(HANDLER_NULL_PROTO);
        testEval("proto()", null, ec, new XPathTypeMismatchException());
        testEval("proto(5, 5)", null, ec, "[Double:5.0,Double:5.0]");
        testEval("proto(6)", null, ec, "[Double:6.0]");
        testEval("proto('asdf')", null, ec, "[Double:NaN]");
        testEval("proto('7', '7')", null, ec, "[Double:7.0,Double:7.0]"); //note: args treated as doubles because
        //(double, double) prototype takes precedence and strings are convertible to doubles
        testEval("proto(1.1, 'asdf', true())", null, ec, "[Double:1.1,String:asdf,Boolean:true]");
        testEval("proto(false(), false(), false())", null, ec, "[Double:0.0,String:false,Boolean:false]");
        testEval("proto(1.1, 'asdf', inconvertible())", null, ec, new XPathTypeMismatchException());
        testEval("proto(1.1, 'asdf', true(), 16)", null, ec, new XPathTypeMismatchException());
        testEval("null-proto()", null, ec, new NullPointerException());
    }

    @Test
    public void raw() {
        ec.addFunctionHandler(HANDLER_RAW);
        ec.addFunctionHandler(HANDLER_GET_CUSTOM);
        testEval("raw()", null, ec, "[]");
        testEval("raw(5, 5)", null, ec, "[Double:5.0,Double:5.0]");
        testEval("raw('7', '7')", null, ec, "[String:7,String:7]");
        testEval("raw('1.1', 'asdf', 17)", null, ec, "[Double:1.1,String:asdf,Boolean:true]"); //convertible to prototype
        testEval("raw(get-custom(false()), get-custom(true()))", null, ec, "[CustomType:,CustomSubType:]");
    }

    @Test
    public void concat() {
        ec.addFunctionHandler(HANDLER_CONCAT);
        ec.addFunctionHandler(HANDLER_CHECK_TYPES);
        ec.addFunctionHandler(HANDLER_GET_CUSTOM);
        testEval("concat()", null, ec, "");
        testEval("concat('a')", null, ec, "a");
        testEval("concat('a','b','')", null, ec, "ab");
        testEval("concat('ab','cde','','fgh',1,false(),'ijklmnop')", null, ec, "abcdefgh1falseijklmnop");
        testEval("check-types(55, '55', false(), '1999-09-09', get-custom(false()))", null, ec, TRUE);
        testEval("check-types(55, '55', false(), '1999-09-09', get-custom(true()))", null, ec, TRUE);
    }

    @Test
    public void regex() {
        ec.addFunctionHandler(HANDLER_REGEX);
        testEval("regex('12345','[0-9]+')", null, ec, TRUE);
    }

    @Test
    public void variable_refs() {
        ec.setVariable("var_float_five", 5.0f);
        testEval("$var_float_five", null, ec, 5.0);

        ec.setVariable("var_string_five", "five");
        testEval("$var_string_five", null, ec, "five");

        ec.setVariable("var_int_five", 5);
        testEval("$var_int_five", null, ec, 5.0);

        ec.setVariable("var_double_five", 5.0);
        testEval("$var_double_five", null, ec, 5.0);
    }

    @Test
    public void node_referencing() {
        // happy flow scenario where the index node is not blank
        FormInstance instance1 = createTestDataForIndexedRepeatFunction(1);
        testEval(
            "indexed-repeat( /data/repeat/name , /data/repeat , /data/index1 )",
            instance1,
            null,
            createExpectedNodesetFromIndexedRepeatFunction(instance1, 1, "name")
        );

        // situation where the referenced index node is blank and the default value (0 which means the first repeat group) is used
        FormInstance instance2 = createTestDataForIndexedRepeatFunction(null);
        testEval(
            "indexed-repeat( /data/repeat/name , /data/repeat , /data/index1 )",
            instance2,
            null,
            createExpectedNodesetFromIndexedRepeatFunction(instance2, 0, "name")
        );
    }

    @Test
    public void crypto_functions() {
        // Support for all 5 supported digest algorithms (required and optional) and default base64 encoding
        testEval("digest('some text', 'MD5', 'base64')", "VS4hzUzZkYZ448Gg30kbww==");
        testEval("digest('some text', 'SHA-1', 'base64')", "N6pjx3OY2VRHMmLhoAV8HmMu2nc=");
        testEval("digest('some text', 'SHA-256', 'base64')", "uU9vElx546X/qoJvWEwQ1SraZp5nYgUbgmtVd20FrtI=");
        testEval("digest('some text', 'SHA-384', 'base64')", "zJTsPphzwLmnJIZEKVj2cQZ833e5QnQW0DFEDMYgQeLuE0RJhEfsDO2fcENGG9Hz");
        testEval("digest('some text', 'SHA-512', 'base64')", "4nMrrtyj6sFAeChjfeHbynAsP8ns4Wz1Nt241hOc2F3+dGS4I1spgm9gjM9KxkPimxnGN4WKPYcQpZER30LdtQ==");
        // Support for hexadecimal encoding
        testEval("digest('some text', 'MD5', 'hex')", "552e21cd4cd9918678e3c1a0df491bc3");
        // Support for optional third argument (defaults to 'base64')
        testEval("digest('some text', 'MD5')", "VS4hzUzZkYZ448Gg30kbww==");
    }

    @Test
    public void read_write_function_handlers() {
        ec.addFunctionHandler(HANDLER_STATEFUL_READ);
        ec.addFunctionHandler(HANDLER_STATEFUL_WRITE);

        HANDLER_STATEFUL_READ.value = "testing-read";
        testEval("read()", null, ec, "testing-read");

        testEval("write('testing-write')", null, ec, TRUE);
        if (!"testing-write".equals(HANDLER_STATEFUL_WRITE.value))
            fail("Custom function handler did not successfully send data to external source");
    }

    @Test
    public void fallback_function_handler() {
        List<String> unknownFunctions = new ArrayList<>();
        ec.addFallbackFunctionHandler((name, args, ec) -> {
            unknownFunctions.add(name);
            return "";
        });
        testEval("foo(bar(33))", null, ec, "");
        assertThat(unknownFunctions, hasItem("foo"));
        assertThat(unknownFunctions, hasItem("bar"));
    }

    private void testEval(String expr, Object expected) {
        testEval(expr, null, null, expected);
    }

    private void testEval(String expr, FormInstance model, EvaluationContext ec, Object expected) {
        XPathExpression xpe = getXPathExpression(expr);
        ec = ec != null ? ec : new EvaluationContext(model);
        boolean exceptionExpected = (expected instanceof Throwable);

        try {
            Object result = xpe.eval(model, ec);
            if (exceptionExpected) {
                fail("We were expecting an exception when evaluating " + expr);
            } else if (expected instanceof Double) {
                assertEquals((Double) expected, (Double) result, 1e-12);
            } else if (expected instanceof XPathNodeset) {
                XPathNodeset expectedAsXPathNodeset = (XPathNodeset) expected;
                XPathNodeset resultAsXPathNodeset = (XPathNodeset) result;
                assertThat(resultAsXPathNodeset.size(), is(expectedAsXPathNodeset.size()));
                assertThat(resultAsXPathNodeset.getRefAt(0), is(expectedAsXPathNodeset.getRefAt(0)));
                assertThat(resultAsXPathNodeset.unpack(), is(expectedAsXPathNodeset.unpack()));
                assertThat(resultAsXPathNodeset.toArgList().length, is(expectedAsXPathNodeset.toArgList().length));
                for (int i = 0; i < expectedAsXPathNodeset.toArgList().length; i++)
                    assertThat(resultAsXPathNodeset.toArgList()[i], is(expectedAsXPathNodeset.toArgList()[i]));
            } else {
                assertThat(result, equalTo(expected));
            }
        } catch (Throwable t) {
            if (t instanceof AssertionError)
                throw t;
            if (!exceptionExpected)
                throw new RuntimeException("Unexpected exception when evaluating '" + expr + "'", t);
            if (t.getClass() != expected.getClass())
                fail("Did not get expected exception type when evaluating '" + expr + "'");
        }
    }

    private XPathExpression getXPathExpression(String expr) {
        try {
            return XPathParseTool.parseXPath(expr);
        } catch (XPathSyntaxException xpse) {
            throw new RuntimeException("Parsing '" + expr + "' failed", xpse);
        }
    }

    private XPathNodeset createExpectedNodesetFromInstance(FormInstance testInstance, String nodeName, int index) {
        TreeReference referencedNode = testInstance.getRoot().getChildrenWithName(nodeName).get(index).getRef();
        return new XPathNodeset(
            Collections.singletonList(referencedNode),
            testInstance,
            new EvaluationContext(new EvaluationContext(testInstance), referencedNode)
        );
    }

    private XPathNodeset createExpectedNodesetFromIndexedRepeatFunction(FormInstance testInstance, int repeatIndex, String nodeName) {
        TreeReference referencedNode = testInstance.getRoot().getChildAt(repeatIndex).getChildrenWithName(nodeName).get(0).getRef();
        return new XPathNodeset(
            Collections.singletonList(referencedNode),
            testInstance,
            new EvaluationContext(new EvaluationContext(testInstance), referencedNode)
        );
    }

    private FormInstance createTestDataForIndexedRepeatFunction(Integer indexNodeValue) {
        TreeElement root = new TreeElement("data");
        TreeElement repeat = new TreeElement("repeat");
        TreeElement repeatChild = new TreeElement("name");
        repeatChild.setAnswer(new StringData("A"));
        repeat.addChild(repeatChild);
        root.addChild(repeat);

        repeat = new TreeElement("repeat");
        repeatChild = new TreeElement("name");
        repeatChild.setAnswer(new StringData("B"));
        repeat.addChild(repeatChild);
        root.addChild(repeat);

        repeat = new TreeElement("repeat");
        repeatChild = new TreeElement("name");
        repeatChild.setAnswer(new StringData("C"));
        repeat.addChild(repeatChild);
        root.addChild(repeat);

        TreeElement index = new TreeElement("index1");
        if (indexNodeValue != null) {
            index.setValue(new IntegerData(1));
        }
        return new FormInstance(root);
    }

    private static FormInstance buildInstance() {
        TreeElement data = new TreeElement("data");

        TreeElement path = new TreeElement("path", 0);
        path.addChild(new TreeElement("child", 0));
        path.addChild(new TreeElement("child", 1));
        data.addChild(path);

        data.addChild(new TreeElement("path", 1));

        path = new TreeElement("path", 2);
        path.setValue(new StringData("    some    value"));
        data.addChild(path);

        path = new TreeElement("path", 3);
        path.addChild(new TreeElement("child", 0));
        data.addChild(path);

        data.addChild(new TreeElement("path", 4));

        path = new TreeElement("geoshape", 0);
        path.setValue(new StringData("0 0 0 0;0 1 0 0;1 1 0 0;1 0 0 0;0 0 0 0"));
        data.addChild(path);

        return new FormInstance(data);
    }

}


