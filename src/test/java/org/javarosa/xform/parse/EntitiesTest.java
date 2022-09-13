package org.javarosa.xform.parse;

import kotlin.Pair;
import org.javarosa.core.model.Entity;
import org.javarosa.core.test.Scenario;
import org.javarosa.core.util.XFormsElement;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.javarosa.core.util.BindBuilderXFormsElement.bind;
import static org.javarosa.core.util.XFormsElement.body;
import static org.javarosa.core.util.XFormsElement.head;
import static org.javarosa.core.util.XFormsElement.input;
import static org.javarosa.core.util.XFormsElement.mainInstance;
import static org.javarosa.core.util.XFormsElement.model;
import static org.javarosa.core.util.XFormsElement.t;
import static org.javarosa.core.util.XFormsElement.title;

public class EntitiesTest {

    @Test
    public void fillingFormWithoutCreate_doesNotCreateAnyEntities() throws IOException {
        Scenario scenario = Scenario.init("Entity form", XFormsElement.html(
            head(
                title("Entity form"),
                model(
                    mainInstance(
                        t("data id=\"entity-form\"",
                            t("name"),
                            t("orx:meta",
                                t("entities:entity dataset=\"people\" id=\"\"")
                            )
                        )
                    ),
                    bind("/data/name").type("string").saveTo("name")
                )
            ),
            body(
                input("/data/name")
            )
        ));

        scenario.next();
        scenario.answer("Tom Wambsgans");
        scenario.finalizeInstance();

        List<Entity> entities = scenario.getFormDef().getMainInstance().getEntities();
        assertThat(entities.size(), equalTo(0));
    }

    @Test
    public void fillingFormWithCreate_makesEntityAvailable() throws IOException {
        Scenario scenario = Scenario.init("Create entity form", XFormsElement.html(
            head(
                title("Create entity form"),
                model(
                    mainInstance(
                        t("data id=\"create-entity-form\"",
                            t("name"),
                            t("orx:meta",
                                t("entities:entity dataset=\"people\" id=\"\"",
                                    t("entities:create")
                                )
                            )
                        )
                    ),
                    bind("/data/name").type("string").saveTo("name")
                )
            ),
            body(
                input("/data/name")
            )
        ));

        scenario.next();
        scenario.answer("Tom Wambsgans");
        scenario.finalizeInstance();

        List<Entity> entities = scenario.getFormDef().getMainInstance().getEntities();
        assertThat(entities.size(), equalTo(1));
        assertThat(entities.get(0).dataset, equalTo("people"));
        assertThat(entities.get(0).fields, equalTo(asList(new Pair<>("name", "Tom Wambsgans"))));
    }

    @Test
    public void entityFormCanBeSerialized() throws IOException, DeserializationException {
        Scenario scenario = Scenario.init("Create entity form", XFormsElement.html(
            head(
                title("Create entity form"),
                model(
                    mainInstance(
                        t("data id=\"create-entity-form\"",
                            t("name"),
                            t("orx:meta",
                                t("entities:entity dataset=\"people\" id=\"\"",
                                    t("entities:create")
                                )
                            )
                        )
                    ),
                    bind("/data/name").type("string").saveTo("name")
                )
            ),
            body(
                input("/data/name")
            )
        ));

        Scenario deserializedScenario = scenario.serializeAndDeserializeForm();

        deserializedScenario.next();
        deserializedScenario.answer("Shiv Roy");
        deserializedScenario.finalizeInstance();

        List<Entity> entities = deserializedScenario.getFormDef().getMainInstance().getEntities();
        assertThat(entities.size(), equalTo(1));
        assertThat(entities.get(0).dataset, equalTo("people"));
        assertThat(entities.get(0).fields, equalTo(asList(new Pair<>("name", "Shiv Roy"))));
    }
}
