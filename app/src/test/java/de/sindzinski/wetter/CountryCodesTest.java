package de.sindzinski.wetter;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Created by steffen on 12.02.17.
 */
public class CountryCodesTest {

    @Test
    public void testGetCode() {
        assertEquals("DE", CountryCodes.getCode("Germany"));
        assertThat(CountryCodes.getCode("Germany"),  is( equalTo("DE")));
    }

}