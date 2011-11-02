package net.ocheyedan.ply;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * User: blangel
 * Date: 11/2/11
 * Time: 5:02 PM
 */
public class PropertiesFileUtilTest {


    @Test
    public void store() throws IOException {

        // test null case
        assertFalse(PropertiesFileUtil.store(null, null));
        assertFalse(PropertiesFileUtil.store(null, null, false));
        assertFalse(PropertiesFileUtil.store(null, null, null, false));

        // test fnf and create false
        assertFalse(PropertiesFileUtil.store(new Properties(), null));
        assertFalse(PropertiesFileUtil.store(new Properties(), "not a file", false));

        File tmp = File.createTempFile("test", "store");
        Properties props = new Properties();
        long now = System.currentTimeMillis();
        props.put("test", String.valueOf(now));
        assertTrue(PropertiesFileUtil.store(props, tmp.getPath(), true));

        Properties loaded = new Properties();
        loaded.load(new FileInputStream(tmp));

        assertEquals(now, (long) Long.valueOf(loaded.getProperty("test")));
    }

}
