/*
 * Copyright (C) 2009 JavaRosa
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.javarosa.xpath.expr;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFallbackFunctionHandler;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.util.Base64;
import org.javarosa.core.util.Ed25519;
import org.javarosa.core.util.GeoUtils;
import org.javarosa.core.util.MathUtils;
import org.javarosa.core.util.PropertyUtils;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapListPoly;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xpath.IExprDataType;
import org.javarosa.xpath.XPathArityException;
import org.javarosa.xpath.XPathNodeset;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.javarosa.xpath.XPathUnhandledException;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.Double.NaN;
import static org.javarosa.xform.parse.RandomizeHelper.toNumericWithLongHash;

/**
 * Representation of an xpath function expression.
 * <p>
 * All of the built-in xpath functions are included here, as well as the xpath type conversion logic
 * <p>
 * Evaluation of functions can delegate out to custom function handlers that must be registered at
 * runtime.
 *
 * @author Drew Roos
 */
public class XPathFuncExpr extends XPathExpression {
    public XPathQName id;            //name of the function
    public XPathExpression[] args;    //argument list

    public XPathFuncExpr() {
    } //for deserialization

    public XPathFuncExpr(XPathQName id, XPathExpression[] args) {
        this.id = id;
        this.args = args;

        if (id.name.equals("instance") && args[0] instanceof XPathStringLiteral) {
            XFormParser.recordInstanceFunctionCall(((XPathStringLiteral) args[0]).s);
        }
    }

    public XPathFuncExpr(XPathQName id) {
        this(id, new XPathExpression[] {});
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("{func-expr:");
        sb.append(id.toString());
        sb.append(",{");
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i].toString());
            if (i < args.length - 1)
                sb.append(",");
        }
        sb.append("}}");

        return sb.toString();
    }

    public boolean equals(Object o) {
        if (o instanceof XPathFuncExpr) {
            XPathFuncExpr x = (XPathFuncExpr) o;

            //Shortcuts for very easily comparable values
            //We also only return "True" for methods we expect to return the same thing. This is not good
            //practice in Java, since o.equals(o) will return false. We should evaluate that differently.
            //Dec 8, 2011 - Added "uuid", since we should never assume one uuid equals another
            //May 6, 2013 - Added "random", since two calls asking for a random
            //Jun 4, 2013 - Added "now" and "today", since these could change during the course of a survey
            if (!id.equals(x.id) || args.length != x.args.length ||
                id.toString().equals("uuid") ||
                id.toString().equals("random") ||
                id.toString().equals("once") ||
                id.toString().equals("now") ||
                id.toString().equals("today")) {
                return false;
            }

            return ExtUtil.arrayEquals(args, x.args);
        } else {
            return false;
        }
    }

    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        id = (XPathQName) ExtUtil.read(in, XPathQName.class);
        List<Object> v = (List<Object>) ExtUtil.read(in, new ExtWrapListPoly(), pf);

        args = new XPathExpression[v.size()];
        for (int i = 0; i < args.length; i++)
            args[i] = (XPathExpression) v.get(i);
    }

    public void writeExternal(DataOutputStream out) throws IOException {
        List<XPathExpression> v = Arrays.asList(args);

        ExtUtil.write(out, id);
        ExtUtil.write(out, new ExtWrapListPoly(v));
    }

    /**
     * Evaluate the function call.
     * <p>
     * First check if the function is a member of the built-in function suite. If not, then check
     * for any custom handlers registered to handler the function. If not, throw and exception.
     * <p>
     * Both function name and appropriate arguments are taken into account when finding a suitable
     * handler. For built-in functions, the number of arguments must match; for custom functions,
     * the supplied arguments must match one of the function prototypes defined by the handler.
     */
    public Object eval(DataInstance model, EvaluationContext evalContext) {
        String name = id.toString();
        Object[] argVals = new Object[args.length];

        HashMap<String, IFunctionHandler> funcHandlers = evalContext.getFunctionHandlers();

        //TODO: Func handlers should be able to declare the desire for short circuiting as well
        if (name.equals("if")) {
            assertArgsCount(name, args, 3);
            return ifThenElse(model, evalContext, args, argVals);
        } else if (name.equals("coalesce")) {
            assertArgsCount(name, args, 2);
            argVals[0] = args[0].eval(model, evalContext);
            if (!isNull(argVals[0])) {
                return argVals[0];
            } else {
                // that was null, so try the other one...
                argVals[1] = args[1].eval(model, evalContext);
                return argVals[1];
            }
        } else if (name.equals("indexed-repeat")) {
            if ((args.length == 3 || args.length == 5 || args.length == 7 || args.length == 9 || args.length == 11)) {
                return indexedRepeat(model, evalContext, args, argVals);
            } else {
                throw new XPathUnhandledException("function \'" + name + "\' requires " +
                    "3, 5, 7, 9 or 11 arguments. Only " + args.length + " provided.");
            }
        }

        for (int i = 0; i < args.length; i++) {
            argVals[i] = args[i].eval(model, evalContext);
        }

        //check built-in functions
        if (name.equals("true")) {
            assertArgsCount(name, args, 0);
            return Boolean.TRUE;
        } else if (name.equals("false")) {
            assertArgsCount(name, args, 0);
            return Boolean.FALSE;
        } else if (name.equals("boolean")) {
            assertArgsCount(name, args, 1);
            return toBoolean(argVals[0]);
        } else if (name.equals("number")) {
            assertArgsCount(name, args, 1);
            return toNumeric(argVals[0]);
        } else if (name.equals("int")) { //non-standard
            assertArgsCount(name, args, 1);
            return toInt(argVals[0]);
        } else if (name.equals("round")) { // Proximate XPath 3.0 and Excel-style round(value,decimal place)
            final int places;
            if (args.length == 1) {
                places = 0;
            } else {
                assertArgsCount(name, args, 2);
                places = toNumeric(argVals[1]).intValue();
            }
            return round(toNumeric(argVals[0]), places);
        } else if (name.equals("string")) {
            assertArgsCount(name, args, 1);
            return toString(argVals[0]);
        } else if (name.equals("date")) { //non-standard
            assertArgsCount(name, args, 1);
            return toDate(argVals[0], false);
        } else if (name.equals("date-time")) { //non-standard -- convert double/int/string to Date object
            assertArgsCount(name, args, 1);
            return toDate(argVals[0], true);
        } else if (name.equals("decimal-date-time")) { //non-standard -- convert string/date to decimal days off 1970-01-01T00:00:00.000-000
            assertArgsCount(name, args, 1);
            return toDecimalDateTime(argVals[0], true);
        } else if (name.equals("decimal-time")) { //non-standard -- convert string/date to decimal days off 1970-01-01T00:00:00.000-000
            assertArgsCount(name, args, 1);
            return toDecimalDateTime(argVals[0], false);
        } else if (name.equals("not")) {
            assertArgsCount(name, args, 1);
            return boolNot(argVals[0]);
        } else if (name.equals("boolean-from-string")) {
            assertArgsCount(name, args, 1);
            return boolStr(argVals[0]);
        } else if (name.equals("format-date")) {
            assertArgsCount(name, args, 2);
            return formatDateTime(argVals[0], argVals[1]);
        } else if (name.equals("abs")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.abs(toDouble(argVals[0]));
        } else if (name.equals("acos")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.acos(toDouble(argVals[0]));
        } else if (name.equals("asin")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.asin(toDouble(argVals[0]));
        } else if (name.equals("atan")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.atan(toDouble(argVals[0]));
        } else if (name.equals("atan2")) { //XPath 3.0
            checkArity(name, 2, args.length);
            return Math.atan2(toDouble(argVals[0]), toDouble(argVals[1]));
        } else if (name.equals("cos")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.cos(toDouble(argVals[0]));
        } else if (name.equals("exp")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.exp(toDouble(argVals[0]));
        } else if (name.equals("exp10")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.pow(10.0, toDouble(argVals[0]));
        } else if (name.equals("log")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.log(toDouble(argVals[0]));
        } else if (name.equals("log10")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.log10(toDouble(argVals[0]));
        } else if (name.equals("pi")) { //XPath 3.0
            checkArity(name, 0, args.length);
            return Math.PI;
        } else if (name.equals("sin")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.sin(toDouble(argVals[0]));
        } else if (name.equals("sqrt")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.sqrt(toDouble(argVals[0]));
        } else if (name.equals("tan")) { //XPath 3.0
            checkArity(name, 1, args.length);
            return Math.tan(toDouble(argVals[0]));
        } else if (name.equals("format-date-time")) { // non-standard
            assertArgsCount(name, args, 2);
            return formatDateTime(argVals[0], argVals[1]);
        } else if ((name.equals("selected") || name.equals("is-selected"))) { //non-standard
            assertArgsCount(name, args, 2);
            return multiSelected(argVals[0], argVals[1], name);
        } else if (name.equals("count-selected")) { //non-standard
            assertArgsCount(name, args, 1);
            return countSelected(argVals[0]);
        } else if (name.equals("selected-at")) { //non-standard
            assertArgsCount(name, args, 2);
            return selectedAt(argVals[0], argVals[1]);
        } else if (name.equals("position")) {
            //TODO: Technically, only the 0 length argument is valid here.
            if (args.length == 1) {
                XPathNodeset nodes = (XPathNodeset) argVals[0];
                if (nodes.size() == 0) {
                    // Added to prevent an exception within ODK Validate.
                    // Will likely cause an error downstream when used in an XPath.
                    return (double) (1 + TreeReference.INDEX_UNBOUND);
                } else {
                    // This is weird -- we are returning the position of the first
                    // Nodeset element but there may be a list of elements. Unclear
                    // if or how this might manifest into a bug... .
                    return position(nodes.getRefAt(0));
                }
            } else if (args.length == 0) {
                if (evalContext.getContextPosition() != -1) {
                    return (double) (1 + evalContext.getContextPosition());
                }
                return position(evalContext.getContextRef());
            } else {
                throw new XPathUnhandledException("function \'" + name +
                    "\' requires either exactly one argument or no arguments. Only " + args.length + " provided.");
            }
        } else if (name.equals("count")) {
            assertArgsCount(name, args, 1);
            return count(argVals[0]);
        } else if (name.equals("count-non-empty")) {
            assertArgsCount(name, args, 1);
            return countNonEmpty(argVals[0]);
        } else if (name.equals("sum")) {
            assertArgsCount(name, args, 1);
            if (argVals[0] instanceof XPathNodeset) {
                return sum(((XPathNodeset) argVals[0]).toArgList());
            } else {
                throw new XPathTypeMismatchException("not a nodeset");
            }
        } else if (name.equals("max")) {
            if (args.length == 1 && argVals[0] instanceof XPathNodeset) {
                return max(((XPathNodeset) argVals[0]).toArgList());
            } else {
                return max(argVals);
            }
        } else if (name.equals("min")) {
            if (args.length == 1 && argVals[0] instanceof XPathNodeset) {
                return min(((XPathNodeset) argVals[0]).toArgList());
            } else {
                return min(argVals);
            }
        } else if (name.equals("today")) {
            assertArgsCount(name, args, 0);
            return DateUtils.roundDate(new Date());
        } else if (name.equals("now")) {
            assertArgsCount(name, args, 0);
            return new DateTime().toDate();
        } else if (name.equals("concat")) {
            if (args.length == 1 && argVals[0] instanceof XPathNodeset) {
                return join("", ((XPathNodeset) argVals[0]).toArgList());
            } else {
                return join("", argVals);
            }
        } else if (name.equals("join") && args.length >= 1) {
            if (args.length == 2 && argVals[1] instanceof XPathNodeset) {
                return join(argVals[0], ((XPathNodeset) argVals[1]).toArgList());
            } else {
                return join(argVals[0], subsetArgList(argVals, 1));
            }
        } else if (name.equals("substr") && (args.length == 2 || args.length == 3)) {
            return substring(argVals[0], argVals[1], args.length == 3 ? argVals[2] : null);
        } else if (name.equals("substring-before") && args.length == 2) {
            String str = toString(argVals[0]);
            String substr = toString(argVals[1]);
            int pos = str.indexOf(substr);
            // XPath reference states that we should return the empty string when we don't find the substring
            return pos >= 0 ? str.substring(0, pos) : "";
        } else if (name.equals("substring-after") && args.length == 2) {
            String str = toString(argVals[0]);
            String substr = toString(argVals[1]);
            int pos = str.indexOf(substr);
            // XPath reference states that we should return the empty string when we don't find the substring
            return pos >= 0 ? str.substring(pos + substr.length()) : "";
        } else if (name.equals("translate") && args.length == 3) {
            String str = toString(argVals[0]);
            String fromChars = toString(argVals[1]);
            String toChars = toString(argVals[2]);
            int toNumChars = toChars.length();
            StringBuilder result = new StringBuilder();
            // iterate thru each char in the original string
            for (int i = 0; i < str.length(); i++) {
                char from = str.charAt(i);
                // determine if there is a mapping for this char
                int fromPos = fromChars.indexOf(from);
                if (fromPos == -1) {
                    // no mapping for this char, so just add it to result unchanged
                    result.append(from);
                } else if (fromPos < toNumChars) {
                    // replace with the corresponding char it is mapped to
                    result.append(toChars.charAt(fromPos));
                }
                // else the char is mapped to nothing, so per XPath definition we 'delete' it from the string by simply not appending it
            }
            return result.toString();
        } else if (name.equals("contains") && args.length == 2) {
            return toString(argVals[0]).contains(toString(argVals[1]));
        } else if (name.equals("starts-with") && args.length == 2) {
            return toString(argVals[0]).startsWith(toString(argVals[1]));
        } else if (name.equals("ends-with") && args.length == 2) {
            return toString(argVals[0]).endsWith(toString(argVals[1]));
        } else if (name.equals("string-length") && args.length <= 1) {
            Object arg;
            if (args.length == 1) {
                arg = argVals[0];
            } else {
                arg = (XPathPathExpr.fromRef(evalContext.getContextRef())).eval(model, evalContext).unpack();
            }
            return stringLength(arg);
        } else if (name.equals("normalize-space") && args.length <= 1) {
            Object arg;
            if (args.length == 1) {
                arg = argVals[0];
            } else {
                arg = (XPathPathExpr.fromRef(evalContext.getContextRef())).eval(model, evalContext).unpack();
            }
            return normalizeSpace(arg);
        } else if (name.equals("checklist") && args.length >= 2) { //non-standard
            if (args.length == 3 && argVals[2] instanceof XPathNodeset) {
                return checklist(argVals[0], argVals[1], ((XPathNodeset) argVals[2]).toArgList());
            } else {
                return checklist(argVals[0], argVals[1], subsetArgList(argVals, 2));
            }
        } else if (name.equals("weighted-checklist") && args.length >= 2 && args.length % 2 == 0) { //non-standard
            if (args.length == 4 && argVals[2] instanceof XPathNodeset && argVals[3] instanceof XPathNodeset) {
                Object[] factors = ((XPathNodeset) argVals[2]).toArgList();
                Object[] weights = ((XPathNodeset) argVals[3]).toArgList();
                if (factors.length != weights.length) {
                    throw new XPathTypeMismatchException("weighted-checklist: nodesets not same length");
                }
                return checklistWeighted(argVals[0], argVals[1], factors, weights);
            } else {
                return checklistWeighted(argVals[0], argVals[1], subsetArgList(argVals, 2, 2), subsetArgList(argVals, 3, 2));
            }
        } else if (name.equals("regex")) { //non-standard
            assertArgsCount(name, args, 2);
            return regex(argVals[0], argVals[1]);
        } else if (name.equals("depend") && args.length >= 1) { //non-standard
            return argVals[0];
        } else if (name.equals("random")) { //non-standard
            assertArgsCount(name, args, 0);
            //calculated expressions may be recomputed w/o warning! use with caution!!
            return MathUtils.getRand().nextDouble();
        } else if (name.equals("once")) {
            assertArgsCount(name, args, 1);
            XPathPathExpr currentFieldPathExpr = XPathPathExpr.fromRef(evalContext.getContextRef());
            Object currValue = currentFieldPathExpr.eval(model, evalContext).unpack();
            if (currValue == null || toString(currValue).length() == 0) {
                // this is the "once" case
                return argVals[0];
            } else {
                return currValue;
            }
        } else if (name.equals("uuid") && (args.length == 0 || args.length == 1)) { //non-standard
            //calculated expressions may be recomputed w/o warning! use with caution!!
            if (args.length == 0) {
                return PropertyUtils.genUUID();
            }

            int len = toInt(argVals[0]).intValue();
            return PropertyUtils.genGUID(len);
        } else if (name.equals("version")) { //non-standard
            assertArgsCount(name, args, 0);
            final String formVersion = (model instanceof FormInstance) ? ((FormInstance) model).formVersion : "";
            return formVersion == null ? "" : formVersion;
        } else if (name.equals("property")) { // non-standard
            // return a property defined by the property manager.
            // NOTE: Property should be immutable.
            // i.e., does not work with 'start' or 'end' property.
            assertArgsCount(name, args, 1);
            String s = toString(argVals[0]);
            return PropertyManager.__().getSingularProperty(s);
        } else if (name.equals("pow") && (args.length == 2)) { //XPath 3.0
            double a = toDouble(argVals[0]);
            double b = toDouble(argVals[1]);
            return Math.pow(a, b);
        } else if (name.equals("enclosed-area") || name.equals("area")) {
            assertArgsCount(name, args, 1);
            List<GeoUtils.LatLong> latLongs = new XPathFuncExprGeo().getGpsCoordinatesFromNodeset(name, argVals[0]);
            return GeoUtils.calculateAreaOfGPSPolygonOnEarthInSquareMeters(latLongs);
        } else if (name.equals("distance")) {
            if (args.length == 1) {
                if (argVals[0] instanceof XPathNodeset) {
                    List<GeoUtils.LatLong> latLongs = new XPathFuncExprGeo().getGpsCoordinatesFromNodeset(name, argVals[0]);
                    return GeoUtils.calculateDistance(latLongs);
                } else if (argVals[0] instanceof String) {
                    List<GeoUtils.LatLong> latLongs = new XPathFuncExprGeo().geopointsToLatLongs(name, ((String) argVals[0]).split(";"));
                    return GeoUtils.calculateDistance(latLongs);
                } else {
                    throw new XPathUnhandledException("function 'distance' requires a field or text as the parameter.");
                }
            } else if (args.length > 1) {
                return GeoUtils.calculateDistance(new XPathFuncExprGeo().geopointsToLatLongs(name, argVals));
            } else {
                throw new XPathUnhandledException("function 'distance' requires at least one parameter.");
            }
        } else if (name.equals("geofence")) {
            assertArgsCount(name, args, 2);
            GeoPointData geoPointData = new GeoPointData().cast(new UncastData(XPathFuncExpr.toString(argVals[0])));
            GeoUtils.LatLong point = new GeoUtils.LatLong(geoPointData.getPart(0), geoPointData.getPart(1));
            List<GeoUtils.LatLong> latLongs = new XPathFuncExprGeo().getGpsCoordinatesFromNodeset(name, argVals[1]);
            return GeoUtils.calculateIsPointInGPSPolygon(point, latLongs);
         } else if (name.equals("digest") && (args.length == 2 || args.length == 3)) {
            return DigestAlgorithm.from(toString(argVals[1])).digest(
                toString(argVals[0]),
                args.length == 3 ? Encoding.from(toString(argVals[2])) : Encoding.BASE64
            );
        } else if (name.equals("randomize")) {
            if (!(argVals[0] instanceof XPathNodeset))
                throw new XPathTypeMismatchException("First argument to randomize must be a nodeset");

            if (args.length == 1)
                return XPathNodeset.shuffle((XPathNodeset) argVals[0]);

            if (args.length == 2)
                return XPathNodeset.shuffle((XPathNodeset) argVals[0], toNumericWithLongHash(argVals[1]));

            throw new XPathUnhandledException("function 'randomize' requires 1 or 2 arguments. " + args.length + " provided.");
        } else if (name.equals("base64-decode")) {
            assertArgsCount(name, args, 1);
            return base64Decode(argVals[0]);
        } else if (name.equals("extract-signed")) {
            assertArgsCount(name, args, 2);
            return extractSigned(argVals[0], argVals[1]);
        } else {
            //check for custom handler
            IFunctionHandler handler = funcHandlers.get(name);
            if (handler != null)
                return evalCustomFunction(handler, argVals, evalContext);

            // check for fallback handler
            IFallbackFunctionHandler fallbackHandler = evalContext.getFallbackFunctionHandler();
            if (fallbackHandler != null)
                return evalCustomFunction(fallbackHandler, name, argVals, evalContext);

            throw new XPathUnhandledException("function \'" + name + "\'");
        }
    }

    private static void assertArgsCount(String name, Object[] args, int count) {
        if (args.length != count) {
            throw new XPathUnhandledException("function '" + name + "'. Requires " +
                count + " arguments but " + args.length + " provided.");
        }
    }

    /**
     * Given a handler registered to handle the function, try to coerce the function arguments into
     * one of the prototypes defined by the handler. If no suitable prototype found, throw an eval
     * exception. Otherwise, evaluate.
     * <p>
     * Note that if the handler supports 'raw args', it will receive the full, unaltered argument
     * list if no prototype matches. (this lets functions support variable-length argument lists)
     */
    private static Object evalCustomFunction(IFunctionHandler handler, Object[] args, EvaluationContext ec) {
        List<Class[]> prototypes = handler.getPrototypes();
        Object[] typedArgs = null;

        int i = 0;
        while (typedArgs == null && prototypes.size() > i) {
            typedArgs = matchPrototype(args, prototypes.get(i++));
        }

        if (typedArgs != null) {
            return handler.eval(typedArgs, ec);
        } else if (handler.rawArgs()) {
            return handler.eval(args, ec);  //should we have support for expanding nodesets here?
        } else {
            throw new XPathTypeMismatchException("for function \'" + handler.getName() + "\'");
        }
    }

    private static Object evalCustomFunction(IFallbackFunctionHandler handler, String name, Object[] args, EvaluationContext ec) {
        return handler.eval(name, args, ec);
    }

    /**
     * Given a prototype defined by the function handler, attempt to coerce the function arguments
     * to match that prototype (checking # args, type conversion, etc.). If it is coercible, return
     * the type-converted argument list -- these will be the arguments used to evaluate the function.
     * If not coercible, return null.
     */
    private static Object[] matchPrototype(Object[] args, Class[] prototype) {
        Object[] typed = null;

        if (prototype.length == args.length) {
            typed = new Object[args.length];

            for (int i = 0; i < prototype.length; i++) {
                typed[i] = null;

                //how to handle type conversions of custom types?
                if (prototype[i].isAssignableFrom(args[i].getClass())) {
                    typed[i] = args[i];
                } else {
                    try {
                        if (prototype[i] == Boolean.class) {
                            typed[i] = toBoolean(args[i]);
                        } else if (prototype[i] == Double.class) {
                            typed[i] = toNumeric(args[i]);
                        } else if (prototype[i] == String.class) {
                            typed[i] = toString(args[i]);
                        } else if (prototype[i] == Date.class) {
                            typed[i] = toDate(args[i], false);
                        }
                    } catch (XPathTypeMismatchException xptme) { /* swallow type mismatch exception */ }
                }

                if (typed[i] == null)
                    return null;
            }
        }

        return typed;
    }

    /******** HANDLERS FOR BUILT-IN FUNCTIONS ********
     *
     * the functions below are the handlers for the built-in xpath function suite
     *
     * if you add a function to the suite, it should adhere to the following pattern:
     *
     *   * the function takes in its arguments as objects (DO NOT cast the arguments when calling
     *     the handler up in eval() (i.e., return stringLength((String)argVals[0])  <--- NO!)
     *
     *   * the function converts the generic argument(s) to the desired type using the built-in
     *     xpath type conversion functions (toBoolean(), toNumeric(), toString(), toDate())
     *
     *   * the function MUST return an object of type Boolean, Double, String, or Date; it may
     *     never return null (instead return the empty string or NaN)
     *
     *   * the function may throw exceptions, but should try as hard as possible not to, and if
     *     it must, strive to make it an XPathException
     *
     */

    public static boolean isNull(Object o) {
        if (o == null) {
            return true; //true 'null' values aren't allowed in the xpath engine, but whatever
        }

        o = unpack(o);
        if (o instanceof String && ((String) o).length() == 0) {
            return true;
        } else if (o instanceof Double && ((Double) o).isNaN()) {
            return true;
        } else {
            return false;
        }
    }

    public static Double stringLength(Object o) {
        String s = toString(o);
        if (s == null) {
            return 0.0;
        }
        return (double) s.length();
    }

    public static String normalizeSpace(Object o) {
        String s = toString(o);
        String normalized = s.trim().replaceAll("\\s+", " ");
        return normalized;
    }

    /**
     * convert a value to a boolean using xpath's type conversion rules
     */
    public static Boolean toBoolean(Object o) {
        Boolean val = null;

        o = unpack(o);

        if (o instanceof Boolean) {
            val = (Boolean) o;
        } else if (o instanceof Double) {
            double d = (Double) o;
            val = Math.abs(d) > 1.0e-12 && !Double.isNaN(d);
        } else if (o instanceof String) {
            String s = (String) o;
            val = s.length() > 0;
        } else if (o instanceof Date) {
            val = Boolean.TRUE;
        } else if (o instanceof IExprDataType) {
            val = ((IExprDataType) o).toBoolean();
        }

        if (val != null) {
            return val;
        } else {
            throw new XPathTypeMismatchException("converting to boolean");
        }
    }

    public static Double toDouble(Object o) {
        if (o instanceof Date) {
            return DateUtils.fractionalDaysSinceEpoch((Date) o);
        } else {
            return toNumeric(o);
        }

    }

    /**
     * Convert a non-zero-length string value to an integer by:
     * - encoding it as utf-8
     * - hashing it with sha256 (available cross-platform, including via browser crypto API)
     * - interpreting the first 8 bytes of the hash as a long
     *
     * A zero-length string results in a 0L — this is the classic behaviour that we want
     * to conserve for backward compatibility with a case that is expected to be common.
     * In practical terms, we don't want the sort order of choice lists using an empty string as
     * a seed to change with the introduction of this seed derivation mechanism.
     */
    public static long toLongHash(String sourceString) {
        if (sourceString.length() == 0) return 0L;
        byte[] hasheeBuf = sourceString.getBytes(Charset.forName("UTF-8"));
        SHA256Digest hasher = new SHA256Digest();
        hasher.update(hasheeBuf, 0, hasheeBuf.length);
        byte[] digestBuf = new byte[32];
        hasher.doFinal(digestBuf, 0);
        return (ByteBuffer.wrap(digestBuf)).getLong(0);
    }

    public static long toLongHash(Object o) {
        String sourceString = (String) unpack(o);
        return toLongHash(sourceString);
    }

    /**
     * convert a value to a number using xpath's type conversion rules (note that xpath itself makes
     * no distinction between integer and floating point numbers)
     */
    public static Double toNumeric(Object o) {
        Double val = null;

        o = unpack(o);

        if (o instanceof Boolean) {
            val = (double) ((Boolean) o ? 1 : 0);
        } else if (o instanceof Double) {
            val = (Double) o;
        } else if (o instanceof String) {
            /* annoying, but the xpath spec doesn't recognize scientific notation, or +/-Infinity
             * when converting a string to a number
             */

            final String s = ((String) o)
                .replace(',', '.') // Some locales use ',' instead of '.'
                .trim();

            try {
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c != '-' && c != '.' && (c < '0' || c > '9'))
                        throw new NumberFormatException();
                }

                val = Double.parseDouble(s);
            } catch (NumberFormatException nfe) {
                val = NaN;
            }
        } else if (o instanceof Date) {
            val = (double) DateUtils.fractionalDaysSinceEpoch((Date) o);
        } else if (o instanceof IExprDataType) {
            val = ((IExprDataType) o).toNumeric();
        }

        if (val != null) {
            return val;
        }

        throw new XPathTypeMismatchException("converting to numeric");
    }

    /**
     * convert a number to an integer by truncating the fractional part. if non-numeric, coerce the
     * value to a number first. note that the resulting return value is still a Double, as required
     * by the xpath engine
     */
    public static Double toInt(Object o) {
        Double val = toNumeric(o);

        if (val.isInfinite() || val.isNaN()) {
            return val;
        } else if (val >= Long.MAX_VALUE || val <= Long.MIN_VALUE) {
            return val;
        } else {
            long l = val.longValue();
            Double dbl = (double) l;
            if (l == 0 && (val < 0. || val.equals(-0.))) {
                dbl = -0.;
            }
            return dbl;
        }
    }

    /**
     * convert a value to a string using xpath's type conversion rules
     */
    public static String toString(Object o) {
        String val = null;

        o = unpack(o);

        if (o instanceof Boolean) {
            val = ((Boolean) o ? "true" : "false");
        } else if (o instanceof Double) {
            double d = (Double) o;
            if (Double.isNaN(d)) {
                val = "NaN";
            } else if (Math.abs(d) < 1.0e-12) {
                val = "0";
            } else if (Double.isInfinite(d)) {
                val = (d < 0 ? "-" : "") + "Infinity";
            } else if (Math.abs(d - (int) d) < 1.0e-12) {
                val = String.valueOf((int) d);
            } else {
                val = String.valueOf(d);
            }
        } else if (o instanceof String) {
            val = (String) o;
        } else if (o instanceof Date) {
            val = DateUtils.formatDateTime((Date) o, DateUtils.FORMAT_ISO8601);
        } else if (o instanceof IExprDataType) {
            val = ((IExprDataType) o).toString();
        }

        if (val != null) {
            return val;
        } else {
            throw new XPathTypeMismatchException("converting to string");
        }
    }

    /**
     * Convert a value to a {@link Date}. note that xpath has no intrinsic representation of dates, so this
     * is off-spec.
     * <p>
     * Dates convert to strings as 'yyyy-mm-dd', convert to numbers as # of days since
     * the unix epoch, and convert to booleans always as 'true'.
     * <p>
     * This function parses input values to dates. Input values can be:
     * * A well formatted ISO8601 string representation of a date, with or without time.
     * Examples: '2018-01-01', '2018-01-01T10:20:30.400', '2018-01-01T10:20:30.400+02'
     * * An epoch integer measured in days (Days since 1970-01-01, negative values are allowed)
     * <p>
     * Some values won't get parsed and will be returned as is without throwing an exception:
     * * Empty string
     * * Double.NaN (used by xpath as 'null value')
     * * A value that is already a Date
     * <p>
     * Any other value will throw an exception. Specifically:
     * * Double.POSITIVE_INFINITY and Double.NEGATIVE_INFINITY
     * * A double value less than Integer.MIN_VALUE or greater than Integer.MAX_VALUE
     * * A non parseable string
     *
     * @param input an Object containing a well formatted Date string, an epoch integer (in days)
     *              or a {@link Date} instance
     * @return a {@link Date} instance
     */
    public static Object toDate(Object input, boolean preserveTime) {
        input = unpack(input);

        if (input instanceof Double) {
            if (preserveTime) {
                Double n = (Double) input;

                if (n.isNaN()) {
                    return n;
                }

                if (n.isInfinite() || n > Integer.MAX_VALUE || n < Integer.MIN_VALUE) {
                    throw new XPathTypeMismatchException("The value \"" + n + "\" is out of range for representing a date.");
                }

                long timeMillis = (long) (n * DateUtils.DAY_IN_MS);

                Date d = new Date(timeMillis);
                return d;
            } else {
                Double n = toInt(input);

                if (n.isNaN()) {
                    return n;
                }

                if (n.isInfinite() || n > Integer.MAX_VALUE || n < Integer.MIN_VALUE) {
                    throw new XPathTypeMismatchException("The value \"" + n + "\" is out of range for representing a date.");
                }

                return DateUtils.dateAdd(DateUtils.getDate(1970, 1, 1), n.intValue());
            }
        } else if (input instanceof String) {
            String s = (String) input;

            if (s.length() == 0) {
                return s;
            }

            Date d = DateUtils.parseDateTime(s);
            if (d == null) {
                throw new XPathTypeMismatchException("The value \"" + s + "\" can't be converted to a date.");
            } else {
                return d;
            }
        } else if (input instanceof Date) {
            if (preserveTime) {
                return (Date) input;
            } else {
                return DateUtils.roundDate((Date) input);
            }
        } else {
            throw new XPathTypeMismatchException("The value \"" + input.toString() + "\" can't be converted to a date.");
        }
    }

    public static Object toDecimalDateTime(Object o, boolean keepDate) {
        o = unpack(o);

        if (o instanceof Double) {
            Double n = (Double) o;

            if (n.isNaN()) {
                return n;
            }

            if (n.isInfinite() || n > Integer.MAX_VALUE || n < Integer.MIN_VALUE) {
                throw new XPathTypeMismatchException("The value \"" + n + "\" is out of range for representing a date.");
            }

            if (keepDate) {
                return n;
            } else {
                return n - Math.floor(n);
            }
        } else if (o instanceof String) {
            String s = (String) o;

            if (s.length() == 0) {
                return s;
            }

            Date d = DateUtils.parseDateTime(s);
            if (d == null) {
                throw new XPathTypeMismatchException("The value \"" + s + "\" can't be converted to a date.");
            } else {
                if (keepDate) {
                    long milli = d.getTime();
                    Double v = ((double) milli) / DateUtils.DAY_IN_MS;
                    return v;
                } else {
                    return DateUtils.decimalTimeOfLocalDay(d);
                }
            }
        } else if (o instanceof Date) {
            Date d = (Date) o;
            if (keepDate) {
                long milli = d.getTime();
                Double v = ((double) milli) / DateUtils.DAY_IN_MS;
                return v;
            } else {
                return DateUtils.decimalTimeOfLocalDay(d);
            }
        } else {
            throw new XPathTypeMismatchException("The value \"" + o.toString() + "\" can't be converted to a date.");
        }
    }

    public static Boolean boolNot(Object o) {
        boolean b = toBoolean(o);
        return !b;
    }

    public static Boolean boolStr(Object o) {
        String s = toString(o);
        if (s.equalsIgnoreCase("true") || s.equals("1"))
            return Boolean.TRUE;
        else
            return Boolean.FALSE;
    }

    public static String formatDateTime(Object inputValue, Object format) {
        Object parseResult = toDate(inputValue, true);
        return parseResult instanceof Date
            ? DateUtils.format((Date) parseResult, toString(format))
            : "";
    }

    private Double position(TreeReference refAt) {
        return (double) (1 + refAt.getMultLast());
    }

    public static Object ifThenElse(DataInstance model, EvaluationContext ec, XPathExpression[] args, Object[] argVals) {
        argVals[0] = args[0].eval(model, ec);
        boolean b = toBoolean(argVals[0]);
        return (b ? args[1].eval(model, ec) : args[2].eval(model, ec));
    }

    /**
     * Provides a method of indexing fields stored in a repeat without writing an XPath filter expression.
     * <p>
     * args[0] = reference to a field inside one or more repeats
     * args[1] = generic XPath expression for repeat to index
     * args[2] = index number for repeat instance
     * args[3] = generic XPath expression for add'l repeat to index (if 5 or 7 parameters passed)
     * args[4] = index number for repeat (if 5 or 7 parameters passed)
     * args[5] = generic XPath expression for add'l repeat to index (if 7 parameters passed)
     * args[6] = index number for repeat (if 7 parameters passed)
     */
    public static Object indexedRepeat(DataInstance model, EvaluationContext ec, XPathExpression[] args, Object[] argVals) throws XPathTypeMismatchException {
        // initialize target and context references
        if (!(args[0] instanceof XPathPathExpr)) {
            throw new XPathTypeMismatchException("indexed-repeat(): first parameter must be XPath field reference");
        }
        XPathPathExpr targetPath = (XPathPathExpr) args[0];
        // Ensure we can deal with relative target refs by contextualizing with the EC's context ref
        TreeReference targetRef = targetPath.getReference().contextualize(ec.getContextRef());
        TreeReference contextRef = targetRef.clone();

        // process passed index(es)
        for (int pathargi = 1, idxargi = 2; idxargi < args.length; pathargi += 2, idxargi += 2) {
            // confirm that we were passed an XPath
            if (!(args[pathargi] instanceof XPathPathExpr)) {
                throw new XPathTypeMismatchException("indexed-repeat(): parameter " + (pathargi + 1) + " must be a reference to a repeat");
            }
            // confirm that the passed XPath is a parent of our overall target path
            // Ensure we can deal with relative group refs by contextualizing with the EC's context ref
            TreeReference groupRef = ((XPathPathExpr) args[pathargi]).getReference().contextualize(ec.getContextRef());
            if (!groupRef.isAncestorOf(targetRef, true)) {
                throw new XPathTypeMismatchException("indexed-repeat(): parameter " + (pathargi + 1) + " must be a parent of the field in parameter 1");
            }

            // process index (if valid)
            int groupIdx = toInt(args[idxargi].eval(model, ec)).intValue();
            if (groupIdx <= 0) {
                /*
                   Don't allow invalid index otherwise an exception will be thrown later by XPathNodeset#unpack().
                   This may happen if referenced node hadn't gotten a value yet but the calculation was fired. For example when adding a new repeat.
                 */
                groupIdx = 1;
            }

            contextRef.setMultiplicity(groupRef.size() - 1, groupIdx - 1);
        }

        // evaluate and return the XPath expression, in context
        EvaluationContext revisedec = new EvaluationContext(ec, contextRef);
        // The target ref might have changed due to the contextualization. Ensure we evaluate a fresh XPath path expression:
        return XPathPathExpr.fromRef(targetRef).eval(revisedec);
    }

    /**
     * return whether a particular choice of a multi-select is selected
     *
     * @param o1 XML-serialized answer to multi-select question (i.e, space-delimited choice values)
     * @param o2 choice to look for
     */
    public static Boolean multiSelected(Object o1, Object o2, String functionName) {
        Object indexObject = unpack(o2);
        if (!(indexObject instanceof String)) {
            throw new XPathTypeMismatchException("The second parameter to the " + functionName + "() function must be in quotes (like '1').");
        }
        String s1 = (String) unpack(o1);
        String s2 = ((String) indexObject).trim();

        return (" " + s1 + " ").contains(" " + s2 + " ");
    }

    /**
     * return the number of choices in a multi-select answer
     *
     * @param o XML-serialized answer to multi-select question (i.e, space-delimited choice values)
     */
    public static Double countSelected(Object o) {
        String s = (String) unpack(o);

        return (double) DateUtils.split(s, " ", true).size();
    }

    /**
     * Get the Nth item in a selected list
     *
     * @param o1 XML-serialized answer to multi-select question (i.e, space-delimited choice values)
     * @param o2 the integer index into the list to return
     */
    public static String selectedAt(Object o1, Object o2) {
        String selection = (String) unpack(o1);
        int index = toInt(o2).intValue();
        List<String> stringVector = DateUtils.split(selection, " ", true);
        if (stringVector.size() > index && index >= 0) {
            return stringVector.get(index);
        } else {
            return ""; // empty string if outside of array
        }
    }

    /**
     * count the number of nodes in a nodeset
     */
    public static Double count(Object o) {
        if (o instanceof XPathNodeset) {
            return (double) ((XPathNodeset) o).size();
        } else {
            throw new XPathTypeMismatchException("not a nodeset");
        }
    }

    /**
     * A node is considered non-empty if it is convertible into a string with a greater-than-zero length.
     *
     * @param o NodeSet to evaluate. Throws if not a NodeSet
     * @return the number of non-empty nodes in argument node-set.
     */
    private static Double countNonEmpty(Object o) {
        if (o instanceof XPathNodeset) {
            return (double) ((XPathNodeset) o).getNonEmptySize();
        }

        throw new XPathTypeMismatchException("not a nodeset");
    }


    /**
     * sum the values in a nodeset; each element is coerced to a numeric value
     */
    public static Double sum(Object argVals[]) {
        double sum = 0.0;
        for (Object argVal : argVals) {
            Double dargVal = toNumeric(argVal);
            if (!dargVal.isNaN()) {
                sum += dargVal;
            }
        }
        return sum;
    }

    /**
     * round function like in Excel.
     */
    private static Double round(double number, int numDecimals) {
        // rounding doesn't affect special values
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return number;
        }

        // absurdly large rounding requests yield NaN
        if (numDecimals > 30 || numDecimals < -30) {
            return NaN;
        }

        // Rounding with positive decimals
        if (numDecimals >= 0) {
            try {
                // Per XPath specification, round up or towards zero
                int method = (number < 0) ? BigDecimal.ROUND_HALF_DOWN : BigDecimal.ROUND_HALF_UP;
                return (new BigDecimal(
                    Double.toString(number)))
                    .setScale(numDecimals, method)
                    .doubleValue();
            } catch (NumberFormatException ex) {
                if (Double.isInfinite(number)) {
                    return number;
                } else {
                    return Double.NaN;
                }
            }
        }

        // we want to retain 100's or higher value
        // round(33.33, -1) = 30.0
        final BigDecimal numScaled = BigDecimal.valueOf(number).scaleByPowerOfTen(numDecimals);

        // round to the nearest long
        final BigDecimal numRounded = BigDecimal.valueOf((double) ((long) (numScaled.doubleValue() + 0.5)));

        // and now restore the number to the appropriate scale
        return numRounded.scaleByPowerOfTen(-numDecimals).doubleValue();
    }

    /**
     * Identify the largest value from the list of provided values.
     */
    private static Object max(Object[] argVals) {
        double max = Double.MIN_VALUE;
        boolean returnNaN = true;
        for (Object argVal : argVals) {
            Double dargVal = toNumeric(argVal);
            if (!dargVal.isNaN()) {
                max = Math.max(max, dargVal);
                returnNaN = false;
            }
        }
        return returnNaN ? NaN : max;
    }

    private static Object min(Object[] argVals) {
        double min = Double.MAX_VALUE;
        boolean returnNaN = true;
        for (Object argVal : argVals) {
            Double dargVal = toNumeric(argVal);
            if (!dargVal.isNaN()) {
                min = Math.min(min, dargVal);
                returnNaN = false;
            }
        }
        return returnNaN ? NaN : min;
    }

    /**
     * concatenate an arbitrary-length argument list of string values together
     */
    public static String join(Object oSep, Object[] argVals) {
        String sep = toString(oSep);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < argVals.length; i++) {
            sb.append(toString(argVals[i]));
            if (i < argVals.length - 1)
                sb.append(sep);
        }

        return sb.toString();
    }

    public static String substring(Object o1, Object o2, Object o3) {
        String s = toString(o1);
        int start = toInt(o2).intValue();

        int len = s.length();

        int end = (o3 != null ? toInt(o3).intValue() : len);
        if (start < 0) {
            start = len + start;
        }
        if (end < 0) {
            end = len + end;
        }
        end = Math.min(Math.max(0, end), len);
        start = Math.min(Math.max(0, start), len);

        return (start <= end ? s.substring(start, end) : "");
    }

    /**
     * perform a 'checklist' computation, enabling expressions like 'if there are at least 3 risk
     * factors active'
     * <p>
     * the first argument is a numeric value expressing the minimum number of factors required.
     * if -1, no minimum is applicable
     * the second argument is a numeric value expressing the maximum number of allowed factors.
     * if -1, no maximum is applicalbe
     * arguments 3 through the end are the individual factors, each coerced to a boolean value
     *
     * @return true if the count of 'true' factors is between the applicable minimum and maximum,
     *     inclusive
     */
    public static Boolean checklist(Object oMin, Object oMax, Object[] factors) {
        int min = toNumeric(oMin).intValue();
        int max = toNumeric(oMax).intValue();

        int count = 0;
        for (Object factor : factors) {
            if (toBoolean(factor))
                count++;
        }

        return (min < 0 || count >= min) && (max < 0 || count <= max);
    }

    /**
     * very similar to checklist, only each factor is assigned a real-number 'weight'.
     * <p>
     * the first and second args are again the minimum and maximum, but -1 no longer means
     * 'not applicable'.
     * <p>
     * subsequent arguments come in pairs: first the boolean value, then the floating-point
     * weight for that value
     * <p>
     * the weights of all the 'true' factors are summed, and the function returns whether
     * this sum is between the min and max
     */
    public static Boolean checklistWeighted(Object oMin, Object oMax, Object[] flags, Object[] weights) {
        double min = toNumeric(oMin);
        double max = toNumeric(oMax);

        double sum = 0.;
        for (int i = 0; i < flags.length; i++) {
            boolean flag = toBoolean(flags[i]);
            double weight = toNumeric(weights[i]);

            if (flag)
                sum += weight;
        }

        return sum >= min && sum <= max;
    }

    /**
     * determine if a string matches a regular expression.
     *
     * @param o1 string being matched
     * @param o2 regular expression
     */
    public static Boolean regex(Object o1, Object o2) {
        String str = toString(o1);
        String re = toString(o2);

        return Pattern.matches(re, str);
    }

    private static String base64Decode(Object o1) {
        String base64String = toString(o1);

        byte[] decoded = Encoding.BASE64.decode(base64String.getBytes(StandardCharsets.UTF_8));
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static String extractSigned(Object o1, Object o2) {
        byte[] decodedContents = Base64.decode(toString(o1).getBytes());
        byte[] decodedPublicKey = Base64.decode(toString(o2).getBytes());

        String extracted = Ed25519.extractSigned(decodedContents, decodedPublicKey);
        if (extracted != null) {
            return extracted;
        } else {
            return "";
        }
    }

    private static Object[] subsetArgList(Object[] args, int start) {
        return subsetArgList(args, start, 1);
    }

    /**
     * return a subset of an argument list as a new arguments list
     *
     * @param start index to start at
     * @param skip  sub-list will contain every nth argument, where n == skip (default: 1)
     */
    private static Object[] subsetArgList(Object[] args, int start, int skip) {
        if (start > args.length || skip < 1) {
            throw new RuntimeException("error in subsetting arglist");
        }

        Object[] subargs = new Object[(int) MathUtils.divLongNotSuck(args.length - start - 1, skip) + 1];
        for (int i = start, j = 0; i < args.length; i += skip, j++) {
            subargs[j] = args[i];
        }

        return subargs;
    }

    public static Object unpack(Object o) {
        if (o instanceof XPathNodeset) {
            return ((XPathNodeset) o).unpack();
        } else {
            return o;
        }
    }

    /**
     * Throws an arity exception if expected arity doesn't match the provided arity.
     *
     * @param name          the function name
     * @param expectedArity expected number of arguments to the function
     * @param providedArity number of arguments actually provided to the function
     */
    private static void checkArity(String name, int expectedArity, int providedArity)
        throws XPathArityException {
        if (expectedArity != providedArity) {
            throw new XPathArityException(name, expectedArity, providedArity);
        }
    }

    @Override
    public Object pivot(DataInstance model, EvaluationContext evalContext, List<Object> pivots, Object sentinal) throws UnpivotableExpressionException {
        String name = id.toString();

        //for now we'll assume that all that functions do is return the composition of their components
        Object[] argVals = new Object[args.length];


        //Identify whether this function is an identity: IE: can reflect back the pivot sentinal with no modification
        String[] identities = new String[]{"string-length"};
        boolean id = false;
        for (String identity : identities) {
            if (identity.equals(name)) {
                id = true;
            }
        }

        //get each argument's pivot
        for (int i = 0; i < args.length; i++) {
            argVals[i] = args[i].pivot(model, evalContext, pivots, sentinal);
        }

        boolean pivoted = false;
        //evaluate the pivots
        for (Object argVal : argVals) {
            if (argVal == null) {
                //one of our arguments contained pivots,
                pivoted = true;
            } else if (sentinal.equals(argVal)) {
                //one of our arguments is the sentinal, return the sentinal if possible
                if (id) {
                    return sentinal;
                } else {
                    //This function modifies the sentinal in a way that makes it impossible to capture
                    //the pivot.
                    throw new UnpivotableExpressionException();
                }
            }
        }

        if (pivoted) {
            if (id) {
                return null;
            } else {
                //This function modifies the sentinal in a way that makes it impossible to capture
                //the pivot.
                throw new UnpivotableExpressionException();
            }
        }

        //TODO: Inner eval here with eval'd args to improve speed
        return eval(model, evalContext);

    }

    @Override
    public boolean isIdempotent() {
        return Arrays.asList(IDEMPOTENT_FUNCTIONS).contains(id.toString()) &&
            Arrays.stream(args).allMatch(XPathExpression::isIdempotent);
    }

    @Override
    public boolean containsFunc(@NotNull String name) {
        return name.equals(id.name) || Arrays.stream(args).anyMatch(expression -> expression.containsFunc(name));
    }

    public static final String[] IDEMPOTENT_FUNCTIONS = new String[]{
        "regex",
        "starts-with",
        "ends-with",
        "contains",
        "substr",
        "substring-before",
        "substring-after",
        "translate",
        "string-length",
        "normalize-space",
        "concat",
        "join",
        "boolean-from-string",
        "string"
    };
}
