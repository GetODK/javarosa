package org.javarosa.core.model.data.test;

import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.test.Scenario;
import org.javarosa.xform.parse.XFormParser;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.javarosa.test.BindBuilderXFormsElement.bind;
import static org.javarosa.test.XFormsElement.body;
import static org.javarosa.test.XFormsElement.head;
import static org.javarosa.test.XFormsElement.html;
import static org.javarosa.test.XFormsElement.input;
import static org.javarosa.test.XFormsElement.mainInstance;
import static org.javarosa.test.XFormsElement.model;
import static org.javarosa.test.XFormsElement.t;
import static org.javarosa.test.XFormsElement.title;

public class GeoPointDataTests {
    @Test
    public void getDisplayText_returnsSpaceSeparatedComponents() {
        GeoPointData point = new GeoPointData(new double[]{0, 1, 2, 3});

        assertThat(point.getDisplayText(), is("0.0 1.0 2.0 3.0"));
    }

    @Test
    public void getDisplayText_whenAllComponentsAreZero_returnsEmptyString() {
        GeoPointData zeroedOut = new GeoPointData(new double[]{0, 0, 0, 0});

        assertThat(zeroedOut.getDisplayText(), is(""));
    }

    @Test
    public void getDisplayText_whenAccuracyOmitted_HasThreeComponents() {
        GeoPointData point = new GeoPointData(new double[]{2.3, 7.3, 3.2});

        assertThat(point.getDisplayText(), is("2.3 7.3 3.2"));
    }

    @Test
    public void missingAccuracy_isNotTreatedAs0() throws IOException, XFormParser.ParseException {
        Scenario scenario = Scenario.init("Missing accuracy", html(
            head(
                title("Missing accuracy"),
                model(
                    mainInstance(
                        t("data id=\"missing-accuracy\"",
                            t("q1"),
                            t("accuracy_rounded"),
                            t("note")
                        )),
                    bind("/data/q1").type("geopoint"),
                    bind("/data/accuracy_rounded").calculate("round(selected-at(/data/q1, 3), 2)"),
                    bind("/data/note").relevant("/data/accuracy_rounded = 0"))),
            body(
                input("/data/q1"),
                input("/data/note")
            )
        ));

        scenario.answer("/data/q1","1.234 5.678");
        assertThat(scenario.getAnswerNode("/data/note").isRelevant(), is(false));

        scenario.answer("/data/q1", "1.234 5.678 0 0");
        assertThat(scenario.getAnswerNode("/data/note").isRelevant(), is(true));
    }
}
