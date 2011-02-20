/**
 * VStar: a statistical analysis tool for variable star data.
 * Copyright (C) 2010  AAVSO (http://www.aavso.org/)
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
package org.aavso.tools.vstar.external.plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.aavso.tools.vstar.data.DateInfo;
import org.aavso.tools.vstar.data.InvalidObservation;
import org.aavso.tools.vstar.data.MTypeType;
import org.aavso.tools.vstar.data.Magnitude;
import org.aavso.tools.vstar.data.SeriesType;
import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.data.validation.InclusiveRangePredicate;
import org.aavso.tools.vstar.data.validation.JulianDayValidator;
import org.aavso.tools.vstar.data.validation.MagnitudeFieldValidator;
import org.aavso.tools.vstar.data.validation.MagnitudeValueValidator;
import org.aavso.tools.vstar.data.validation.TransformedValidator;
import org.aavso.tools.vstar.data.validation.UncertaintyValueValidator;
import org.aavso.tools.vstar.exception.ObservationReadError;
import org.aavso.tools.vstar.exception.ObservationValidationError;
import org.aavso.tools.vstar.input.AbstractObservationRetriever;
import org.aavso.tools.vstar.plugin.InputType;
import org.aavso.tools.vstar.plugin.ObservationSourcePluginBase;

/**
 * This plug-in class reads AAVSO extended format files, yielding an observation
 * list.
 * 
 * The following was used for reference:
 * http://www.aavso.org/aavso-extended-file-format
 */
public class AAVSOExtendedFileFormatObservationSource extends
		ObservationSourcePluginBase {

	/**
	 * @see org.aavso.tools.vstar.plugin.ObservationSourcePluginBase#getCurrentStarName
	 *      ()
	 */
	@Override
	public String getCurrentStarName() {
		return getInputName();
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.ObservationSourcePluginBase#getInputType()
	 */
	@Override
	public InputType getInputType() {
		return InputType.FILE;
	}

	/**
	 * @seeorg.aavso.tools.vstar.plugin.ObservationSourcePluginBase# 
	 *                                                               getObservationRetriever
	 *                                                               ()
	 */
	@Override
	public AbstractObservationRetriever getObservationRetriever() {
		return new AAVSOExtendedFileFormatRetriever();
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.PluginBase#getDescription()
	 */
	@Override
	public String getDescription() {
		return "An AAVSO Extended File Format reader.";
	}

	/**
	 * @see org.aavso.tools.vstar.plugin.PluginBase#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return "New Star from AAVSO Extended Format File...";
	}

	class AAVSOExtendedFileFormatRetriever extends AbstractObservationRetriever {
		private String fileType;
		private String obscode;
		private String software;
		private String delimiter;
		private String dateType;
		private String obsType;

		private JulianDayValidator julianDayValidator;
		private MagnitudeFieldValidator magnitudeFieldValidator;
		private UncertaintyValueValidator uncertaintyValueValidator;
		private TransformedValidator transformedValidator;
		private MagnitudeValueValidator magnitudeValueValidator;

		/**
		 * Constructor.
		 */
		public AAVSOExtendedFileFormatRetriever() {
			julianDayValidator = new JulianDayValidator();
			magnitudeFieldValidator = new MagnitudeFieldValidator();
			uncertaintyValueValidator = new UncertaintyValueValidator(
					new InclusiveRangePredicate(0, 1));
			transformedValidator = new TransformedValidator();
			// What should this be for CCD/PEP?
			magnitudeValueValidator = new MagnitudeValueValidator(
					new InclusiveRangePredicate(-10, 25));
		}

		@Override
		public void retrieveObservations() throws ObservationReadError,
				InterruptedException {

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					getInputStream()));

			String line = null;
			int lineNum = 1;

			try {
				line = reader.readLine();
				while (line != null) {
					line = line.replaceFirst("\n", "");
					if (!isEmpty(line)) {
						if (line.startsWith("#")) {
							handleDirective(line);
						} else {
							collectObservation(readNextObservation(line));
						}
					}
					line = reader.readLine();
					lineNum++;
				}
			} catch (Exception e) {
				// Create an invalid observation.
				String error = e.getLocalizedMessage();
				InvalidObservation ob = new InvalidObservation(line, error);
				ob.setRecordNumber(lineNum);
				addInvalidObservation(ob);
			}
		}

		// If a line starts with #, it's either a directive or a comment.
		private void handleDirective(String line) throws ObservationReadError {
			String[] pair = line.toUpperCase().split("=");

			if ("#TYPE".equals(pair[0])) {
				fileType = pair[1];
				if (!"EXTENDED".equals(fileType)) {
					throw new ObservationReadError("Invalid file type: "
							+ fileType);
				}
			} else if ("#OBSCODE".equals(pair[0])) {
				obscode = pair[1].toUpperCase();
			} else if ("#SOFTWARE".equals(pair[0])) {
				software = pair[1];
			} else if ("#DELIM".equals(pair[0])) {
				delimiter = pair[1];
				if (isEmpty(delimiter)) {
					throw new ObservationReadError("No delimiter specified.");
				}
			} else if ("#DATE".equals(pair[0])) {
				dateType = pair[1];
				if (!"JD".equals(dateType)) {
					throw new ObservationReadError("Unsupported date type: "
							+ dateType);
				}
			} else if ("#OBSTYPE".equals(pair[0])) {
				obsType = pair[1];
				if (!"CCD".equals(obsType) && !"PEP".equals(obsType)) {
					throw new ObservationReadError("Unknown observation type: "
							+ obsType);
				}
			}
		}

		private ValidObservation readNextObservation(String line)
				throws ObservationValidationError {
			String[] fields = line.split(delimiter);

			ValidObservation observation = new ValidObservation();

			// TODO: set line number 
			
			String name = fields[0];

			observation.setName(name);
			observation.setObsCode(obscode);

			// TODO: handle calendar date format as well as JD.
			DateInfo dateInfo = julianDayValidator.validate(fields[1]);
			observation.setDateInfo(dateInfo);

			Magnitude magnitude = magnitudeFieldValidator.validate(fields[2]);

			String uncertaintyStr = fields[3];
			if (!isNA(uncertaintyStr)) {
				double uncertainty = uncertaintyValueValidator
						.validate(fields[3]);
				magnitude.setUncertainty(uncertainty);
			}

			observation.setMagnitude(magnitude);

			String filter = fields[4];
			SeriesType band = SeriesType.getSeriesFromShortName(filter);
			observation.setBand(band);

			String transformedStr = fields[5];
			if (!isNA(transformedStr)) {
				boolean transformed = transformedValidator
						.validate(transformedStr);
				// Defaults to false.
				observation.setTransformed(transformed);
			}

			// Note: enum doesn't have ABS currently; STEP not in spec. Add it?
			// ValidObservation defaults to STD.
			String mtypeStr = fields[6];
			MTypeType mtype = null;
			if (!isNA(mtypeStr)) {
				if ("DIF".equals(mtypeStr)) {
					mtype = MTypeType.DIFF;
				} else if ("STD".equals(mtypeStr)) {
					mtype = MTypeType.STD;
				}
			}
			if (mtype != null) {
				observation.setMType(mtype);

			}

			String cname = fields[7];
			if (isNA(cname)) {
				if (mtype == MTypeType.DIFF) {
					throw new ObservationValidationError(
							"Magnitude type is differential but there is no CNAME.");
				} else {
					cname = "";
				}
			} else {
				cname += ": ";
			}

			String cmagStr = fields[8];
			if (!isNA(cmagStr)) {
				double cmag = magnitudeValueValidator.validate(cmagStr);
				observation.setCMag(cname + cmag);
			}

			String kname = fields[9];
			if (isNA(kname)) {
				kname = "";
			} else {
				kname += ": ";
			}

			String kmagStr = fields[10];
			if (!isNA(kmagStr)) {
				double kmag = magnitudeValueValidator.validate(kmagStr);
				observation.setKMag(kname + kmag);
			}

			String airmass = fields[11];
			if (!isNA(airmass)) {
				observation.setAirmass(airmass);
			}

			String group = fields[12];
			if (group.length() > 5) {
				throw new ObservationValidationError(
						"GROUP has more than 5 characters.");
			}

			String chart = fields[13];
			if (!isNA(chart)) {
				observation.setCharts(chart);
			}

			String notes = fields[14];

			// Combine some fields as comments.
			String comments = "";

			if (!isNA(notes)) {
				comments = notes;
			}

			if (!isEmpty(software)) {
				if (!isEmpty(comments)) {
					comments += "; ";
				}
				comments += "software: " + software;
			}

			if (!isEmpty(group)) {
				if (!isEmpty(comments)) {
					comments += "; ";
				}
				comments += "group: " + group;
			}

			if (!isEmpty(comments)) {
				observation.setComments(comments);
			}

			return observation;
		}
	}

	private boolean isEmpty(String str) {
		return str != null && "".equals(str.trim());
	}

	private boolean isNA(String str) {
		return "NA".equalsIgnoreCase(str);
	}
}