package org.javarosa.xpath.expr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.javarosa.core.model.data.GeoPointData;
import org.junit.Test;

public class XPathFuncExprGeoPointDataTest {
    
    @Test
    public void testWithGeoPointData() {
        GeoPointData d = new GeoPointData(new double[]{1.22, 2.33, 3.33, 0.01});
        
        assertEquals(0.01, XPathFuncExpr.toNumeric(d), 0);
        assertEquals("1.22 2.33 3.33 0.01", XPathFuncExpr.toString(d));
        assertTrue(XPathFuncExpr.toBoolean(d));
        
        d = new GeoPointData(new double[] {0.0, 0.0, 0.0});
        assertEquals(9999999.0, XPathFuncExpr.toNumeric(d), 0.0);
        assertFalse(XPathFuncExpr.toBoolean(d));
    }

}
