/**
 * VStar: a statistical analysis tool for variable star data.
 * Copyright (C) 2009  AAVSO (http://www.aavso.org/)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */
package org.aavso.tools.vstar.input.text;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.List;

import junit.framework.TestCase;

import org.aavso.tools.vstar.data.SeriesType;
import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.data.ValidationType;
import org.aavso.tools.vstar.data.validation.SimpleTextFormatValidator;
import org.aavso.tools.vstar.exception.ObservationValidationError;
import org.aavso.tools.vstar.exception.ObservationValidationWarning;
import org.aavso.tools.vstar.input.AbstractObservationRetriever;
import org.aavso.tools.vstar.ui.mediator.NewStarType;

import com.csvreader.CsvReader;

/**
 * This is a unit test for TextFormatObservationReader.
 * 
 * It contains tests for valid and invalid test data.
 * 
 * The format is for each line is: JD MAG [UNCERTAINTY] [OBSCODE] [VALFLAG]
 */
public class TextFormatObservationReaderTest extends TestCase {

	/**
	 * Constructor
	 * 
	 * @param name
	 */
	public TextFormatObservationReaderTest(String name) {
		super(name);
	}

	// Tests of valid simple text format.

	public void testSimpleValidJulianDayAndMagTSV() {
		commonValidJulianDayAndMagTest("2450001.5\t10.0\n", "\t");
	}

	public void testSimpleValidJulianDayAndMagCSV() {
		commonValidJulianDayAndMagTest("2450001.5,10.0\n", ",");
	}

	public void testSimpleValidFullObservationValflagDiscrepantTSV() {
		ValidObservation ob = commonValidJulianDayAndMagTest(
				"2450001.5\t10.0\t0.1\tDJB\tD\n", "\t");
		assertEquals(0.1, ob.getMagnitude().getUncertainty());
		assertEquals("DJB", ob.getObsCode());
		assertEquals(ValidationType.DISCREPANT, ob.getValidationType());
		assertTrue(ob.isDiscrepant());
	}

	public void testSimpleValidFullObservationValflagUnvalidatedTSV() {
		ValidObservation ob = commonValidJulianDayAndMagTest(
				"2450001.5\t10.0\t0.1\tDJB\tU\n", "\t");
		assertEquals(0.1, ob.getMagnitude().getUncertainty());
		assertEquals("DJB", ob.getObsCode());
		assertEquals(ValidationType.UNVALIDATED, ob.getValidationType());
	}

	public void testSimpleValidAllButUncertaintyTSV() {
		ValidObservation ob = commonValidJulianDayAndMagTest(
				"2450001.5\t10.0\t\tDJB\tD\n", "\t");
		assertEquals(0.0, ob.getMagnitude().getUncertainty());
		assertEquals("DJB", ob.getObsCode());
		assertTrue(ob.isDiscrepant());
	}

	public void testSimpleValidAllButUncertaintyAndValflagTSV() {
		ValidObservation ob = commonValidJulianDayAndMagTest(
				"2450001.5\t10.0\t\tDJB\n", "\t");
		assertEquals(0.0, ob.getMagnitude().getUncertainty());
		assertEquals("DJB", ob.getObsCode());
		assertTrue(!ob.isDiscrepant());
	}

	public void testSimpleValidAllButUncertaintyAndValflagCSV() {
		ValidObservation ob = commonValidJulianDayAndMagTest(
				"2450001.5,10.0,,DJB\n", ",");
		assertEquals(0.0, ob.getMagnitude().getUncertainty());
		assertEquals("DJB", ob.getObsCode());
		assertTrue(!ob.isDiscrepant());
	}

	public void testSimpleValidMultipleLines() {
		StringBuffer lines = new StringBuffer();
		lines.append("2450001.5\t10.0\n");
		lines.append("2430002.0\t2.0");

		List<ValidObservation> obs = commonValidTest(lines.toString(), "\t", "");

		assertTrue(obs.size() == 2);

		ValidObservation ob0 = obs.get(0);
		assertEquals(2430002.0, ob0.getDateInfo().getJulianDay());

		ValidObservation ob1 = obs.get(1);
		assertEquals(2450001.5, ob1.getDateInfo().getJulianDay());
	}

	public void testSimpleValidOutOfOrderTabSeparatedMultipleLines() {
		StringBuffer lines = new StringBuffer();
		lines.append("24550001 3.2 0.2\n");
		lines.append("24550000 4.2 0.1\n");
		lines.append("24550002 2.2 0.3");

		List<ValidObservation> obs = commonValidTest(lines.toString(), " ", "");

		assertTrue(obs.size() == 3);

		double[][] expected = { { 24550000, 4.2, 0.1 }, { 24550001, 3.2, 0.2 },
				{ 24550002, 2.2, 0.3 } };

		int i = 0;
		for (ValidObservation ob : obs) {
			assertEquals(expected[i][0], ob.getJD());
			assertEquals(expected[i][1], ob.getMag());
			assertEquals(expected[i][2], ob.getMagnitude().getUncertainty());
			i++;
		}
	}

	// Tests of valid AAVSO Download format.

	// Note: This test currently fails (2.16.3) yet loading a TSV file with the
	// same lines works as expected. Why?
	public void testAAVSODownloadTSV1() {
		StringBuffer lines = new StringBuffer();
		lines.append("2454531.66261	8.441			V	FOO		89	92	80320		No	1.143	G	8.936			W UMA		STD			\n");
		lines.append("2454531.66346	9.283			B	FOO		89	92	80320		No	1.143	G	9.958			W UMA		STD			\n");

		List<ValidObservation> obs = commonValidTest(lines.toString(), "\t", "");

		assertTrue(obs.size() == 2);

		// A few checks.
		ValidObservation ob0 = obs.get(0);
		assertEquals(2454531.66261, ob0.getDateInfo().getJulianDay());
		assertEquals(SeriesType.Johnson_V, ob0.getBand());
		assertEquals("W UMA", ob0.getName());
	}

	// Note: This test currently fails (2.16.3) yet loading a CSV file with the
	// same lines works as expected. Why?
	public void testAAVSODownloadCSV1() {
		StringBuffer lines = new StringBuffer();
		lines.append("2454531.66261,8.441,,,V,FOO,,89,92,80320,,No,1.143,P,8.936,,,W UMA,,STD,,\n");
		lines.append("2454531.66346,9.283,,,B,FOO,,89,92,80320,,No,1.143,P,9.958,,,W UMA,,STD,,\n");

		List<ValidObservation> obs = commonValidTest(lines.toString(), ",", "");

		assertTrue(obs.size() == 2);

		// A few checks.
		ValidObservation ob0 = obs.get(0);
		assertEquals(2454531.66261, ob0.getDateInfo().getJulianDay());
		assertEquals(SeriesType.Johnson_V, ob0.getBand());
		assertEquals("W UMA", ob0.getName());
	}

	public void testLineWithValidationFlagV() {

		try {
			String str = "2430929.7861,8.0,,,Vis.,LJ,QK,,,,,,,V,,,,ETA CAR,,STD,";

			ObservationSourceAnalyser analyser = new ObservationSourceAnalyser(
					new LineNumberReader(new StringReader(str)), "Some String");
			analyser.analyse();

			AbstractObservationRetriever simpleTextFormatReader = new TextFormatObservationReader(
					new LineNumberReader(new StringReader(str)), analyser, "");

			simpleTextFormatReader.retrieveObservations();

			List<ValidObservation> obs = simpleTextFormatReader
					.getValidObservations();

			assertEquals(1, obs.size());

			assertEquals(8.0, obs.get(0).getMagnitude().getMagValue());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	// Test that we can turn an out of order observation sequence into an
	// ordered sequence (by JD).
	public void testOutOfOrderData() {
		StringBuffer lines = new StringBuffer();
		lines.append("2454924.3,4.1\n");
		lines.append("2454923.3,4.2\n");
		lines.append("2454921.3,4.3\n");
		lines.append("2454925.3,4.2\n");
		lines.append("2454922.3,4.0\n");

		List<ValidObservation> obs = commonValidTest(lines.toString(), ",", "");

		double[][] expected = { { 2454921.3, 4.3 }, { 2454922.3, 4.0 },
				{ 2454923.3, 4.2 }, { 2454924.3, 4.1 }, { 2454925.3, 4.2 } };

		int i = 0;
		for (ValidObservation ob : obs) {
			// JD
			assertEquals(expected[i][0], ob.getJD());
			// Magnitude
			assertEquals(expected[i][1], ob.getMag());
			i++;
		}
	}

	// Tests with invalid data.

	// No digit after the magnitude decimal point. Although the format spec says
	// this is not well-formed, some real observations have this, which is odd,
	// but parseable.
	public void testSimpleInvalidMagTrailingDecimalPoint() {
		commonValidJulianDayAndMagTest("2450001.5\t10.\n", "\t");
	}

	// Tests of invalid simple text format.

	public void testSimpleInvalidAllButUncertaintyAndValflagTSV()
			throws IOException {
		// There should be another tab between the magnitude and obscode
		// to account for the missing uncertainty value field.
		commonInvalidTest("2450001.5\t10.0\tDJB\n");
	}

	// Helpers

	private ValidObservation commonValidJulianDayAndMagTest(String line,
			String delimiter) {
		List<ValidObservation> obs = commonValidTest(line, delimiter, "");

		assertTrue(obs.size() == 1);

		ValidObservation ob = obs.get(0);
		assertEquals(2450001.5, ob.getDateInfo().getJulianDay());
		assertEquals(10.0, ob.getMagnitude().getMagValue());
		assertFalse(ob.getMagnitude().isUncertain());
		assertFalse(ob.getMagnitude().isFainterThan());

		return ob;
	}

	private List<ValidObservation> commonValidTest(String str,
			String delimiter, String velaFilterStr) {
		List<ValidObservation> obs = null;

		try {
			ObservationSourceAnalyser analyser = new ObservationSourceAnalyser(
					new LineNumberReader(new StringReader(str)), "Some String");
			analyser.analyse();

			AbstractObservationRetriever simpleTextFormatReader = new TextFormatObservationReader(
					new LineNumberReader(new StringReader(str)), analyser,
					velaFilterStr);

			simpleTextFormatReader.retrieveObservations();
			obs = simpleTextFormatReader.getValidObservations();
		} catch (Exception e) {
			fail(e.getMessage());
		}

		return obs;
	}

	private void commonInvalidTest(String str) throws IOException {
		try {
			CsvReader reader = new CsvReader(new StringReader(str));
			reader.setDelimiter('\t');
			assertTrue(reader.readRecord());
			SimpleTextFormatValidator validator = new SimpleTextFormatValidator(
					reader, 2, 5,
					NewStarType.NEW_STAR_FROM_SIMPLE_FILE.getFieldInfoSource());
			validator.validate();
			// We should have thrown a ObservationValidationError...
			fail();
		} catch (ObservationValidationError e) {
			// We expect to get here.
			assertTrue(true);
		} catch (ObservationValidationWarning e) {
			// We should have thrown a ObservationValidationError...
			fail();
		}
	}
}
