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

package org.javarosa.smoketests;


import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.test.Scenario;
import org.javarosa.xform.parse.XFormParser;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.javarosa.smoketests.ChildVaccinationTest.Sex.FEMALE;
import static org.javarosa.smoketests.ChildVaccinationTest.Sex.MALE;
import static org.javarosa.smoketests.ChildVaccinationTest.Vaccines.DIPHTERIA;
import static org.javarosa.smoketests.ChildVaccinationTest.Vaccines.DIPHTERIA_AND_MEASLES;
import static org.javarosa.smoketests.ChildVaccinationTest.Vaccines.DIPHTERIA_FIRST;
import static org.javarosa.smoketests.ChildVaccinationTest.Vaccines.DIPHTERIA_FIRST_AND_MEASLES;
import static org.javarosa.smoketests.ChildVaccinationTest.Vaccines.MEASLES;
import static org.javarosa.smoketests.ChildVaccinationTest.Vaccines.NONE;
import static org.javarosa.test.Scenario.getRef;
import static org.junit.Assert.fail;

public class ChildVaccinationTest {

    public static final TreeReference DOB_DAY_MONTH_TYPE_1_REF = getRef("/data/household/child_repeat/dob_day_1");
    public static final TreeReference DOB_DAY_MONTH_TYPE_2_REF = getRef("/data/household/child_repeat/dob_day_2");
    public static final TreeReference DOB_DAY_MONTH_TYPE_3_REF = getRef("/data/household/child_repeat/dob_day_3");
    public static final TreeReference DOB_DAY_MONTH_TYPE_4_REF = getRef("/data/household/child_repeat/dob_day_4");
    public static final TreeReference DOB_AGE_IN_MONTHS_REF = getRef("/data/household/child_repeat/age_months");
    public static final TreeReference VACCINATION_PENTA1_REF = getRef("/data/household/child_repeat/penta1");
    public static final TreeReference VACCINATION_PENTA3_REF = getRef("/data/household/child_repeat/penta3");
    public static final TreeReference VACCINATION_MEASLES_REF = getRef("/data/household/child_repeat/mcv1");
    public static final TreeReference CHILD_REPEAT_REF = getRef("/data/household/child_repeat");
    public static final TreeReference NOT_ELIG_NOTE_REF = getRef("/data/household/child_repeat/not_elig_note");
    public static final TreeReference NEXT_CHILD_REF = getRef("/data/household/child_repeat/nextChild");
    public static final TreeReference NEXT_CHILD_NO_MOTHER_REF = getRef("/data/household/child_repeat/nextChild_no_mother");
    public static final TreeReference NEW_HOUSEHOLD_REPEAT_JUNCTION_REF = getRef("/data/household");
    public static final TreeReference FINAL_FLAT_REF = getRef("/data/household/finalflat");
    public static final TreeReference FINISHED_FORM_REF = getRef("/data/household/finished2");
    public static final LocalDate TODAY = LocalDate.now();

    @Test
    public void smoke_test() throws XFormParser.ParseException {
        Scenario scenario = Scenario.init("child_vaccination_VOL_tool_v12.xml");

        // region Answer questions about the building

        scenario.next();
        scenario.answer("multi");
        scenario.next();
        scenario.next();
        scenario.answer("1.234 5.678 0 2.3"); // an accuracy of 0m or greater than 5m makes a second geopoint question relevant
        scenario.next();
        scenario.answer("Some building");
        scenario.next();
        scenario.answer("Some address, some location");

        // endregion

        // region Answer all household repeats

        // Create all possible permutations of children combining
        // all health record types, meaningful ages, ways to define age,
        // and vaccination sets, which amounts to 18 households and 108 children
        List<List<Consumer<Integer>>> households = HealthRecord.all().stream()
            .flatMap(healthRecord -> buildHouseholdChildren(scenario, healthRecord).stream())
            .collect(toList());

        for (int i = 0; i < households.size(); i++) {
            List<Consumer<Integer>> children = households.get(i);
            assertThat(scenario.nextRef().genericize(), is(NEW_HOUSEHOLD_REPEAT_JUNCTION_REF));
            answerHousehold(scenario, i, children);
            // We just want to make sure that we are in a valid position without
            // going into more detail. Due to the conditional nature of this
            // form, it would be too complex to describe that in a test with all
            // the precission in a test like this one that will follow all possible
            // branches.
            assertThat(scenario.refAtIndex().genericize(), anyOf(
                // Either we stopped after filling the age with a decomposed date
                is(DOB_DAY_MONTH_TYPE_1_REF),
                is(DOB_DAY_MONTH_TYPE_2_REF),
                is(DOB_DAY_MONTH_TYPE_3_REF),
                is(DOB_DAY_MONTH_TYPE_4_REF),
                // Or we stopped after filling the age in months
                is(DOB_AGE_IN_MONTHS_REF),
                // Or we stopped after answering any of the vaccination questions
                is(VACCINATION_PENTA1_REF),
                is(VACCINATION_PENTA3_REF),
                is(VACCINATION_MEASLES_REF),
                // Or we answered all questions
                is(NEXT_CHILD_REF)
            ));
            scenario.next();
            assertThat(scenario.refAtIndex().genericize(), is(FINAL_FLAT_REF));
            if (i + 1 < households.size()) {
                scenario.answer("no");
                scenario.next();
            } else {
                scenario.answer("yes");
            }
        }

        scenario.trace("END HOUSEHOLDS");

        // endregion

        // region Go to the end of the form

        scenario.next();
        assertThat(scenario.refAtIndex().genericize(), is(FINISHED_FORM_REF));
        scenario.next();

        // endregion
    }

    /**
     * Builds every possible combination of:
     * <ul>
     *     <li>Meaningful ages: 23, 6 and 3 months</li>
     *     <li>Supported ways of defining the age: a decomposed date of birth or
     *     a number of months</li>
     *     <li>Vaccination records: No vaccines, first and third diphteria shot, measles shot</li>
     * </ul>
     * <p>
     * These combinations ensure that all critical paths for the provided health
     * record type are taken while filling out a child group.
     * <p>
     * Since the form's limit to the number of children per household is 10, this method will return
     * a list entry per every 6 children (the possible permutations of vaccine sets).
     */
    private List<List<Consumer<Integer>>> buildHouseholdChildren(Scenario scenario, HealthRecord healthRecord) {
        return Arrays.asList(
            buildChildrenWithLocalDates(scenario, 23, healthRecord),
            buildChildrenWithIntegers(scenario, 23, healthRecord),
            buildChildrenWithLocalDates(scenario, 6, healthRecord),
            buildChildrenWithIntegers(scenario, 6, healthRecord),
            buildChildrenWithLocalDates(scenario, 3, healthRecord),
            buildChildrenWithIntegers(scenario, 3, healthRecord)
        );
    }

    private List<Consumer<Integer>> buildChildrenWithIntegers(Scenario scenario, int ageInMonths, HealthRecord healthRecord) {
        return Arrays.asList(
            answerChild(scenario, healthRecord, ageInMonths, NONE, MALE),
            answerChild(scenario, healthRecord, ageInMonths, DIPHTERIA_FIRST, FEMALE),
            answerChild(scenario, healthRecord, ageInMonths, DIPHTERIA, MALE),
            answerChild(scenario, healthRecord, ageInMonths, MEASLES, FEMALE),
            answerChild(scenario, healthRecord, ageInMonths, DIPHTERIA_FIRST_AND_MEASLES, MALE),
            answerChild(scenario, healthRecord, ageInMonths, DIPHTERIA_AND_MEASLES, FEMALE)
        );
    }

    private List<Consumer<Integer>> buildChildrenWithLocalDates(Scenario scenario, int ageInMonths, HealthRecord healthRecord) {
        LocalDate dob = TODAY.minusMonths(ageInMonths);
        return Arrays.asList(
            answerChild(scenario, healthRecord, dob, NONE, FEMALE),
            answerChild(scenario, healthRecord, dob, DIPHTERIA_FIRST, MALE),
            answerChild(scenario, healthRecord, dob, DIPHTERIA, FEMALE),
            answerChild(scenario, healthRecord, dob, MEASLES, MALE),
            answerChild(scenario, healthRecord, dob, DIPHTERIA_FIRST_AND_MEASLES, FEMALE),
            answerChild(scenario, healthRecord, dob, DIPHTERIA_AND_MEASLES, MALE)
        );
    }

    private void answerHousehold(Scenario scenario, int number, List<Consumer<Integer>> children) {
        // region Answer info about the household

        scenario.trace("HOUSEHOLD " + number);
        scenario.next();
        scenario.next();
        scenario.answer(number);
        // Does someone answer the door?
        scenario.next();
        scenario.answer("yes");
        // Is there an adult
        scenario.next();
        scenario.answer("yes");
        // Do children under 2 live in the house?
        scenario.next();
        scenario.answer("yes");
        // What's the mother's or caregiver's name
        scenario.next();
        scenario.answer("Foo");
        // Is the mother or caregiver present?
        scenario.next();
        scenario.answer("yes");
        // Give consent
        scenario.next();
        scenario.answer("yes");

        // endregion

        // How many children under 2?
        scenario.next();
        scenario.answer(children.size());

        for (int i = 0; i < children.size(); i++)
            children.get(i).accept(i);

        scenario.trace("END CHILDREN");
    }


    enum Vaccines {
        NONE(false, false, false),
        DIPHTERIA_FIRST(true, false, false),
        DIPHTERIA(true, true, false),
        MEASLES(false, false, true),
        DIPHTERIA_FIRST_AND_MEASLES(true, false, true),
        DIPHTERIA_AND_MEASLES(true, true, true);

        static List<TreeReference> END_OF_VISIT_REFS = Arrays.asList(NEXT_CHILD_REF, FINAL_FLAT_REF, CHILD_REPEAT_REF);

        private final boolean diphteriaFirst;
        private final boolean diphteriaThird;
        private final boolean measles;

        Vaccines(boolean diphteriaFirst, boolean diphteriaThird, boolean measles) {
            this.diphteriaFirst = diphteriaFirst;
            this.diphteriaThird = diphteriaThird;
            this.measles = measles;
        }

        void visit(Scenario scenario) {
            // Answer questions until there's no more vaccination related questions
            while (!(END_OF_VISIT_REFS.contains(scenario.nextRef().genericize()))) {
                scenario.next();
                if (scenario.refAtIndex().genericize().equals(VACCINATION_PENTA1_REF))
                    scenario.answer(diphteriaFirst ? "yes" : "no");
                else if (scenario.refAtIndex().genericize().equals(VACCINATION_PENTA3_REF))
                    scenario.answer(diphteriaThird ? "yes" : "no");
                else if (scenario.refAtIndex().genericize().equals(VACCINATION_MEASLES_REF))
                    scenario.answer(measles ? "yes" : "no");
            }
        }
    }

    enum HealthRecord {
        HEALTH_HANDBOOK,
        VACCINATION_CARD,
        HEALTH_CLINIC;

        public static List<HealthRecord> all() {
            return Arrays.asList(values());
        }

        void visit(Scenario scenario) {
            if (this == HEALTH_HANDBOOK) {
                scenario.next();
                scenario.answer("yes");
            } else if (this == VACCINATION_CARD) {
                scenario.next();
                scenario.answer("no");
                scenario.next();
                scenario.answer("yes");
            } else if (this == HEALTH_CLINIC) {
                scenario.next();
                scenario.answer("no");
                scenario.next();
                scenario.answer("no");
                scenario.next();
                scenario.answer("yes");
            }
        }
    }

    enum Sex {
        FEMALE("female"), MALE("male");

        private final String name;

        Sex(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private Consumer<Integer> answerChild(Scenario scenario, HealthRecord healthRecord, LocalDate dob, Vaccines vaccines, Sex sex) {
        return i -> {
            int ageInMonths = dob.until(TODAY).getMonths();
            String name = String.format("CHILD %d - Age %d months - %s", i, ageInMonths, sex.getName());
            scenario.trace(name);
            scenario.next();
            scenario.next();
            scenario.answer(name);
            healthRecord.visit(scenario);
            scenario.next();
            scenario.answer(sex.getName());
            answerDateOfBirth(scenario, dob);
            if (scenario.nextRef().genericize().equals(NOT_ELIG_NOTE_REF))
                scenario.next();
            else if (scenario.nextRef().genericize().equals(VACCINATION_PENTA1_REF))
                vaccines.visit(scenario);

            if (Arrays.asList(NEXT_CHILD_REF, NEXT_CHILD_NO_MOTHER_REF).contains(scenario.nextRef().genericize()))
                scenario.next();
            else if (!scenario.nextRef().genericize().equals(FINAL_FLAT_REF))
                fail("Unexpected next ref " + scenario.nextRef().toString(true, true) + " at index");
        };
    }

    private Consumer<Integer> answerChild(Scenario scenario, HealthRecord healthRecord, int ageInMonths, Vaccines vaccines, Sex sex) {
        return i -> {
            String name = String.format("CHILD %d - Age %d months - %s", i, ageInMonths, sex.getName());
            scenario.trace(name);
            scenario.next();
            scenario.next();
            scenario.answer(name);
            healthRecord.visit(scenario);
            scenario.next();
            scenario.answer(sex.getName());
            answerAgeInMonths(scenario, ageInMonths);
            if (scenario.nextRef().genericize().equals(VACCINATION_PENTA1_REF))
                vaccines.visit(scenario);

            if (Arrays.asList(NEXT_CHILD_REF, NEXT_CHILD_NO_MOTHER_REF).contains(scenario.nextRef().genericize()))
                scenario.next();
            else if (!scenario.nextRef().genericize().equals(FINAL_FLAT_REF))
                fail("Unexpected next ref " + scenario.nextRef().toString(true, true) + " at index");
        };
    }

    private void answerDateOfBirth(Scenario scenario, LocalDate dob) {
        // Is DoB known?
        scenario.next();
        scenario.answer("yes");
        // Year in DoB
        scenario.next();
        scenario.answer(dob.getYear());
        // Month in DoB
        scenario.next();
        scenario.answer(dob.getMonthValue());
        // Day in DoB
        scenario.next();
        scenario.answer(dob.getDayOfMonth());
    }

    private void answerAgeInMonths(Scenario scenario, int ageInMonths) {
        // Is DoB known?
        scenario.next();
        scenario.answer("no");
        // Age in months
        scenario.next();
        scenario.answer(ageInMonths);
    }

}
