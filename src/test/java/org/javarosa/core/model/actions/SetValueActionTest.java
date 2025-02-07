/*
 * Copyright 2020 Nafundi
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

package org.javarosa.core.model.actions;

import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.test.Scenario;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.javarosa.core.test.AnswerDataMatchers.intAnswer;
import static org.javarosa.core.test.AnswerDataMatchers.stringAnswer;
import static org.javarosa.test.BindBuilderXFormsElement.bind;
import static org.javarosa.test.XFormsElement.body;
import static org.javarosa.test.XFormsElement.group;
import static org.javarosa.test.XFormsElement.head;
import static org.javarosa.test.XFormsElement.html;
import static org.javarosa.test.XFormsElement.input;
import static org.javarosa.test.XFormsElement.mainInstance;
import static org.javarosa.test.XFormsElement.model;
import static org.javarosa.test.XFormsElement.repeat;
import static org.javarosa.test.XFormsElement.setvalue;
import static org.javarosa.test.XFormsElement.setvalueLiteral;
import static org.javarosa.test.XFormsElement.t;
import static org.javarosa.test.XFormsElement.title;
import static org.junit.Assert.fail;

public class SetValueActionTest {
    @Test
    public void when_triggerNodeIsUpdated_targetNodeCalculation_isEvaluated() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Nested setvalue action", html(
            head(
                title("Nested setvalue action"),
                model(
                    mainInstance(t("data id=\"nested-setvalue\"",
                        t("source"),
                        t("destination")
                    )),
                    bind("/data/source").type("int"),
                    bind("/data/destination").type("int")
                )
            ),
            body(
                input("/data/source",
                    setvalue("xforms-value-changed", "/data/destination", "4*4"))
            )));

        assertThat(scenario.answerOf("/data/destination"), is(nullValue()));

        scenario.next();
        scenario.answer(22);

        assertThat(scenario.answerOf("/data/destination"), is(intAnswer(16)));
    }

    @Test
    public void expressionAsLiteralValue_isNotEvaluated() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Literal setvalue", html(
            head(
                title("Literal setvalue"),
                model(
                    mainInstance(t("data id=\"literal-setvalue\"",
                        t("source"),
                        t("destination")
                    )),
                    bind("/data/source").type("string"),
                    bind("/data/destination").type("string")
                )
            ),
            body(
                input("/data/source",
                    setvalueLiteral("xforms-value-changed", "/data/destination", "4*4"))
            )));

        assertThat(scenario.answerOf("/data/destination"), is(nullValue()));

        scenario.next();
        scenario.answer(22);

        assertThat(scenario.answerOf("/data/destination"), is(stringAnswer("4*4")));
    }

    @Test
    public void when_triggerNodeIsUpdatedWithTheSameValue_targetNodeCalculation_isNotEvaluated() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Nested setvalue action", html(
            head(
                title("Nested setvalue action"),
                model(
                    mainInstance(t("data id=\"nested-setvalue\"",
                        t("source"),
                        t("destination"),
                        t("some-field")
                    )),
                    bind("/data/destination").type("string")
            )),
            body(
                input("/data/source",
                    setvalue("xforms-value-changed", "/data/destination", "concat('foo',/data/some-field)")),
                input("/data/some-field")
            )));

        assertThat(scenario.answerOf("/data/destination"), is(nullValue()));

        scenario.next();
        scenario.answer(22);
        assertThat(scenario.answerOf("/data/destination"), is(stringAnswer("foo")));

        scenario.next();
        scenario.answer("bar");

        scenario.prev();
        scenario.answer(22);
        assertThat(scenario.answerOf("/data/destination"), is(stringAnswer("foo")));

        scenario.answer(23);
        assertThat(scenario.answerOf("/data/destination"), is(stringAnswer("foobar")));
    }

    @Test
    public void setvalue_isSerializedAndDeserialized() throws IOException, DeserializationException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Nested setvalue action", html(
            head(
                title("Nested setvalue action"),
                model(
                    mainInstance(t("data id=\"nested-setvalue\"",
                        t("source"),
                        t("destination")
                    )),
                    bind("/data/destination").type("int")
                )
            ),
            body(
                input("/data/source",
                    setvalue("xforms-value-changed", "/data/destination", "4*4"))
            )));

        scenario.serializeAndDeserializeForm();

        assertThat(scenario.answerOf("/data/destination"), is(nullValue()));

        scenario.next();
        scenario.answer(22);

        assertThat(scenario.answerOf("/data/destination"), is(intAnswer(16)));
    }

    @Test
    public void setvalueWithNoValue_isSerializedAndDeserialized() throws IOException, DeserializationException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue action", html(
            head(
                title("Setvalue action"),
                model(
                    mainInstance(t("data id=\"setvalue\"",
                        t("source"),
                        t("destination", "10")
                    )),
                    bind("/data/destination").type("int")
                )
            ),
            body(
                input("/data/source",
                    setvalue("xforms-value-changed", "/data/destination"))
            )));

        scenario.serializeAndDeserializeForm();

        assertThat(scenario.answerOf("/data/destination"), is(intAnswer(10)));

        scenario.next();
        scenario.answer(5);

        assertThat(scenario.answerOf("/data/destination"), is(nullValue()));
    }

    //region groups
    @Test
    public void setvalueInGroup_setsValueOutsideOfGroup() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue", html(
            head(
                title("Setvalue"),
                model(
                    mainInstance(t("data id=\"setvalue\"",
                        t("g",
                            t("source")),
                        t("destination")
                    )),
                    bind("/data/g/source").type("int"),
                    bind("/data/destination").type("int")
                )
            ),
            body(
                group("/data/g",
                    input("/data/g/source",
                        setvalueLiteral("xforms-value-changed", "/data/destination", "7")
                    ))
            )));

        scenario.answer("/data/g/source", "foo");
        assertThat(scenario.answerOf("/data/destination"), is(intAnswer(7)));
    }

    @Test
    public void setvalueOutsideGroup_setsValueInGroup() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue", html(
            head(
                title("Setvalue"),
                model(
                    mainInstance(t("data id=\"setvalue\"",
                        t("source"),
                        t("g",
                            t("destination"))
                    )),
                    bind("/data/source").type("int"),
                    bind("/data/g/destination").type("int")
                )
            ),
            body(
                input("/data/source",
                    setvalueLiteral("xforms-value-changed", "/data/g/destination", "7")
                ))
        ));

        scenario.answer("/data/source", "foo");
        assertThat(scenario.answerOf("/data/g/destination"), is(intAnswer(7)));
    }
    //endregion

    //region repeats
    @Test
    public void sourceInRepeat_updatesDestInSameRepeatInstance() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Nested setvalue action with repeats", html(
            head(
                title("Nested setvalue action with repeats"),
                model(
                    mainInstance(t("data id=\"nested-setvalue-repeats\"",
                        t("repeat",
                            t("source"),
                            t("destination")
                        )
                    )),
                    bind("/data/repeat/destination").type("int")
                )
            ),
            body(
                repeat("/data/repeat",
                    input("/data/repeat/source",
                        setvalue("xforms-value-changed", "/data/repeat/destination", "4*position(..)"))
                )
            )));

        final int REPEAT_COUNT = 5;

        for (int i = 1; i <= REPEAT_COUNT; i++) {
            scenario.createNewRepeat("/data/repeat");
            assertThat(scenario.answerOf("/data/repeat[" + i + "]/destination"), is(nullValue()));
        }

        for (int i = 1; i <= REPEAT_COUNT; i++) {
            scenario.answer("/data/repeat[" + i + "]/source", 7);
        }

        for (int i = 1; i <= REPEAT_COUNT; i++) {
            assertThat(scenario.answerOf("/data/repeat[" + i + "]/destination"), is(intAnswer(4 * i)));
        }
    }

    @Test
    public void setvalueAtRoot_setsValueOfNodeInFirstRepeatInstance() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue into repeat", html(
            head(
                title("Setvalue into repeat"),
                model(
                    mainInstance(t("data id=\"setvalue-into-repeat\"",
                        t("source"),
                        t("repeat",
                            t("destination")
                        )
                    ))
                )
            ),
            body(
                input("/data/source",
                    setvalue("xforms-value-changed", "/data/repeat[position()=1]/destination", "/data/source")),
                repeat("/data/repeat",
                    input("/data/repeat/destination")
                ))));

        scenario.createNewRepeat("/data/repeat");
        scenario.createNewRepeat("/data/repeat");
        scenario.createNewRepeat("/data/repeat");

        scenario.answer("/data/source", "foo");
        assertThat(scenario.answerOf("/data/repeat[1]/destination").getDisplayText(), is("foo"));
    }

    @Ignore("TODO: verifyActions seems like it may be overzealous")
    @Test
    public void setvalueAtRoot_setsValueOfNodeInRepeatInstanceAddedAfterFormLoad() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue into repeat", html(
            head(
                title("Setvalue into repeat"),
                model(
                    mainInstance(t("data id=\"setvalue-into-repeat\"",
                        t("source"),
                        t("repeat",
                            t("destination")
                        )
                    ))
                )
            ),
            body(
                input("/data/source",
                    setvalue("xforms-value-changed", "/data/repeat[position()=2]/destination", "/data/source")),
                repeat("/data/repeat",
                    input("/data/repeat/destination")
                ))));

        scenario.createNewRepeat("/data/repeat");
        scenario.createNewRepeat("/data/repeat");
        scenario.createNewRepeat("/data/repeat");

        scenario.answer("/data/source", "foo");
        assertThat(scenario.answerOf("/data/repeat[2]/destination").getDisplayText(), is("foo"));
    }

    @Test
    public void setValueAtRoot_throwsExpression_whenTargetIsUnboundReference() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue into repeat", html(
            head(
                title("Setvalue into repeat"),
                model(
                    mainInstance(t("data id=\"setvalue-into-repeat\"",
                        t("source"),
                        t("repeat",
                            t("destination")
                        )
                    ))
                )
            ),
            body(
                input("/data/source",
                    setvalue("xforms-value-changed", "/data/repeat/destination", "/data/source")),
                repeat("/data/repeat",
                    input("/data/repeat/destination")
            ))));

        scenario.createNewRepeat("/data/repeat");
        scenario.createNewRepeat("/data/repeat");
        scenario.createNewRepeat("/data/repeat");

        try {
            scenario.answer("/data/source", "foo");
            fail("Expected multiple node target to fail");
        } catch (XPathTypeMismatchException e) {
            assertThat(e.getMessage(), containsString("has more than one node"));
        }
    }

    @Test
    public void setValueInRepeat_setsValueOutsideOfRepeat() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Nested setvalue action with repeats", html(
            head(
                title("Nested setvalue action with repeats"),
                model(
                    mainInstance(t("data id=\"nested-setvalue-repeats\"",
                        t("destination", "0"),
                        t("repeat",
                            t("source")
                        )
                    )),
                    bind("/data/destination").type("int")
                )
            ),
            body(
                repeat("/data/repeat",
                    input("/data/repeat/source",
                        setvalue("xforms-value-changed", "/data/destination", ".+1"))
                )
            )));

        final int REPEAT_COUNT = 5;

        for (int i = 1; i <= REPEAT_COUNT; i++) {
            scenario.createNewRepeat("/data/repeat");
            assertThat(scenario.answerOf("/data/destination"), is(intAnswer(0)));
        }

        for (int i = 1; i <= REPEAT_COUNT; i++) {
            scenario.answer("/data/repeat[" + i + "]/source", 7);
            assertThat(scenario.answerOf("/data/destination"), is(intAnswer(i)));
        }
    }

    @Test
    public void setvalueInOuterRepeat_setsInnerRepeatValue() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Nested repeats", html(
            head(
                title("Nested repeats"),
                model(
                    mainInstance(t("data id=\"nested-repeats\"",
                        t("repeat1",
                            t("source"),
                            t("repeat2",
                                t("destination")
                            ))
                    ))
                )
            ),
            body(
                repeat("/data/repeat1",
                    input("/data/repeat1/source",
                        setvalue("xforms-value-changed", "/data/repeat1/repeat2/destination", "../../source")),
                    repeat("/data/repeat1/repeat2",
                        input("/data/repeat1/repeat2/destination")
            )))));

            scenario.answer("/data/repeat1[0]/source", "foo");
            assertThat(scenario.answerOf("/data/repeat1[0]/repeat2[0]/destination").getDisplayText(), is("foo"));
    }
    //endregion

    /**
     * Read-only is a display-only concern so it should be possible to use an action to modify the value of a read-only
     * field.
     */
    @Test
    public void setvalue_setsValueOfReadOnlyField() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue readonly", html(
            head(
                title("Setvalue readonly"),
                model(
                    mainInstance(t("data id=\"setvalue-readonly\"",
                        t("readonly-field")
                    )),
                    bind("/data/readonly-field").readonly("1").type("int"),
                    setvalue("odk-instance-first-load", "/data/readonly-field", "4*4")
                )
            ),
            body(
                input("/data/readonly-field")
            )));

        assertThat(scenario.answerOf("/data/readonly-field"), is(intAnswer(16)));
    }

    @Test
    public void setvalue_withInnerEmptyString_clearsTarget() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue empty string", html(
            head(
                title("Setvalue empty string"),
                model(
                    mainInstance(t("data id=\"setvalue-empty-string\"",
                        t("a-field", "12")
                    )),
                    bind("/data/a-field").type("int"),
                    setvalue("odk-instance-first-load", "/data/a-field")
                )
            ),
            body(
                input("/data/a-field")
            )));

        assertThat(scenario.answerOf("/data/a-field"), is(nullValue()));
    }

    @Test
    public void setvalue_withEmptyStringValue_clearsTarget() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue empty string", html(
            head(
                title("Setvalue empty string"),
                model(
                    mainInstance(t("data id=\"setvalue-empty-string\"",
                        t("a-field", "12")
                    )),
                    bind("/data/a-field").type("int"),
                    setvalue("odk-instance-first-load", "/data/a-field", "")
                )
            ),
            body(
                input("/data/a-field")
            )));

        assertThat(scenario.answerOf("/data/a-field"), is(nullValue()));
    }

    @Test
    public void setvalue_setsValueOfMultipleFields() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue multiple destinations", html(
            head(
                title("Setvalue multiple destinations"),
                model(
                    mainInstance(t("data id=\"setvalue-multiple\"",
                        t("source"),
                        t("destination1"),
                        t("destination2")
                    )),
                    bind("/data/destination1").type("int"),
                    bind("/data/destination2").type("int")
                )
            ),
            body(
                input("/data/source",
                    setvalueLiteral("xforms-value-changed", "/data/destination1", "7"),
                    setvalueLiteral("xforms-value-changed", "/data/destination2", "11"))
            )));

        scenario.answer("/data/source", "foo");
        assertThat(scenario.answerOf("/data/destination1"), is(intAnswer(7)));
        assertThat(scenario.answerOf("/data/destination2"), is(intAnswer(11)));
    }

    @Test
    public void xformsValueChanged_triggeredAfterRecomputation() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("xforms-value-changed-event", html(
            head(
                title("Value changed event"),
                model(
                    mainInstance(t("data id=\"xforms-value-changed-event\"",
                        t("source"),
                        t("calculate"),
                        t("destination")
                    )),
                    bind("/data/calculate").type("int").calculate("/data/source * 2"),
                    bind("/data/destination").type("int")
                )
            ),
            body(
                input("/data/source",
                    setvalue("xforms-value-changed", "/data/destination", "/data/calculate")
            ))));

        scenario.answer("/data/source", 12);
        assertThat(scenario.answerOf("/data/destination"), is(intAnswer(24)));
    }

    @Test
    public void setvalue_setsValueOfAttribute() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Setvalue attribute", html(
            head(
                title("Setvalue attribute"),
                model(
                    mainInstance(t("data id=\"setvalue-attribute\"",
                        t("element attr=\"\"")
                    )),
                    setvalue("odk-instance-first-load", "/data/element/@attr", "7")
                )
            ),
            body(
                input("/data/element")
            )));

        assertThat(scenario.answerOf("/data/element/@attr").getDisplayText(), is("7"));
    }

    @Test
    public void setvalue_setsValueOfAttribute_afterDeserialization() throws IOException, XFormParser.ParseException, DeserializationException {
        Scenario scenario = Scenario.init("Setvalue attribute", html(
            head(
                title("Setvalue attribute"),
                model(
                    mainInstance(t("data id=\"setvalue-attribute\"",
                        t("element attr=\"\"")
                    )),
                    setvalue("odk-instance-first-load", "/data/element/@attr", "7")
                )
            ),
            body(
                input("/data/element")
            )));

        assertThat(scenario.answerOf("/data/element/@attr").getDisplayText(), is("7"));

        Scenario cached = scenario.serializeAndDeserializeForm();

        cached.newInstance();
        assertThat(cached.answerOf("/data/element/@attr").getDisplayText(), is("7"));
    }
}
