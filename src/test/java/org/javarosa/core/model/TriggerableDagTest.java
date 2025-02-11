package org.javarosa.core.model;

import static java.lang.String.join;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.javarosa.core.test.AnswerDataMatchers.booleanAnswer;
import static org.javarosa.core.test.AnswerDataMatchers.intAnswer;
import static org.javarosa.core.test.AnswerDataMatchers.stringAnswer;
import static org.javarosa.core.test.FormDefMatchers.valid;
import static org.javarosa.core.test.QuestionDefMatchers.enabled;
import static org.javarosa.core.test.QuestionDefMatchers.nonRelevant;
import static org.javarosa.core.test.QuestionDefMatchers.readOnly;
import static org.javarosa.core.test.QuestionDefMatchers.relevant;
import static org.javarosa.test.Scenario.getRef;
import static org.javarosa.test.BindBuilderXFormsElement.bind;
import static org.javarosa.test.XFormsElement.body;
import static org.javarosa.test.XFormsElement.group;
import static org.javarosa.test.XFormsElement.head;
import static org.javarosa.test.XFormsElement.html;
import static org.javarosa.test.XFormsElement.input;
import static org.javarosa.test.XFormsElement.item;
import static org.javarosa.test.XFormsElement.label;
import static org.javarosa.test.XFormsElement.mainInstance;
import static org.javarosa.test.XFormsElement.model;
import static org.javarosa.test.XFormsElement.repeat;
import static org.javarosa.test.XFormsElement.select1;
import static org.javarosa.test.XFormsElement.t;
import static org.javarosa.test.XFormsElement.title;
import static org.javarosa.form.api.FormEntryController.ANSWER_CONSTRAINT_VIOLATED;
import static org.javarosa.form.api.FormEntryController.ANSWER_REQUIRED_BUT_EMPTY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import org.hamcrest.CoreMatchers;
import org.javarosa.test.Scenario;
import org.javarosa.test.BindBuilderXFormsElement;
import org.javarosa.test.XFormsElement;
import org.javarosa.debug.Event;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xpath.expr.XPathPathExpr;
import org.javarosa.xpath.expr.XPathPathExprEval;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TriggerableDagTest {
    private static Logger logger = LoggerFactory.getLogger(TriggerableDagTest.class);

    private List<Event> dagEvents = new ArrayList<>();

    @Before
    public void setUp() {
        dagEvents.clear();
    }

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void order_of_the_DAG_is_ensured() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("a", "2"),
                        t("b"),
                        t("c")
                    )),
                    bind("/data/a").type("int"),
                    bind("/data/b").type("int").calculate("/data/a * 3"),
                    bind("/data/c").type("int").calculate("(/data/a + /data/b) * 5")
                )
            ),
            body(input("/data/a"))
        ));

        assertThat(scenario.answerOf("/data/a"), is(intAnswer(2)));
        assertThat(scenario.answerOf("/data/b"), is(intAnswer(6)));
        assertThat(scenario.answerOf("/data/c"), is(intAnswer(40)));

        scenario.answer("/data/a", 3);

        assertThat(scenario.answerOf("/data/a"), is(intAnswer(3)));
        assertThat(scenario.answerOf("/data/b"), is(intAnswer(9)));
        // Verify that c gets computed using the updated value of b.
        assertThat(scenario.answerOf("/data/c"), is(intAnswer(60)));
    }

    //region Cycles
    @Test
    public void parsing_forms_with_cycles_by_self_reference_in_calculate_should_fail() throws IOException, XFormParser.ParseException {
        exceptionRule.expect(XFormParseException.class);
        exceptionRule.expectMessage("Cycle detected in form's relevant and calculation logic!");

        Scenario.init("Some form", buildFormForDagCyclesCheck(
            bind("/data/count").type("int").calculate(". + 1")
        ));
    }

    @Test
    public void parsing_forms_with_cycles_in_calculate_should_fail() throws IOException, XFormParser.ParseException {
        exceptionRule.expect(XFormParseException.class);
        exceptionRule.expectMessage("Cycle detected in form's relevant and calculation logic!");

        Scenario.init("Some form", buildFormForDagCyclesCheck(
            bind("/data/a").type("int").calculate("/data/b + 1"),
            bind("/data/b").type("int").calculate("/data/c + 1"),
            bind("/data/c").type("int").calculate("/data/a + 1")
        ));
    }

    @Test
    public void parsing_forms_with_cycles_by_self_reference_in_relevance_should_fail() throws IOException, XFormParser.ParseException {
        exceptionRule.expect(XFormParseException.class);
        exceptionRule.expectMessage("Cycle detected in form's relevant and calculation logic!");

        Scenario.init("Some form", buildFormForDagCyclesCheck(
            bind("/data/count").type("int").relevant(". > 0")
        ));
    }

    @Test
    public void parsing_forms_with_cycles_by_self_reference_in_read_only_condition_should_fail() throws IOException, XFormParser.ParseException {
        exceptionRule.expect(XFormParseException.class);
        exceptionRule.expectMessage("Cycle detected in form's relevant and calculation logic!");

        Scenario.init("Some form", buildFormForDagCyclesCheck(
            bind("/data/count").type("int").readonly(". > 10")
        ));
    }

    @Test
    public void parsing_forms_with_cycles_by_self_reference_in_required_condition_should_fail() throws IOException, XFormParser.ParseException {
        exceptionRule.expect(XFormParseException.class);
        exceptionRule.expectMessage("Cycle detected in form's relevant and calculation logic!");

        Scenario.init("Some form", buildFormForDagCyclesCheck(
            bind("/data/count").type("int").required(". > 10")
        ));
    }

    @Test
    public void supports_self_references_in_constraints() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", buildFormForDagCyclesCheck(
            bind("/data/count").type("int").constraint(". > 10")
        ));
        scenario.next();
        scenario.answer(5);
        assertThat(scenario.answerOf("/data/count"), is(nullValue()));
        scenario.answer(20);
        assertThat(scenario.answerOf("/data/count"), is(intAnswer(20)));
        scenario.answer(5);
        assertThat(scenario.answerOf("/data/count"), is(intAnswer(20)));
    }

    /**
     * This test is here to represent a use case that might seem like it
     * has a cycle, but it doesn't.
     * <p>
     * This test is ignored because the current implementation incorrectly
     * detects a cycle given the relevance conditions we have used. Once
     * this is fixed, this test would be a regression test to ensure we
     * never rollback on the fix.
     * <p>
     * The relevance conditions used here a co-dependant(field a depends
     * on b, b depends on a), but they depend on the field's value, not on
     * the field's relevance expression. This is why there's no cycle here.
     * <p>
     * To have a cycle using relevance conditions exclusively, we would need
     * a isRelevant() xpath function that doesn't exist and change the revelance
     * expressions to:
     *
     * <code>
     * bind("/data/a").type("int").relevant("isRelevant(/data/b) > 0")
     * bind("/data/b").type("int").relevant("isRelevant(/data/a) > 0")
     * </code>
     */
    @Test
    @Ignore
    public void supports_codependant_relevant_expressions() throws IOException, XFormParser.ParseException {
        Scenario.init("Some form", buildFormForDagCyclesCheck(
            bind("/data/a").type("int").relevant("/data/b > 0"),
            bind("/data/b").type("int").relevant("/data/a > 0")));
        // TODO Complete the test adding some assertions that verify that the form works as we would expect
    }

    /**
     * This test is here to represent a use case that might seem like it
     * has a cycle, but it doesn't.
     * <p>
     * This test is ignored because the current implementation incorrectly
     * detects a cycle given the required conditions we have used. Once
     * this is fixed, this test would be a regression test to ensure we
     * never rollback on the fix.
     * <p>
     * The required conditions used here a co-dependant(field a depends
     * on b, b depends on a), but they depend on the field's value, not on
     * the field's required conditions. This is why there's no cycle here.
     * <p>
     * To have a cycle using required conditions exclusively, we would need
     * a isRequired() xpath function that doesn't exist and change the required
     * expressions to:
     *
     * <code>
     * bind("/data/a").type("int").required("isRequired(/data/b) > 0")
     * bind("/data/b").type("int").required("isRequired(/data/a) > 0")
     * </code>
     */
    @Test
    @Ignore
    public void supports_codependant_required_conditions() throws IOException, XFormParser.ParseException {
        Scenario.init("Some form", buildFormForDagCyclesCheck(
            bind("/data/a").type("int").required("/data/b > 0"),
            bind("/data/b").type("int").required("/data/a > 0")));
        // TODO Complete the test adding some assertions that verify that the form works as we would expect
    }

    /**
     * This test is here to represent a use case that might seem like it
     * has a cycle, but it doesn't.
     * <p>
     * This test is ignored because the current implementation incorrectly
     * detects a cycle given the readonly conditions we have used. Once
     * this is fixed, this test would be a regression test to ensure we
     * never rollback on the fix.
     * <p>
     * The readonly conditions used here a co-dependant(field a depends
     * on b, b depends on a), but they depend on the field's value, not on
     * the field's readonly conditions. This is why there's no cycle here.
     * <p>
     * To have a cycle using readonly conditions exclusively, we would need
     * a isReadonly() xpath function that doesn't exist and change the readonly
     * expressions to:
     *
     * <code>
     * bind("/data/a").type("int").readonly("isReadonly(/data/b) > 0")
     * bind("/data/b").type("int").readonly("isReadonly(/data/a) > 0")
     * </code>
     */
    @Test
    @Ignore
    public void supports_codependant_readonly_conditions() throws IOException, XFormParser.ParseException {
        Scenario.init("Some form", buildFormForDagCyclesCheck(
            bind("/data/a").type("int").readonly("/data/b > 0"),
            bind("/data/b").type("int").readonly("/data/a > 0")));
        // TODO Complete the test adding some assertions that verify that the form works as we would expect
    }

    @Test
    public void parsing_forms_with_cycles_involving_fields_inside_and_outside_of_repeat_groups_should_fail() throws IOException, XFormParser.ParseException {
        exceptionRule.expect(XFormParseException.class);
        exceptionRule.expectMessage("Cycle detected in form's relevant and calculation logic!");

        Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("group", t("a", "1")),
                        t("b", "1")
                    )),
                    bind("/data/group/a").type("int").calculate("/data/b + 1"),
                    bind("/data/b").type("int").calculate("/data/group[position() = 1]/a + 1")
                )
            ),
            body(
                group("/data/group",
                    repeat("/data/group",
                        input("/data/group/a")
                    )
                ),
                input("/data/b")
            )
        ));
    }

    @Test
    public void parsing_forms_with_self_reference_cycles_in_fields_of_repeat_groups_should_fail() throws IOException, XFormParser.ParseException {
        exceptionRule.expect(XFormParseException.class);
        exceptionRule.expectMessage("Cycle detected in form's relevant and calculation logic!");

        Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("group",
                            t("a", "1")
                        )
                    )),
                    bind("/data/group/a").type("int").calculate("../a + 1")
                )
            ),
            body(group("/data/group", repeat("/data/group",
                input("/data/group/a")
            )))
        ));
    }

    /**
     * This test fails to parse the form because it thinks there's a
     * self-reference cycle in /data/group/a,
     * but this would be incorrect because each it depends on the same field
     * belonging to the previous
     * repeat instance, which wouldn't be a cycle, but an autoincremental
     * feature.
     */
    @Test
    @Ignore
    public void supports_self_reference_dependency_when_targeting_different_repeat_instance_siblings() throws IOException, XFormParser.ParseException {
        Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("group",
                            t("a", "1")
                        )
                    )),
                    bind("/data/group/a").type("int").calculate("/data/group[position() = (position(current()) - 1)]/a + 1")
                )
            ),
            body(group("/data/group", repeat("/data/group",
                input("/data/group/a")
            )))
        ));
    }


    @Test
    public void parsing_forms_with_cycles_between_fields_of_the_same_repeat_instance_should_fail() throws IOException, XFormParser.ParseException {
        exceptionRule.expect(XFormParseException.class);
        exceptionRule.expectMessage("Cycle detected in form's relevant and calculation logic!");

        Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("group",
                            t("a", "1"),
                            t("b", "1")
                        )
                    )),
                    bind("/data/group/a").type("int").calculate("../b + 1"),
                    bind("/data/group/b").type("int").calculate("../a + 1")
                )
            ),
            body(group("/data/group", repeat("/data/group",
                input("/data/group/a"),
                input("/data/group/b")
            )))
        ));
    }
    //endregion

    //region Relevance
    /**
     * Non-relevance is inherited from ancestor nodes, as per the W3C XForms specs:
     * - https://www.w3.org/TR/xforms11/#model-prop-relevant
     * - https://www.w3.org/community/xformsusers/wiki/XForms_2.0#The_relevant_Property
     */
    @Test
    public void non_relevance_is_inherited_from_ancestors() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("is-group-relevant"),
                        t("is-field-relevant"),
                        t("group", t("field"))
                    )),
                    bind("/data/is-group-relevant").type("boolean"),
                    bind("/data/is-field-relevant").type("boolean"),
                    bind("/data/group").relevant("/data/is-group-relevant"),
                    bind("/data/group/field").type("string").relevant("/data/is-field-relevant")
                )
            ),
            body(
                input("/data/is-group-relevant"),
                input("/data/is-field-relevant"),
                group("/data/group", input("/data/group/field"))
            )));

        // Form initialization evaluates all triggerables, which makes the group and
        //field non-relevants because their relevance expressions are not satisfied
        assertThat(scenario.getAnswerNode("/data/group"), is(nonRelevant()));
        assertThat(scenario.getAnswerNode("/data/group/field"), is(nonRelevant()));

        // Now we make both relevant
        scenario.answer("/data/is-group-relevant", true);
        scenario.answer("/data/is-field-relevant", true);
        assertThat(scenario.getAnswerNode("/data/group"), is(relevant()));
        assertThat(scenario.getAnswerNode("/data/group/field"), is(relevant()));

        // Now we make the group non-relevant, which makes the field non-relevant
        // regardless of its local relevance expression, which would be satisfied
        // in this case
        scenario.answer("/data/is-group-relevant", false);
        assertThat(scenario.getAnswerNode("/data/group"), is(nonRelevant()));
        assertThat(scenario.getAnswerNode("/data/group/field"), is(nonRelevant()));
    }

    /**
     * Nodes can be nested differently in the model and body. The model structure is used
     * to determine relevance inheritance.
     */
    @Test
    public void relevanceIsDeterminedByModelNesting() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("outernode"),
                        t("group",
                            t("innernode"))
                    )),
                    bind("/data/group").relevant("false()")
                    )
            ),
            body(
                group("/data/group",
                    input("/data/outernode"),
                    input("/data/group/innernode"))
            )));

        assertThat(scenario.getAnswerNode("/data/group"), is(nonRelevant()));
        assertThat(scenario.getAnswerNode("/data/outernode"), is(relevant()));
        assertThat(scenario.getAnswerNode("/data/group/innernode"), is(nonRelevant()));
    }

    @Test
    public void non_relevant_nodes_are_excluded_from_nodeset_evaluation() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        // position() is one-based
                        t("node", t("value", "1")), // non-relevant
                        t("node", t("value", "2")), // non-relevant
                        t("node", t("value", "3")), // relevant
                        t("node", t("value", "4")), // relevant
                        t("node", t("value", "5")) // relevant
                    )),
                    bind("/data/node").relevant("position() > 2"),
                    bind("/data/node/value").type("int")
                )
            ),
            body(
                group("/data/node", input("/data/node/value"))
            )));

        // The XPathPathExprEval is used when evaluating the nodesets that the
        // xpath functions declared in triggerable expressions need to operate
        // upon. This assertion shows that non-relevant nodes are not included
        // in the resulting nodesets
        assertThat(
            new XPathPathExprEval().eval(getRef("/data/node"), scenario.getEvaluationContext()).getReferences(),
            hasSize(3)
        );

        // The method XPathPathExpr.getRefValue is what ultimately is used by
        // triggerable expressions to extract the values they need to operate
        // upon. The following assertion shows how extrating values from
        // non-relevant nodes returns `null` values instead of the actual values
        // they're holding
        assertThat(
            XPathPathExpr.getRefValue(
                scenario.getFormDef().getMainInstance(),
                scenario.getEvaluationContext(),
                scenario.expandSingle(getRef(("/data/node[2]/value")))
            ),
            is("")
        );
        // ... as opposed to the value that we can get by resolving the same
        // reference with the main instance, which has the expected `2` value
        assertThat(scenario.answerOf("/data/node[2]/value"), is(intAnswer(2)));
    }

    @Test
    public void non_relevant_node_values_are_always_null_regardless_of_their_actual_value() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("relevance-trigger", "1"),
                        t("result"),
                        t("some-field", "42")
                    )),
                    bind("/data/relevance-trigger").type("boolean"),
                    bind("/data/result").type("int").calculate("if(/data/some-field != '', /data/some-field + 33, 33)"),
                    bind("/data/some-field").type("int").relevant("/data/relevance-trigger")
                )
            ),
            body(
                input("/data/relevance-trigger")
            )));

        assertThat(scenario.answerOf("/data/result"), is(intAnswer(75)));
        assertThat(scenario.answerOf("/data/some-field"), is(intAnswer(42)));

        scenario.answer("/data/relevance-trigger", false);

        // This shows how JavaRosa will ignore the actual values of non-relevant fields. The
        // W3C XForm specs regard relevance a purely UI concern. No side effects on node values
        // are described in the specs, which implies that a relevance change wouln't
        // have any consequence on a node's value. This means that /data/result should keep having
        // a 75 after making /data/some-field non-relevant.
        assertThat(scenario.answerOf("/data/result"), is(intAnswer(33)));
        assertThat(scenario.answerOf("/data/some-field"), is(intAnswer(42)));
    }

    // Users use relevance on calculates to ensure that calculations are only run when the values
    // they need are populated. Using relevance is easier than adding a condition.
    @Test
    public void relevance_appliesToElementsWithoutControls() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Relevance on calculate", html(
            head(
                title("Relevance on calculate"),
                model(
                    mainInstance(t("data id=\"relevance-calculate\"",
                        t("q1"),
                        t("c1"),
                        t("c2")
                    )),
                    bind("/data/q1").type("string"),
                    bind("/data/c1").calculate("2 * 2").relevant("/data/q1 = 'yes'"),
                    bind("/data/c2").calculate("/data/c1")
                )),
            body(
                input("/data/q1")
            )));

        assertThat(scenario.answerOf("/data/c2"), is(nullValue()));

        scenario.answer("/data/q1", "yes");
        assertThat(scenario.answerOf("/data/c2"), is(intAnswer(4)));
    }

    /**
     * This test was inspired by the issue reported at https://code.google.com/archive/p/opendatakit/issues/888
     * <p>
     * We want to focus on the relationship between relevance and other calculations
     * because relevance can be defined for fields **and groups**, which is a special
     * case of expression evaluation in our DAG.
     */
    @Test
    public void verify_relation_between_calculate_expressions_and_relevancy_conditions() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(
                        t("data id=\"some-form\"",
                            t("number1"),
                            t("continue"),
                            t("group", t("number1_x2"), t("number1_x2_x2"), t("number2"))
                        )
                    ),
                    bind("/data/number1").type("int").constraint(". > 0").required(),
                    bind("/data/continue").type("string").required(),
                    bind("/data/group").relevant("/data/continue = '1'"),
                    bind("/data/group/number1_x2").type("int").calculate("/data/number1 * 2"),
                    bind("/data/group/number1_x2_x2").type("int").calculate("/data/group/number1_x2 * 2"),
                    bind("/data/group/number2").type("int").relevant("/data/group/number1_x2 > 0").required()
                )
            ),
            body(
                input("/data/number1"),
                select1("/data/continue",
                    item(1, "Yes"),
                    item(0, "No")
                ),
                group("/data/group",
                    input("/data/group/number2")
                )
            )
        ));
        scenario.next();
        scenario.answer(2);
        assertThat(scenario.answerOf("/data/group/number1_x2"), is(intAnswer(4)));
        // The expected value is null because the calculate expression uses a non-relevant field.
        // Values of non-relevant fields are always null.
        assertThat(scenario.answerOf("/data/group/number1_x2_x2"), is(nullValue()));
        scenario.next();
        scenario.answer("1"); // Label: "yes"
        assertThat(scenario.answerOf("/data/group/number1_x2"), is(intAnswer(4)));
        assertThat(scenario.answerOf("/data/group/number1_x2_x2"), is(intAnswer(8)));
    }

    /**
     * Identical expressions in a form get collapsed to a single Triggerable and the Triggerable's context becomes
     * its targets' highest common parent (see Triggerable.intersectContextWith). This makes evaluation in the context
     * of repeats hard to reason about. This test shows that relevance is propagated as expected when a relevance expression
     * is shared between a repeat and non-repeat. See https://github.com/getodk/javarosa/issues/603.
     */
    @Test
    public void whenRepeatAndTopLevelNodeHaveSameRelevanceExpression_andExpressionEvaluatesToFalse_repeatPromptIsSkipped() throws Exception {
        Scenario scenario = Scenario.init("Repeat relevance same as other", html(
            head(
                title("Repeat relevance same as other"),
                model(
                    mainInstance(t("data id=\"repeat_relevance_same_as_other\"",
                        t("selectYesNo", "no"),
                        t("repeat1",
                            t("q1")),
                        t("q0")
                    )),
                    bind("/data/q0").relevant("/data/selectYesNo = 'yes'"),
                    bind("/data/repeat1").relevant("/data/selectYesNo = 'yes'")
                )),
            body(
                select1("/data/selectYesNo",
                    item("yes", "Yes"),
                    item("no", "No")),
                repeat("/data/repeat1",
                    input("/data/repeat1/q1")
                )
            )));

        scenario.jumpToBeginningOfForm();
        scenario.next();
        int event = scenario.next();

        assertThat(event, is(FormEntryController.EVENT_END_OF_FORM));
    }
    //endregion

    //region Read-only
    /**
     * Read-only is inherited from ancestor nodes, as per the W3C XForms specs:
     * - https://www.w3.org/TR/xforms11/#model-prop-relevant
     */
    @Test
    public void readonly_is_inherited_from_ancestors() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("is-outer-readonly"),
                        t("is-inner-readonly"),
                        t("is-field-readonly"),
                        t("outer",
                            t("inner",
                                t("field")))
                    )),
                    bind("/data/is-outer-readonly").type("boolean"),
                    bind("/data/is-inner-readonly").type("boolean"),
                    bind("/data/is-field-readonly").type("boolean"),
                    bind("/data/outer").readonly("/data/is-outer-readonly"),
                    bind("/data/outer/inner").readonly("/data/is-inner-readonly"),
                    bind("/data/outer/inner/field").type("string").readonly("/data/is-field-readonly")
                )
            ),
            body(
                input("/data/is-outer-readonly"),
                input("/data/is-inner-readonly"),
                input("/data/is-field-readonly"),
                group("/data/outer", group("/data/outer/inner", input("/data/outer/inner/field")))
            )));

        // Form initialization evaluates all triggerables, which makes the field editable (not read-only)
        assertThat(scenario.getAnswerNode("/data/outer"), is(enabled()));
        assertThat(scenario.getAnswerNode("/data/outer/inner"), is(enabled()));
        assertThat(scenario.getAnswerNode("/data/outer/inner/field"), is(enabled()));

        // Make the outer group read-only
        scenario.answer("/data/is-outer-readonly", true);
        assertThat(scenario.getAnswerNode("/data/outer"), is(readOnly()));
        assertThat(scenario.getAnswerNode("/data/outer/inner"), is(readOnly()));
        assertThat(scenario.getAnswerNode("/data/outer/inner/field"), is(readOnly()));

        // Make the inner group read-only
        scenario.answer("/data/is-outer-readonly", false);
        scenario.answer("/data/is-inner-readonly", true);
        assertThat(scenario.getAnswerNode("/data/outer"), is(enabled()));
        assertThat(scenario.getAnswerNode("/data/outer/inner"), is(readOnly()));
        assertThat(scenario.getAnswerNode("/data/outer/inner/field"), is(readOnly()));

        // Make the field read-only
        scenario.answer("/data/is-inner-readonly", false);
        scenario.answer("/data/is-field-readonly", true);
        assertThat(scenario.getAnswerNode("/data/outer"), is(enabled()));
        assertThat(scenario.getAnswerNode("/data/outer/inner"), is(enabled()));
        assertThat(scenario.getAnswerNode("/data/outer/inner/field"), is(readOnly()));
    }
    //endregion Read-only

    //region Required and constraint
    @Test
    public void constraints_of_fields_that_are_empty_are_always_satisfied() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("a"),
                        t("b")
                    )),
                    bind("/data/a").type("string").constraint("/data/b"),
                    bind("/data/b").type("boolean")
                )
            ),
            body(
                input("/data/a"),
                input("/data/b")
            )));

        // Ensure that the constraint expression in /data/a won't be satisfied
        scenario.answer("/data/b", false);

        // Verify that regardless of the constraint defined in /data/a, the
        // form appears to be valid
        assertThat(scenario.getFormDef(), is(valid()));
    }

    @Test
    public void empty_required_fields_make_form_validation_fail() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("a"),
                        t("b")
                    )),
                    bind("/data/a").type("string").required(),
                    bind("/data/b").type("boolean")
                )
            ),
            body(
                input("/data/a"),
                input("/data/b")
            )));

        ValidateOutcome validate = scenario.getValidationOutcome();
        assertThat(validate.failedPrompt, is(scenario.indexOf("/data/a")));
        assertThat(validate.outcome, is(ANSWER_REQUIRED_BUT_EMPTY));
    }


    @Test
    public void constraint_violations_and_form_finalization() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("a"),
                        t("b")
                    )),
                    bind("/data/a").type("string").constraint("/data/b"),
                    bind("/data/b").type("boolean")
                )
            ),
            body(
                input("/data/a"),
                input("/data/b")
            )));

        // First, ensure we will be able to commit an answer in /data/a by
        // making it match its constraint. No values can be committed to the
        // instance if constraints aren't satisfied.
        scenario.answer("/data/b", true);

        // Then, commit an answer (answers with empty values are always valid)
        scenario.answer("/data/a", "cocotero");

        // Then, make the constraint defined at /data/a impossible to satisfy
        scenario.answer("/data/b", false);

        // At this point, the form has /data/a filled with an answer that's
        // invalid according to its constraint expression, but we can't be
        // aware of that, unless we validate the whole form.
        //
        // Clients like Collect will validate the whole form before marking
        // a submission as complete and saving it to the filesystem.
        //
        // FormDef.validate(boolean) will go through all the relevant fields
        // re-answering them with their current values in order to detect
        // any constraint violations. When this happens, a non-null
        // ValidationOutcome object is returned including information about
        // the violated constraint.
        ValidateOutcome validate = scenario.getValidationOutcome();
        assertThat(validate.failedPrompt, is(scenario.indexOf("/data/a")));
        assertThat(validate.outcome, is(ANSWER_CONSTRAINT_VIOLATED));
    }
    //endregion

    //region Adding or deleting repeats
    @Test
    public void addingRepeatInstance_updatesCalculationCascade() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Add repeat instance", html(
            head(
                title("Add repeat instance"),
                model(
                    mainInstance(t("data id=\"repeat-calcs\"",
                        t("repeat",
                            t("inner1"),
                            t("inner2"),
                            t("inner3")
                        ))),
                    bind("/data/repeat/inner2").calculate("2 * ../inner1"),
                    bind("/data/repeat/inner3").calculate("2 * ../inner2"))),

            body(
                repeat("/data/repeat",
                    input("/data/repeat/inner1"))
            )));

        scenario.next();
        scenario.next();
        scenario.answer(0);

        assertThat(scenario.answerOf("/data/repeat[1]/inner2"), CoreMatchers.is(intAnswer(0)));
        assertThat(scenario.answerOf("/data/repeat[1]/inner3"), CoreMatchers.is(intAnswer(0)));

        scenario.next();
        scenario.createNewRepeat();
        scenario.next();

        scenario.answer(1);

        assertThat(scenario.answerOf("/data/repeat[2]/inner2"), CoreMatchers.is(intAnswer(2)));
        assertThat(scenario.answerOf("/data/repeat[2]/inner3"), CoreMatchers.is(intAnswer(4)));
    }

    @Test
    public void addingRepeat_updatesInnerCalculations_withMultipleDependencies() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Repeat cascading calc", html(
            head(
                title("Repeat cascading calc"),
                model(
                    mainInstance(t("data id=\"repeat-calcs\"",
                        t("repeat",
                            t("position"),
                            t("position_2"),
                            t("other"),
                            t("concatenated")
                        ))),
                    // position(..) means the full cascade is evaulated as part of triggerTriggerables
                    bind("/data/repeat/position").calculate("position(..)"),
                    bind("/data/repeat/position_2").calculate("../position * 2"),
                    bind("/data/repeat/other").calculate("2 * 2"),
                    // concat needs to be evaluated after /data/repeat/other has a value
                    bind("/data/repeat/concatenated").calculate("concat(../position_2, '-', ../other)"))),
            body(
                repeat("/data/repeat",
                    input("/data/repeat/concatenated"))
            )));

        scenario.next();
        scenario.next();
        assertThat(scenario.answerOf("/data/repeat[1]/concatenated"), CoreMatchers.is(stringAnswer("2-4")));

        scenario.next();
        scenario.createNewRepeat();

        scenario.next();
        assertThat(scenario.answerOf("/data/repeat[2]/concatenated"), CoreMatchers.is(stringAnswer("4-4")));
    }

    // Illustrates the second case in TriggerableDAG.getTriggerablesAffectingAllInstances
    @Test
    public void addingOrRemovingRepeatInstance_withCalculatedCountOutsideRepeat_updatesReferenceToCountInside() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Count outside repeat used inside", html(
            head(
                title("Count outside repeat used inside"),
                model(
                    mainInstance(t("data id=\"outside-used-inside\"",
                        t("count"),

                        t("repeat jr:template=\"\"",
                            t("question"),
                            t("inner-count"))
                    )),
                    bind("/data/count").type("int").calculate("count(/data/repeat)"),
                    bind("/data/repeat/inner-count").type("int").calculate("/data/count"))),

            body(
                repeat("/data/repeat",
                    input("/data/repeat/question")
                )
            ))).onDagEvent(dagEvents::add);

        dagEvents.clear();

        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            assertThat(scenario.answerOf("/data/count"), CoreMatchers.is(intAnswer(n)));
            scenario.next();
        });

        range(1, 6).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(5))));

        scenario.removeRepeat("/data/repeat[5]");

        range(1, 5).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(4))));
    }

    // In this case, the count(/data/repeat) expression is represented by a single triggerable. The expression gets
    // evaluated once and it's the expandReference call in Triggerable.apply which ensures the result is updated for
    // every repeat instance.
    @Test
    public void addingOrRemovingRepeatInstance_updatesRepeatCount_insideAndOutsideRepeat() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Count outside repeat used inside", html(
            head(
                title("Count outside repeat used inside"),
                model(
                    mainInstance(t("data id=\"outside-used-inside\"",
                        t("count"),

                        t("repeat jr:template=\"\"",
                            t("question"),
                            t("inner-count"))
                    )),
                    bind("/data/count").type("int").calculate("count(/data/repeat)"),
                    bind("/data/repeat/inner-count").type("int").calculate("count(/data/repeat)"))),

            body(
                repeat("/data/repeat",
                    input("/data/repeat/question")
                )
            )));

        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(n)));
            scenario.next();
        });

        range(1, 6).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(5))));

        scenario.removeRepeat("/data/repeat[5]");

        range(1, 5).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(4))));
    }

    // In this case, /data/repeat in the count(/data/repeat) expression is given the context of the current repeat so the
    // count always evaluates to 1. See contrast with addingOrRemovingRepeatInstance_updatesRepeatCount_insideAndOutsideRepeat.
    @Ignore("Highlights issue with de-duplicating refs and different contexts")
    @Test
    public void addingOrRemovingRepeatInstance_updatesRepeatCount_insideRepeat() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Count outside repeat used inside", html(
            head(
                title("Count outside repeat used inside"),
                model(
                    mainInstance(t("data id=\"outside-used-inside\"",
                        t("repeat jr:template=\"\"",
                            t("question"),
                            t("inner-count"))
                    )),
                    bind("/data/repeat/inner-count").type("int").calculate("count(/data/repeat)"))),

            body(
                repeat("/data/repeat",
                    input("/data/repeat/question")
                )
            )));

        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(n)));
            scenario.next();
        });

        range(1, 6).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(5))));

        scenario.removeRepeat("/data/repeat[4]");

        range(1, 5).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(4))));
    }

    @Test
    public void addingOrRemovingRepeatInstance_updatesRelativeRepeatCount_insideRepeat() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Count outside repeat used inside", html(
            head(
                title("Count outside repeat used inside"),
                model(
                    mainInstance(t("data id=\"outside-used-inside\"",
                        t("repeat jr:template=\"\"",
                            t("question"),
                            t("inner-count"))
                    )),
                    bind("/data/repeat/inner-count").type("int").calculate("count(../../repeat)"))),

            body(
                repeat("/data/repeat",
                    input("/data/repeat/question")
                )
            )));

        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(n)));
            scenario.next();
        });

        range(1, 6).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(5))));

        scenario.removeRepeat("/data/repeat[4]");

        range(1, 5).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-count"), CoreMatchers.is(intAnswer(4))));
    }

    @Test
    public void addingOrRemovingRepeatInstance_withReferenceToRepeatInRepeat_andOuterSum_updates() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Count outside repeat used inside", html(
            head(
                title("Count outside repeat used inside"),
                model(
                    mainInstance(t("data id=\"outside-used-inside\"",
                        t("sum"),

                        t("repeat jr:template=\"\"",
                            t("question"),
                            t("position1"),
                            t("position2"))
                    )),
                    bind("/data/sum").type("int").calculate("sum(/data/repeat/position1)"),
                    bind("/data/repeat/position1").type("int").calculate("position(..)"),
                    bind("/data/repeat/position2").type("int").calculate("../position1"))),

            body(
                repeat("/data/repeat",
                    input("/data/repeat/position1")
                )
            )));

        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            assertThat(scenario.answerOf("/data/sum"), CoreMatchers.is(intAnswer(n * (n + 1) / 2)));
            scenario.next();
        });

        range(1, 6).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/position1"), CoreMatchers.is(intAnswer(n))));

        scenario.removeRepeat("/data/repeat[5]");

        range(1, 5).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/position2"), CoreMatchers.is(intAnswer(n))));
    }


    @Test
    public void addingOrRemovingRepeatInstance_withReferenceToPreviousInstance_updatesThatReference() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("group jr:template=\"\"",
                            t("prev-number"),
                            t("number")
                        )
                    )),
                    bind("/data/group/prev-number").type("int").calculate("/data/group[position() = (position(current()/..) - 1)]/number"),
                    bind("/data/group/number").type("int").required()
                )
            ),
            body(group("/data/group", repeat("/data/group", input("/data/group/number"))))
        ));

        scenario.next();
        scenario.createNewRepeat();
        scenario.next();
        scenario.answer(11);

        assertThat(scenario.answerOf("/data/group[1]/prev-number"), is(nullValue()));
        assertThat(scenario.answerOf("/data/group[1]/number"), is(intAnswer(11)));

        scenario.next();
        scenario.createNewRepeat();
        scenario.next();
        scenario.answer(22);

        assertThat(scenario.answerOf("/data/group[1]/number"), is(intAnswer(11)));
        assertThat(scenario.answerOf("/data/group[2]/number"), is(intAnswer(22)));

        assertThat(scenario.answerOf("/data/group[1]/prev-number"), is(nullValue()));
        assertThat(scenario.answerOf("/data/group[2]/prev-number"), is(intAnswer(11)));

        scenario.next();
        scenario.createNewRepeat();
        scenario.next();
        scenario.answer(33);

        assertThat(scenario.answerOf("/data/group[1]/prev-number"), is(nullValue()));
        assertThat(scenario.answerOf("/data/group[2]/prev-number"), is(intAnswer(11)));
        assertThat(scenario.answerOf("/data/group[3]/prev-number"), is(intAnswer(22)));

        scenario.removeRepeat("/data/group[2]");

        assertThat(scenario.answerOf("/data/group[1]/prev-number"), is(nullValue()));
        assertThat(scenario.answerOf("/data/group[2]/number"), is(intAnswer(33)));
        assertThat(scenario.answerOf("/data/group[2]/prev-number"), is(intAnswer(11)));
    }

    @Test
    public void addingOrDeletingRepeatInstance_withRelevanceInsideRepeatDependingOnCount_updatesRelevanceForAllInstances() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("repeat jr:template=\"\"",
                            t("number"),
                            t("group",
                                t("in_group")
                            )
                        )
                    )),
                    bind("/data/repeat/number").type("int").required(),
                    bind("/data/repeat/group").relevant("count(../../repeat) mod 2 = 1")
                )
            ),
            body(
                repeat("/data/repeat",
                    input("/data/repeat/number"),
                    group("/data/repeat/group",
                        input("/data/repeat/group/in_group")
                    ))
                )
        ));

        scenario.next();
        scenario.createNewRepeat();

        assertThat(scenario.getAnswerNode("/data/repeat[1]/group/in_group").isRelevant(), is(true));

        scenario.createNewRepeat("/data/repeat");

        assertThat(scenario.getAnswerNode("/data/repeat[2]/group/in_group").isRelevant(), is(false));
        assertThat(scenario.getAnswerNode("/data/repeat[1]/group/in_group").isRelevant(), is(false));

        scenario.removeRepeat("/data/repeat[2]");

        assertThat(scenario.getAnswerNode("/data/repeat[1]/group/in_group").isRelevant(), is(true));
    }
    //endregion

    //region Deleting repeats
    @Test
    public void deleteSecondRepeatGroup_evaluatesTriggerables_dependentOnPrecedingRepeatGroupSiblings() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("house jr:template=\"\"", t("number"))
                    )),
                    bind("/data/house/number").type("int").calculate("position(..)")
                )
            ),
            body(group("/data/house", repeat("/data/house", input("number"))))
        )).onDagEvent(dagEvents::add);
        range(1, 6).forEach(__ -> {
            scenario.next();
            scenario.createNewRepeat();
            scenario.next();
        });
        assertThat(scenario.answerOf("/data/house[1]/number"), is(intAnswer(1)));
        assertThat(scenario.answerOf("/data/house[2]/number"), is(intAnswer(2)));
        assertThat(scenario.answerOf("/data/house[3]/number"), is(intAnswer(3)));
        assertThat(scenario.answerOf("/data/house[4]/number"), is(intAnswer(4)));
        assertThat(scenario.answerOf("/data/house[5]/number"), is(intAnswer(5)));

        // Start recording DAG events now
        dagEvents.clear();

        scenario.removeRepeat("/data/house[2]");

        assertThat(scenario.answerOf("/data/house[1]/number"), is(intAnswer(1)));
        assertThat(scenario.answerOf("/data/house[2]/number"), is(intAnswer(2)));
        assertThat(scenario.answerOf("/data/house[3]/number"), is(intAnswer(3)));
        assertThat(scenario.answerOf("/data/house[4]/number"), is(intAnswer(4)));
        assertThat(scenario.answerOf("/data/house[5]/number"), is(nullValue()));
        assertDagEvents(dagEvents,
            "Processing 'Recalculate' for number [1_1] (1.0), number [2_1] (2.0), number [3_1] (3.0), number [4_1] (4.0)",
            "Processing 'Deleted: number [2_1]: 1 triggerables were fired.' for "
        );
    }

    @Test
    public void deleteSecondRepeatGroup_evaluatesTriggerables_dependentOnTheParentPosition() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("house jr:template=\"\"",
                            t("number"),
                            t("name"),
                            t("name_and_number")
                        )
                    )),
                    bind("/data/house/number").type("int").calculate("position(..)"),
                    bind("/data/house/name").type("string").required(),
                    bind("/data/house/name_and_number").type("string").calculate("concat(../name, ../number)")
                )
            ),
            body(group("/data/house", repeat("/data/house", input("/data/house/name"))))
        )).onDagEvent(dagEvents::add);
        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            scenario.next();
            scenario.answer((char) (64 + n));
        });
        assertThat(scenario.answerOf("/data/house[1]/name_and_number"), is(stringAnswer("A1")));
        assertThat(scenario.answerOf("/data/house[2]/name_and_number"), is(stringAnswer("B2")));
        assertThat(scenario.answerOf("/data/house[3]/name_and_number"), is(stringAnswer("C3")));
        assertThat(scenario.answerOf("/data/house[4]/name_and_number"), is(stringAnswer("D4")));
        assertThat(scenario.answerOf("/data/house[5]/name_and_number"), is(stringAnswer("E5")));

        // Start recording DAG events now
        dagEvents.clear();

        scenario.removeRepeat("/data/house[2]");

        assertThat(scenario.answerOf("/data/house[1]/name_and_number"), is(stringAnswer("A1")));
        assertThat(scenario.answerOf("/data/house[2]/name_and_number"), is(stringAnswer("C2")));
        assertThat(scenario.answerOf("/data/house[3]/name_and_number"), is(stringAnswer("D3")));
        assertThat(scenario.answerOf("/data/house[4]/name_and_number"), is(stringAnswer("E4")));
        assertThat(scenario.answerOf("/data/house[5]/name_and_number"), is(nullValue()));
        assertDagEvents(dagEvents,
            "Processing 'Recalculate' for number [1_1] (1.0), number [2_1] (2.0), number [3_1] (3.0), number [4_1] (4.0)",
            "Processing 'Recalculate' for name_and_number [1_1] (A1), name_and_number [2_1] (C2), name_and_number [3_1] (D3), name_and_number [4_1] (E4)",
            "Processing 'Deleted: number [2_1]: 0 triggerables were fired.' for ",
            "Processing 'Deleted: name [2_1]: 0 triggerables were fired.' for ",
            "Processing 'Deleted: name_and_number [2_1]: 2 triggerables were fired.' for "
        );
    }

    @Test
    public void deleteSecondRepeatGroup_doesNotEvaluateTriggerables_notDependentOnTheParentPosition() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("house jr:template=\"\"",
                            t("number"),
                            t("name"),
                            t("name_and_number")
                        )
                    )),
                    bind("/data/house/number").type("int").calculate("position(..)"),
                    bind("/data/house/name").type("string").required(),
                    bind("/data/house/name_and_number").type("string").calculate("concat(../name, 'X')")
                )
            ),
            body(group("/data/house", repeat("/data/house", input("/data/house/name"))))
        )).onDagEvent(dagEvents::add);
        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            scenario.next();
            scenario.answer((char) (64 + n));
        });
        assertThat(scenario.answerOf("/data/house[1]/name_and_number"), is(stringAnswer("AX")));
        assertThat(scenario.answerOf("/data/house[2]/name_and_number"), is(stringAnswer("BX")));
        assertThat(scenario.answerOf("/data/house[3]/name_and_number"), is(stringAnswer("CX")));
        assertThat(scenario.answerOf("/data/house[4]/name_and_number"), is(stringAnswer("DX")));
        assertThat(scenario.answerOf("/data/house[5]/name_and_number"), is(stringAnswer("EX")));

        // Start recording DAG events now
        dagEvents.clear();

        scenario.removeRepeat("/data/house[2]");

        assertThat(scenario.answerOf("/data/house[1]/name_and_number"), is(stringAnswer("AX")));
        assertThat(scenario.answerOf("/data/house[2]/name_and_number"), is(stringAnswer("CX")));
        assertThat(scenario.answerOf("/data/house[3]/name_and_number"), is(stringAnswer("DX")));
        assertThat(scenario.answerOf("/data/house[4]/name_and_number"), is(stringAnswer("EX")));
        assertThat(scenario.answerOf("/data/house[5]/name_and_number"), is(nullValue()));
        assertDagEvents(dagEvents,
            "Processing 'Recalculate' for number [1_1] (1.0), number [2_1] (2.0), number [3_1] (3.0), number [4_1] (4.0)",
            "Processing 'Deleted: number [2_1]: 1 triggerables were fired.' for ",
            "Processing 'Recalculate' for name_and_number [2_1] (CX)",
            "Processing 'Deleted: name [2_1]: 1 triggerables were fired.' for ",
            "Processing 'Deleted: name_and_number [2_1]: 1 triggerables were fired.' for "
        );
    }

    @Test
    public void deleteThirdRepeatGroup_evaluatesTriggerables_dependentOnTheRepeatGroupsNumber() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("house jr:template=\"\"", t("number")),
                        t("summary")
                    )),
                    bind("/data/house/number").type("int").calculate("position(..)"),
                    bind("/data/summary").type("int").calculate("sum(/data/house/number)")
                )
            ),
            body(group("/data/house", repeat("/data/house", input("number"))))
        ));
        range(0, 10).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            scenario.next();
        });
        assertThat(scenario.answerOf("/data/summary"), is(intAnswer(55)));

        scenario.removeRepeat("/data/house[3]");

        assertThat(scenario.answerOf("/data/summary"), is(intAnswer(45)));
    }

    // Verifies that the list of recalculations triggered by the repeat instance deletion is minimal. In particular,
    // calculations outside the repeat should only be re-computed once.
    @Test
    public void repeatInstanceDeletion_triggersCalculationsOutsideTheRepeat_exactlyOnce() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("house jr:template=\"\"", t("number")),
                        t("summary")
                    )),
                    bind("/data/house/number").type("int").calculate("position(..)"),
                    bind("/data/summary").type("int").calculate("sum(/data/house/number)")
                )
            ),
            body(group("/data/house", repeat("/data/house", input("number"))))
        )).onDagEvent(dagEvents::add);
        range(1, 11).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            scenario.next();
        });

        // Start recording DAG events now
        dagEvents.clear();

        scenario.removeRepeat("/data/house[3]");

        assertThat(scenario.answerOf("/data/summary"), is(intAnswer(45)));
        assertDagEvents(dagEvents,
            "Processing 'Recalculate' for number [1_1] (1.0), number [2_1] (2.0), number [3_1] (3.0), number [4_1] (4.0), number [5_1] (5.0), number [6_1] (6.0), number [7_1] (7.0), number [8_1] (8.0), number [9_1] (9.0)",
            "Processing 'Recalculate' for summary [1] (45.0)",
            "Processing 'Deleted: number [3_1]: 0 triggerables were fired.' for "
        );
    }

    @Test
    public void repeatInstanceDeletion_withoutReferencesToRepeat_evaluatesNoTriggersInInstances() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("repeat jr:template=\"\"",
                            t("number"),
                            t("numberx2"),
                            t("calc")
                        )
                    )),
                    bind("/data/repeat/number").type("int"),
                    bind("/data/repeat/numberx2").type("int").calculate("../number * 2"),
                    bind("/data/repeat/calc").type("int").calculate("2 * random()")
                )
            ),
            body(group("/data/repeat", repeat("/data/repeat", input("number"))))
        )).onDagEvent(dagEvents::add);
        range(1, 11).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            scenario.next();
        });

        // Start recording DAG events now
        dagEvents.clear();

        scenario.removeRepeat("/data/repeat[3]");

        assertDagEvents(dagEvents,
            "Processing 'Recalculate' for numberx2 [3_1] (NaN)",
            "Processing 'Deleted: number [3_1]: 1 triggerables were fired.' for ",
            "Processing 'Deleted: numberx2 [3_1]: 0 triggerables were fired.' for ",
            "Processing 'Deleted: calc [3_1]: 0 triggerables were fired.' for "
        );
    }

    /**
     * Indirectly means that the calculation - `concat(/data/house/name)` - does
     * not take the
     * `/data/house` nodeset (the repeat group) as an argument
     * but since it takes one of its children (`name` children),
     * the calculation must re-evaluated once after a repeat group deletion
     * because one of the children
     * has been deleted along with its parent (the repeat group instance).
     */
    @Test
    public void deleteThirdRepeatGroup_evaluatesTriggerables_indirectlyDependentOnTheRepeatGroupsNumber() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("house jr:template=\"\"", t("name")),
                        t("summary")
                    )),
                    bind("/data/house/name").type("string").required(),
                    bind("/data/summary").type("string").calculate("concat(/data/house/name)")
                )
            ),
            body(group("/data/house", repeat("/data/house", input("/data/house/name"))))
        )).onDagEvent(dagEvents::add);
        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            scenario.next();
            scenario.answer((char) (64 + n));
        });
        assertThat(scenario.answerOf("/data/summary"), is(stringAnswer("ABCDE")));

        // Start recording DAG events now
        dagEvents.clear();

        scenario.removeRepeat("/data/house[3]");

        assertThat(scenario.answerOf("/data/summary"), is(stringAnswer("ABDE")));
        assertDagEvents(dagEvents,
            "Processing 'Recalculate' for summary [1] (ABDE)",
                "Processing 'Deleted: name [3_1]: 1 triggerables were fired.' for "
        );
    }

    @Test
    public void deleteLastRepeat_evaluatesTriggerables() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Delete last repeat instance", html(
            head(
                title("Delete last repeat instance"),
                model(
                    mainInstance(t("data id=\"delete-last-repeat-instance\"",
                        t("repeat-count"),

                        t("repeat",
                            t("question")),
                        t("repeat",
                            t("question")),
                        t("repeat",
                            t("question"))
                    )),
                    bind("/data/repeat-count").type("int").calculate("count(/data/repeat)")
                )),
            body(
                repeat("/data/repeat",
                    input("question"))
            )));

        assertThat(scenario.answerOf("/data/repeat-count"), is(intAnswer(3)));

        scenario.removeRepeat("/data/repeat[3]");
        assertThat(scenario.answerOf("/data/repeat-count"), is(intAnswer(2)));
    }

    @Test
    public void deleteLastRepeat_evaluatesTriggerables_indirectlyDependentOnTheDeletedRepeat() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Delete last repeat instance", html(
            head(
                title("Delete last repeat instance"),
                model(
                    mainInstance(t("data id=\"delete-last-repeat-instance\"",
                        t("summary"),

                        t("repeat",
                            t("question", "a")),
                        t("repeat",
                            t("question", "b")),
                        t("repeat",
                            t("question", "c"))
                    )),
                    bind("/data/summary").type("string").calculate("concat(/data/repeat/question)")
                )),
            body(
                repeat("/data/repeat",
                    input("question"))
            )));

        assertThat(scenario.answerOf("/data/summary"), is(stringAnswer("abc")));

        scenario.removeRepeat("/data/repeat[3]");
        assertThat(scenario.answerOf("/data/summary"), is(stringAnswer("ab")));
    }
    //endregion

    //region Adding repeats
    /**
     * Excercises the triggerTriggerables call in createRepeatInstance.
     */
    @Test
    public void adding_repeat_instance_triggers_triggerables_outside_repeat_that_reference_repeat_nodeset() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Form", html(
            head(
                title("Form"),
                model(
                    mainInstance(t("data",
                        t("count"),
                        t("repeat jr:template=\"\"",
                            t("string")
                        )
                    )),
                    bind("/data/count").type("int").calculate("count(/data/repeat)"),
                    bind("/data/repeat/string").type("string")
                )
            ),
            body(
                repeat("/data/repeat",
                    input("/data/repeat/string")
                )
            )
        ));

        scenario.createNewRepeat("/data/repeat");
        scenario.createNewRepeat("/data/repeat");

        assertThat(scenario.answerOf("/data/count"), is(intAnswer(2)));
    }

    /**
     * Excercises the initializeTriggerables call in createRepeatInstance.
     */
    @Test
    public void adding_repeat_instance_triggers_descendant_node_triggerables() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Form", html(
            head(
                title("Form"),
                model(
                    mainInstance(t("data",
                        t("repeat jr:template=\"\"",
                            t("string"),
                            t("group",
                                t("int")
                            )
                        )
                    )),
                    bind("/data/repeat/string").type("string"),
                    bind("/data/repeat/group").relevant("0")
                )
            ),
            body(
                repeat("/data/repeat",
                    input("/data/repeat/string"),
                    input("/data/repeat/group/int")
                )
            )
        ));

        scenario.next();
        scenario.createNewRepeat();
        scenario.next();
        scenario.next();
        assertThat(scenario.getAnswerNode("/data/repeat[0]/group/int"), is(nonRelevant()));

        scenario.createNewRepeat();
        scenario.next();
        scenario.next();
        assertThat(scenario.getAnswerNode("/data/repeat[1]/group/int"), is(nonRelevant()));

        scenario.createNewRepeat();
        scenario.next();
        scenario.next();
        assertThat(scenario.getAnswerNode("/data/repeat[2]/group/int"), is(nonRelevant()));
    }
    //endregion

    //region DAG limitations (cases that aren't correctly updated)
    @Ignore("Fails on v2.17.0 (before DAG simplification)")
    // This case is where a particular field in a repeat makes an aggregate computation over another field in the repeat.
    // This should cause every repeat instance to be updated. We could handle this by using a strategy similar to
    // getTriggerablesAffectingAllInstances but for initializeTriggerables.
    @Test
    public void addingRepeatInstance_withInnerSumOfQuestionInRepeat_updatesInnerSumForAllInstances() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Count outside repeat used inside", html(
            head(
                title("Count outside repeat used inside"),
                model(
                    mainInstance(t("data id=\"outside-used-inside\"",
                        t("repeat jr:template=\"\"",
                            t("question", "5"),
                            t("inner-sum"))
                    )),
                    bind("/data/repeat/inner-sum").type("int").calculate("sum(../../repeat/question)"))),

            body(
                repeat("/data/repeat",
                    input("/data/repeat/question")
                )
            )));

        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-sum"), CoreMatchers.is(intAnswer(n * 5)));
            scenario.next();
        });

        range(1, 6).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-sum"), CoreMatchers.is(intAnswer(25))));

        scenario.removeRepeat("/data/repeat[4]");

        range(1, 5).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-sum"), CoreMatchers.is(intAnswer(20))));
    }

    @Ignore("Fails on v2.17.0 (before DAG simplification)")
    // This case is where a particular field in a repeat is referred to in a calculation outside the repeat and that
    // calculation is then referenced in the repeat. The reference outside the repeat could be from an aggregating
    // function such as sum or with a predicate/indexed-repeat. Then, if that calculation is referred to inside the repeat,
    // every repeat instance should be updated. We could handle this by using a strategy similar to
    // getTriggerablesAffectingAllInstances but for initializeTriggerables.
    @Test
    public void addingRepeatInstance_withInnerCalculateDependentOnOuterSum_updatesInnerSumForAllInstances() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Count outside repeat used inside", html(
            head(
                title("Count outside repeat used inside"),
                model(
                    mainInstance(t("data id=\"outside-used-inside\"",
                        t("sum"),

                        t("repeat jr:template=\"\"",
                            t("question", "5"),
                            t("inner-sum"))
                    )),
                    bind("/data/sum").type("int").calculate("sum(/data/repeat/question)"),
                    bind("/data/repeat/inner-sum").type("int").calculate("/data/sum"))),

            body(
                repeat("/data/repeat",
                    input("/data/repeat/question")
                )
            )));

        range(1, 6).forEach(n -> {
            scenario.next();
            scenario.createNewRepeat();
            assertThat(scenario.answerOf("/data/sum"), CoreMatchers.is(intAnswer(n * 5)));
            scenario.next();
        });

        range(1, 6).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-sum"), CoreMatchers.is(intAnswer(25))));

        scenario.removeRepeat("/data/repeat[4]");

        range(1, 5).forEach(n -> assertThat(scenario.answerOf("/data/repeat[" + n + "]/inner-sum"), CoreMatchers.is(intAnswer(20))));
    }

    @Ignore("Fails on v2.17.0 (before DAG simplification)")
    // In this test, it's not the repeat addition that needs to trigger recomputation across repeat instances, it's
    // the setting of the number value in a specific instance. There's currently no mechanism to do that. When a repeat
    // is added, it will trigger recomputation for previous instances.
    @Test
    public void changingValueInRepeat_withReferenceToNextInstance_updatesPreviousInstance() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("group jr:template=\"\"",
                            t("number"),
                            t("next-number")
                        )
                    )),
                    bind("/data/group/number").type("int").required(),
                    bind("/data/group/next-number").type("int").calculate("/data/group[position() = (position(current()/..) + 1)]/number")
                )
            ),
            body(group("/data/group", repeat("/data/group", input("/data/group/number"))))
        ));

        scenario.next();
        scenario.createNewRepeat();
        scenario.next();
        scenario.answer(11);

        assertThat(scenario.answerOf("/data/group[1]/next-number"), is(nullValue()));
        assertThat(scenario.answerOf("/data/group[1]/number"), is(intAnswer(11)));

        scenario.next();
        scenario.createNewRepeat();
        scenario.next();
        scenario.answer(22);

        assertThat(scenario.answerOf("/data/group[1]/number"), is(intAnswer(11)));
        assertThat(scenario.answerOf("/data/group[2]/number"), is(intAnswer(22)));

        // This assertion is false because setting the answer to 22 didn't trigger recomputation across repeat instances
        assertThat(scenario.answerOf("/data/group[1]/next-number"), is(intAnswer(22)));
        assertThat(scenario.answerOf("/data/group[2]/next-number"), is(nullValue()));

        scenario.next();
        scenario.createNewRepeat();
        scenario.next();
        scenario.answer(33);

        // This assertion is true because adding a new repeat triggered recomputation across repeat instances
        assertThat(scenario.answerOf("/data/group[1]/next-number"), is(intAnswer(22)));
        // This assertion is false because setting the answer to 33 didn't trigger recomputation across repeat instances
        assertThat(scenario.answerOf("/data/group[2]/next-number"), is(intAnswer(33)));
        assertThat(scenario.answerOf("/data/group[3]/next-number"), is(nullValue()));
    }

    @Ignore("Fails on v2.17.0 (before DAG simplification)")
    @Test
    public void issue_119_target_question_should_be_relevant() throws IOException, XFormParser.ParseException {
        // This is a translation of the XML form in the issue to our DSL with some adaptations:
        // - Explicit binds for all fields
        // - Migrated the condition field to boolean, which should be easier to understand
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("outer_trigger", "D"),
                        t("inner_trigger"),
                        t("outer",
                            t("inner",
                                t("target_question")
                            ),
                            t("inner_condition")
                        ),
                        t("end")
                    )),
                    bind("/data/outer_trigger").type("string"),
                    bind("/data/inner_trigger").type("int"),
                    bind("/data/outer").relevant("/data/outer_trigger = 'D'"),
                    bind("/data/outer/inner_condition").type("boolean").calculate("/data/inner_trigger > 10"),
                    bind("/data/outer/inner").relevant("../inner_condition"),
                    bind("/data/outer/inner/target_question").type("string")
                )
            ),
            body(
                input("inner_trigger", label("inner trigger (enter 5)")),
                input("outer_trigger", label("outer trigger (enter 'D')")),
                input("outer/inner/target_question", label("target question: i am incorrectly skipped")),
                input("end", label("this is the end of the form"))
            )));

        // Starting conditions (outer trigger is D, inner trigger is empty)
        assertThat(scenario.getAnswerNode("/data/outer"), is(relevant()));
        assertThat(scenario.getAnswerNode("/data/outer/inner_condition"), is(relevant()));
        assertThat(scenario.answerOf("/data/outer/inner_condition"), is(booleanAnswer(false)));
        assertThat(scenario.getAnswerNode("/data/outer/inner"), is(nonRelevant()));
        assertThat(scenario.getAnswerNode("/data/outer/inner/target_question"), is(nonRelevant()));

        scenario.answer("/data/inner_trigger", 15);

        assertThat(scenario.getAnswerNode("/data/outer"), is(relevant()));
        assertThat(scenario.getAnswerNode("/data/outer/inner_condition"), is(relevant()));
        assertThat(scenario.answerOf("/data/outer/inner_condition"), is(booleanAnswer(true)));
        assertThat(scenario.getAnswerNode("/data/outer/inner"), is(relevant()));
        assertThat(scenario.getAnswerNode("/data/outer/inner/target_question"), is(relevant()));

        scenario.answer("/data/outer_trigger", "A");

        assertThat(scenario.getAnswerNode("/data/outer"), is(nonRelevant()));
        assertThat(scenario.getAnswerNode("/data/outer/inner_condition"), is(nonRelevant()));
        assertThat(scenario.answerOf("/data/outer/inner_condition"), is(booleanAnswer(true)));
        assertThat(scenario.getAnswerNode("/data/outer/inner"), is(nonRelevant()));
        assertThat(scenario.getAnswerNode("/data/outer/inner/target_question"), is(relevant()));
    }
    //endregion

    //region Repeat misc
    @Test
    public void issue_135_verify_that_counts_in_inner_repeats_work_as_expected() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(
                        t("data id=\"some-form\"",
                            t("outer-count", "0"),
                            t("outer jr:template=\"\"",
                                t("inner-count", "0"),
                                t("inner jr:template=\"\"",
                                    t("some-field")
                                )
                            )
                        )
                    ),
                    bind("/data/outer-count").type("int"),
                    bind("/data/outer/inner-count").type("int"),
                    bind("/data/outer/inner/some-field").type("string")
                )
            ),
            body(
                input("/data/outer-count"),
                group("/data/outer", repeat("/data/outer", "/data/outer-count",
                    input("/data/outer/inner-count"),
                    group("/data/outer/inner", repeat("/data/outer/inner", "../inner-count",
                        input("/data/outer/inner/some-field")
                    ))
                ))
            )
        ));

        scenario.next();
        scenario.answer(2);
        scenario.next();
        scenario.next();
        scenario.answer(3);
        scenario.next();
        scenario.next();
        scenario.answer("Some field 0-0");
        scenario.next();
        scenario.next();
        scenario.answer("Some field 0-1");
        scenario.next();
        scenario.next();
        scenario.answer("Some field 0-2");
        scenario.next();
        scenario.next();
        scenario.answer(2);
        scenario.next();
        scenario.next();
        scenario.answer("Some field 1-0");
        scenario.next();
        scenario.next();
        scenario.answer("Some field 1-1");
        scenario.next();
        assertThat(scenario.countRepeatInstancesOf("/data/outer[1]/inner"), is(3));
        assertThat(scenario.countRepeatInstancesOf("/data/outer[2]/inner"), is(2));
    }

    @Test
    public void addingNestedRepeatInstance_updatesExpressionTriggeredByGenericRef_forAllRepeatInstances() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("outer jr:template=\"\"",
                            t("inner jr:template=\"\"",
                                t("count"),
                                t("some-field")
                            ),
                            t("some-field")
                        )
                    )),
                    bind("/data/outer/inner/count").type("int").calculate("count(../../inner)")
                )
            ),
            body(
                group("/data/outer", repeat("/data/outer",
                    input("/data/outer/some-field"),
                    group("/data/outer/inner", repeat("/data/outer/inner",
                        input("/data/outer/inner/some-field")
                    ))
                ))
            )));
        scenario.createNewRepeat("/data/outer");
        scenario.createNewRepeat("/data/outer[1]/inner");
        scenario.createNewRepeat("/data/outer[1]/inner");
        scenario.createNewRepeat("/data/outer[1]/inner");
        scenario.createNewRepeat("/data/outer");
        scenario.createNewRepeat("/data/outer[2]/inner");
        scenario.createNewRepeat("/data/outer[2]/inner");

        assertThat(scenario.answerOf("/data/outer[1]/inner[1]/count"), is(intAnswer(3)));
        assertThat(scenario.answerOf("/data/outer[1]/inner[2]/count"), is(intAnswer(3)));
        assertThat(scenario.answerOf("/data/outer[1]/inner[3]/count"), is(intAnswer(3)));
        assertThat(scenario.answerOf("/data/outer[2]/inner[1]/count"), is(intAnswer(2)));
        assertThat(scenario.answerOf("/data/outer[2]/inner[2]/count"), is(intAnswer(2)));
    }

    @Test
    public void addingRepeatInstance_updatesReferenceToLastInstance_usingPositionPredicate() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Some form", html(
            head(
                title("Some form"),
                model(
                    mainInstance(t("data id=\"some-form\"",
                        t("group jr:template=\"\"", t("number")),
                        t("count"),
                        t("result_1"),
                        t("result_2")
                    )),
                    bind("/data/group/number").type("int").required(),
                    bind("/data/count").type("int").calculate("count(/data/group)"),
                    bind("/data/result_1").type("int").calculate("10 + /data/group[position() = /data/count]/number"),
                    bind("/data/result_2").type("int").calculate("10 + /data/group[position() = count(../group)]/number")
                )
            ),
            body(group("/data/group", repeat("/data/group", input("/data/group/number"))))
        ));
        scenario.next();
        scenario.createNewRepeat();
        scenario.next();
        scenario.answer(10);

        assertThat(scenario.answerOf("/data/count"), is(intAnswer(1)));
        assertThat(scenario.answerOf("/data/result_1"), is(intAnswer(20)));
        assertThat(scenario.answerOf("/data/result_2"), is(intAnswer(20)));

        scenario.next();
        scenario.createNewRepeat();
        scenario.next();
        scenario.answer(20);

        assertThat(scenario.answerOf("/data/count"), is(intAnswer(2)));
        assertThat(scenario.answerOf("/data/result_1"), is(intAnswer(30)));
        // This would fail with count(/data/group) because the absolute ref would get a multiplicity
        assertThat(scenario.answerOf("/data/result_2"), is(intAnswer(30)));
    }
    //endregion

    private void assertDagEvents(List<Event> dagEvents, String... lines) {
        assertThat(dagEvents.stream().map(Event::getDisplayMessage).collect(joining("\n")), is(join("\n", lines)));
    }

    private XFormsElement buildFormForDagCyclesCheck(BindBuilderXFormsElement... binds) {
        return buildFormForDagCyclesCheck(null, binds);
    }

    private XFormsElement buildFormForDagCyclesCheck(String initialValue, BindBuilderXFormsElement... binds) {
        // Map the last part of each bind's nodeset to model fields
        // They will get an initial value if provided
        List<XFormsElement> modelFields = Stream.of(binds)
            .map(bind -> {
                String[] parts = bind.getNodeset().split("/");
                return parts[parts.length - 1];
            })
            .map(name -> {
                if (initialValue == null)
                    return t(name);
                return t(name, initialValue);
            })
            .collect(toList());

        // Build the main instance with the model fields we've just built
        XFormsElement mainInstance = mainInstance(t("data id=\"some-form\"", modelFields.toArray(new XFormsElement[]{})));

        // Build the model including the main instance we've just built and the provided binds
        List<XFormsElement> modelChildren = new LinkedList<>();
        modelChildren.add(mainInstance);
        modelChildren.addAll(Arrays.asList(binds));

        // Map each bind's nodeset to body fields (inputs)
        List<XFormsElement> inputs = Stream.of(binds)
            .map(BindBuilderXFormsElement::getNodeset)
            .map(name -> input(name))
            .collect(toList());

        // Return the complete form including model fields, binds, and body inputs
        return html(head(
            title("Some form"),
            model(modelChildren.toArray(new XFormsElement[]{}))
        ), body(inputs.toArray(new XFormsElement[]{})));
    }
}
