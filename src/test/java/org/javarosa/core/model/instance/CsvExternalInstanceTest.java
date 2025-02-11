package org.javarosa.core.model.instance;

import org.apache.commons.io.input.BOMInputStream;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.javarosa.test.ResourcePathHelper.r;
import static org.junit.Assert.assertEquals;

public class CsvExternalInstanceTest {
    private TreeElement commaSeparated;
    private TreeElement semiColonSeparated;

    @Before
    public void setUp() throws IOException {
        commaSeparated = new CsvExternalInstance().parse("id", r("external-secondary-comma-complex.csv").toString());
        semiColonSeparated = new CsvExternalInstance().parse("id", r("external-secondary-semicolon-complex.csv").toString());
    }

    @Test
    public void heading_has_no_extra_quotes() {
        assertEquals("label", commaSeparated.getChildAt(0).getChildAt(0).getName());
        assertEquals("label", semiColonSeparated.getChildAt(0).getChildAt(0).getName());
    }

    @Test
    public void heading_spaces_are_not_stripped() {
        assertEquals(" extra", commaSeparated.getChildAt(0).getChildAt(3).getName());
        assertEquals(" extra", semiColonSeparated.getChildAt(0).getChildAt(3).getName());
    }

    @Test
    public void value_has_no_extra_quotes() {
        assertEquals("A", commaSeparated.getChildAt(0).getChildAt(0).getValue().getValue());
        assertEquals("A", semiColonSeparated.getChildAt(0).getChildAt(0).getValue().getValue());
    }

    @Test
    public void quoted_string_with_comma() {
        assertEquals("121 Main St, NE", commaSeparated.getChildAt(6).getChildAt(0).getValue().getValue());
        assertEquals("121 Main St, NE", semiColonSeparated.getChildAt(6).getChildAt(0).getValue().getValue());
    }

    @Test
    public void quoted_string_with_semicolon() {
        assertEquals("text; more text", commaSeparated.getChildAt(7).getChildAt(0).getValue().getValue());
        assertEquals("text; more text", semiColonSeparated.getChildAt(7).getChildAt(0).getValue().getValue());
    }

    @Test
    public void missing_fields_replaced_with_spaces() {
        for (int fieldIndex = 1; fieldIndex < 2; fieldIndex++) {
            assertEquals("", commaSeparated.getChildAt(5).getChildAt(fieldIndex).getValue().getValue());
            assertEquals("", semiColonSeparated.getChildAt(5).getChildAt(fieldIndex).getValue().getValue());
        }
    }

    @Test
    public void value_spaces_are_not_stripped() {
        assertEquals(" b", commaSeparated.getChildAt(8).getChildAt(1).getValue().getValue());
        assertEquals(" b", semiColonSeparated.getChildAt(8).getChildAt(1).getValue().getValue());
    }

    @Test
    public void ignores_utf8_bom() throws IOException {
        BOMInputStream bomIs = new BOMInputStream(new FileInputStream(r("external-secondary-csv-bom.csv")));
        assertThat(bomIs.hasBOM(), is(true));

        TreeElement bomCsv = new CsvExternalInstance().parse("id", r("external-secondary-csv-bom.csv").toString());
        assertThat(bomCsv.getChildAt(0).getChildAt(0).getName(), is("name"));
    }

    @Test
    public void parses_utf8_characters() throws IOException {
        TreeElement bomCsv = new CsvExternalInstance().parse("id", r("external-secondary-csv-bom.csv").toString());
        assertThat(bomCsv.getChildAt(0).getChild("elevation", 0).getValue().getValue(), is("testé"));
    }
}
