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

package org.javarosa.benchmarks;


import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.test.Scenario;
import org.javarosa.xform.parse.XFormParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static org.javarosa.benchmarks.BenchmarkUtils.dryRun;
import static org.javarosa.benchmarks.BenchmarkUtils.prepareAssets;
import static org.javarosa.benchmarks.ChildVaccinationBenchmark.Sex.FEMALE;
import static org.javarosa.benchmarks.ChildVaccinationBenchmark.Sex.MALE;
import static org.javarosa.benchmarks.ChildVaccinationBenchmark.Vaccines.DIPHTERIA;
import static org.javarosa.benchmarks.ChildVaccinationBenchmark.Vaccines.DIPHTERIA_AND_MEASLES;
import static org.javarosa.benchmarks.ChildVaccinationBenchmark.Vaccines.DIPHTERIA_FIRST;
import static org.javarosa.benchmarks.ChildVaccinationBenchmark.Vaccines.DIPHTERIA_FIRST_AND_MEASLES;
import static org.javarosa.benchmarks.ChildVaccinationBenchmark.Vaccines.MEASLES;
import static org.javarosa.benchmarks.ChildVaccinationBenchmark.Vaccines.NONE;
import static org.javarosa.test.Scenario.getRef;
import static org.javarosa.test.Scenario.init;

public class ChildVaccinationBenchmark {

    public static void main(String[] args) {
        dryRun(ChildVaccinationBenchmark.class);
    }

    @State(Scope.Thread)
    public static class ChildVaccinationState {
        public Scenario scenario;
        private LocalDate today;
        private TreeReference finalFlatRef;
        private TreeReference nextChildNoMotherRef;
        private TreeReference nextChildRef;
        private TreeReference notEligNoteRef;
        private TreeReference vaccinationMeaslesRef;
        private TreeReference vaccinationPenta3Ref;
        private TreeReference vaccinationPenta1Ref;
        private List<TreeReference> endOfVisitRefs;

        @Setup(Level.Trial)
        public void initialize() throws XFormParser.ParseException {
            Path formFile = prepareAssets("child_vaccination_VOL_tool_v12.xml").resolve("child_vaccination_VOL_tool_v12.xml");
            vaccinationPenta1Ref = getRef("/data/household/child_repeat/penta1");
            vaccinationPenta3Ref = getRef("/data/household/child_repeat/penta3");
            vaccinationMeaslesRef = getRef("/data/household/child_repeat/mcv1");
            notEligNoteRef = getRef("/data/household/child_repeat/not_elig_note");
            nextChildRef = getRef("/data/household/child_repeat/nextChild");
            nextChildNoMotherRef = getRef("/data/household/child_repeat/nextChild_no_mother");
            finalFlatRef = getRef("/data/household/finalflat");
            endOfVisitRefs = Arrays.asList(nextChildRef, finalFlatRef, getRef("/data/household/child_repeat"));
            today = LocalDate.now();
            scenario = init(formFile.toFile());
        }
    }

    @Benchmark
    public void run_1_times(ChildVaccinationState state, Blackhole bh) {
        doRuns(state, bh, 1);
    }

    @Benchmark
    public void run_2_times(ChildVaccinationState state, Blackhole bh) {
        doRuns(state, bh, 2);
    }

    @Benchmark
    public void run_3_times(ChildVaccinationState state, Blackhole bh) {
        doRuns(state, bh, 3);
    }

    private void doRuns(ChildVaccinationState state, Blackhole bh, int numberOfRuns) {
        for (int i = 0; i < numberOfRuns; i++) {
            state.scenario.newInstance();
            answerAllQuestions(state, bh);
        }
    }

    private void answerAllQuestions(ChildVaccinationState state, Blackhole bh) {
        // region Answer questions about the building

        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("multi"));
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("1.234 5.678 0 2.3"));
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("Some building"));
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("Some address, some location"));

        // endregion

        // region Answer all household repeats

        // Create all possible permutations of children combining
        // all health record types, meaningful ages, ways to define age,
        // and vaccination sets, which amounts to 18 households and 108 children
        List<List<Consumer<Integer>>> households = HealthRecord.all().stream()
            .flatMap(healthRecord -> buildHouseholdChildren(state, bh, healthRecord).stream())
            .collect(toList());

        for (int i = 0; i < households.size(); i++) {
            List<Consumer<Integer>> children = households.get(i);
            answerHousehold(state, bh, i, children);
            bh.consume(state.scenario.next());
            if (i + 1 < households.size()) {
                bh.consume(state.scenario.answer("no"));
                bh.consume(state.scenario.next());
            } else {
                bh.consume(state.scenario.answer("yes"));
            }
        }

        state.scenario.trace("END HOUSEHOLDS");

        // endregion

        // region Go to the end of the form

        bh.consume(state.scenario.next());
        bh.consume(state.scenario.next());

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
    private List<List<Consumer<Integer>>> buildHouseholdChildren(ChildVaccinationState state, Blackhole bh, HealthRecord healthRecord) {
        return Arrays.asList(
            buildChildrenWithLocalDates(state, bh, 23, healthRecord),
            buildChildrenWithIntegers(state, bh, 23, healthRecord),
            buildChildrenWithLocalDates(state, bh, 6, healthRecord),
            buildChildrenWithIntegers(state, bh, 6, healthRecord),
            buildChildrenWithLocalDates(state, bh, 3, healthRecord),
            buildChildrenWithIntegers(state, bh, 3, healthRecord)
        );
    }

    private List<Consumer<Integer>> buildChildrenWithIntegers(ChildVaccinationState state, Blackhole bh, int ageInMonths, HealthRecord healthRecord) {
        return Arrays.asList(
            answerChild(state, bh, healthRecord, ageInMonths, NONE, MALE),
            answerChild(state, bh, healthRecord, ageInMonths, DIPHTERIA_FIRST, FEMALE),
            answerChild(state, bh, healthRecord, ageInMonths, DIPHTERIA, MALE),
            answerChild(state, bh, healthRecord, ageInMonths, MEASLES, FEMALE),
            answerChild(state, bh, healthRecord, ageInMonths, DIPHTERIA_FIRST_AND_MEASLES, MALE),
            answerChild(state, bh, healthRecord, ageInMonths, DIPHTERIA_AND_MEASLES, FEMALE)
        );
    }

    private List<Consumer<Integer>> buildChildrenWithLocalDates(ChildVaccinationState state, Blackhole bh, int ageInMonths, HealthRecord healthRecord) {
        LocalDate dob = state.today.minusMonths(ageInMonths);
        return Arrays.asList(
            answerChild(state, bh, healthRecord, dob, NONE, FEMALE),
            answerChild(state, bh, healthRecord, dob, DIPHTERIA_FIRST, MALE),
            answerChild(state, bh, healthRecord, dob, DIPHTERIA, FEMALE),
            answerChild(state, bh, healthRecord, dob, MEASLES, MALE),
            answerChild(state, bh, healthRecord, dob, DIPHTERIA_FIRST_AND_MEASLES, FEMALE),
            answerChild(state, bh, healthRecord, dob, DIPHTERIA_AND_MEASLES, MALE)
        );
    }

    private void answerHousehold(ChildVaccinationState state, Blackhole bh, int number, List<Consumer<Integer>> children) {
        // region Answer info about the household

        bh.consume(state.scenario.next());
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer(number));
        // Does someone answer the door?
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("yes"));
        // Is there an adult
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("yes"));
        // Do children under 2 live in the house?
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("yes"));
        // What's the mother's or caregiver's name
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("Foo"));
        // Is the mother or caregiver present?
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("yes"));
        // Give consent
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("yes"));

        // endregion

        // How many children under 2?
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer(children.size()));

        for (int i = 0; i < children.size(); i++)
            children.get(i).accept(i);
    }


    enum Vaccines {
        NONE(false, false, false),
        DIPHTERIA_FIRST(true, false, false),
        DIPHTERIA(true, true, false),
        MEASLES(false, false, true),
        DIPHTERIA_FIRST_AND_MEASLES(true, false, true),
        DIPHTERIA_AND_MEASLES(true, true, true);


        private final boolean diphteriaFirst;
        private final boolean diphteriaThird;
        private final boolean measles;

        Vaccines(boolean diphteriaFirst, boolean diphteriaThird, boolean measles) {
            this.diphteriaFirst = diphteriaFirst;
            this.diphteriaThird = diphteriaThird;
            this.measles = measles;
        }

        void visit(ChildVaccinationState state, Blackhole bh) {
            // Answer questions until there's no more vaccination related questions
            while (!(state.endOfVisitRefs.contains(state.scenario.nextRef().genericize()))) {
                bh.consume(state.scenario.next());
                if (state.scenario.refAtIndex().genericize().equals(state.vaccinationPenta1Ref))
                    bh.consume(state.scenario.answer(diphteriaFirst ? "yes" : "no"));
                else if (state.scenario.refAtIndex().genericize().equals(state.vaccinationPenta3Ref))
                    bh.consume(state.scenario.answer(diphteriaThird ? "yes" : "no"));
                else if (state.scenario.refAtIndex().genericize().equals(state.vaccinationMeaslesRef))
                    bh.consume(state.scenario.answer(measles ? "yes" : "no"));
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

        void visit(ChildVaccinationState state, Blackhole bh) {
            if (this == HEALTH_HANDBOOK) {
                bh.consume(state.scenario.next());
                bh.consume(state.scenario.answer("yes"));
            } else if (this == VACCINATION_CARD) {
                bh.consume(state.scenario.next());
                bh.consume(state.scenario.answer("no"));
                bh.consume(state.scenario.next());
                bh.consume(state.scenario.answer("yes"));
            } else if (this == HEALTH_CLINIC) {
                bh.consume(state.scenario.next());
                bh.consume(state.scenario.answer("no"));
                bh.consume(state.scenario.next());
                bh.consume(state.scenario.answer("no"));
                bh.consume(state.scenario.next());
                bh.consume(state.scenario.answer("yes"));
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

    private Consumer<Integer> answerChild(ChildVaccinationState state, Blackhole bh, HealthRecord healthRecord, LocalDate dob, Vaccines vaccines, Sex sex) {
        return i -> {
            int ageInMonths = dob.until(state.today).getMonths();
            String name = String.format("CHILD %d - Age %d months - %s", i, ageInMonths, sex.getName());
            bh.consume(state.scenario.next());
            bh.consume(state.scenario.next());
            bh.consume(state.scenario.answer(name));
            healthRecord.visit(state, bh);
            bh.consume(state.scenario.next());
            bh.consume(state.scenario.answer(sex.getName()));
            answerDateOfBirth(state, bh, dob);
            if (state.scenario.nextRef().genericize().equals(state.notEligNoteRef))
                bh.consume(state.scenario.next());
            else if (state.scenario.nextRef().genericize().equals(state.vaccinationPenta1Ref))
                vaccines.visit(state, bh);

            if (Arrays.asList(state.nextChildRef, state.nextChildNoMotherRef).contains(state.scenario.nextRef().genericize()))
                bh.consume(state.scenario.next());
            else if (!state.scenario.nextRef().genericize().equals(state.finalFlatRef))
                throw new RuntimeException("Unexpected next ref " + state.scenario.nextRef().toString(true, true) + " at index");
        };
    }

    private Consumer<Integer> answerChild(ChildVaccinationState state, Blackhole bh, HealthRecord healthRecord, int ageInMonths, Vaccines vaccines, Sex sex) {
        return i -> {
            String name = String.format("CHILD %d - Age %d months - %s", i, ageInMonths, sex.getName());
            bh.consume(state.scenario.next());
            bh.consume(state.scenario.next());
            bh.consume(state.scenario.answer(name));
            healthRecord.visit(state, bh);
            bh.consume(state.scenario.next());
            bh.consume(state.scenario.answer(sex.getName()));
            answerAgeInMonths(state, bh, ageInMonths);
            if (state.scenario.nextRef().genericize().equals(state.vaccinationPenta1Ref))
                vaccines.visit(state, bh);

            if (Arrays.asList(state.nextChildRef, state.nextChildNoMotherRef).contains(state.scenario.nextRef().genericize()))
                bh.consume(state.scenario.next());
            else if (!state.scenario.nextRef().genericize().equals(state.finalFlatRef))
                throw new RuntimeException("Unexpected next ref " + state.scenario.nextRef().toString(true, true) + " at index");
        };
    }

    private void answerDateOfBirth(ChildVaccinationState state, Blackhole bh, LocalDate dob) {
        // Is DoB known?
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("yes"));
        // Year in DoB
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer(dob.getYear()));
        // Month in DoB
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer(dob.getMonthValue()));
        // Day in DoB
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer(dob.getDayOfMonth()));
    }

    private void answerAgeInMonths(ChildVaccinationState state, Blackhole bh, int ageInMonths) {
        // Is DoB known?
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer("no"));
        // Age in months
        bh.consume(state.scenario.next());
        bh.consume(state.scenario.answer(ageInMonths));
    }

}
