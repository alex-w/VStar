package org.aavso.tools.vstar.external.plugin;

import java.awt.Dimension;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.TitledBorder;

import org.aavso.tools.vstar.data.DateInfo;
import org.aavso.tools.vstar.data.Magnitude;
import org.aavso.tools.vstar.data.SeriesType;
import org.aavso.tools.vstar.data.ValidObservation;
import org.aavso.tools.vstar.exception.AlgorithmError;
import org.aavso.tools.vstar.external.plugin.VeLaModelCreator.FunctionDomain;
import org.aavso.tools.vstar.plugin.ModelCreatorPluginBase;
import org.aavso.tools.vstar.ui.dialog.MessageBox;
import org.aavso.tools.vstar.ui.mediator.AnalysisType;
import org.aavso.tools.vstar.ui.mediator.Mediator;
import org.aavso.tools.vstar.ui.model.plot.ContinuousModelFunction;
import org.aavso.tools.vstar.ui.vela.VeLaDialog;
import org.aavso.tools.vstar.util.ApacheCommonsDerivativeBasedExtremaFinder;
import org.aavso.tools.vstar.util.Tolerance;
import org.aavso.tools.vstar.util.locale.LocaleProps;
import org.aavso.tools.vstar.util.model.AbstractModel;
import org.aavso.tools.vstar.util.prefs.NumericPrecisionPrefs;
import org.aavso.tools.vstar.vela.Operand;
import org.aavso.tools.vstar.vela.Type;
import org.aavso.tools.vstar.vela.VeLaInterpreter;
import org.aavso.tools.vstar.vela.VeLaValidObservationEnvironment;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.DifferentiableUnivariateRealFunction;
import org.apache.commons.math.analysis.UnivariateRealFunction;

/**
 * A model creator that allows a VeLa function to be applied to observations.
 */
public class VeLaModelCreator extends ModelCreatorPluginBase {

    private static final String DIALOG_TITLE = "Function Code [model: f(t:real):real, optional derivative: df(t:real):real]";
    private static final String FUNC_NAME = "F";
    private static final String DERIV_FUNC_NAME = "DF";
    private static final String RESOLUTION_VAR = "RESOLUTION";

    /**
     * Domain of the parameter t passed to the user-supplied VeLa function
     * f(t:real):real. A VeLa function string carries no indication of
     * whether t is a time value (JD/HJD/BJD/...) or a standard phase, so
     * the user chooses.
     */
    public enum FunctionDomain {
        TIME, PHASE
    }

    private static VeLaDialog velaDialog;

    /**
     * Default domain of t in f(t:real):real. Time matches the common case
     * of applying a function obtained from Fourier/polynomial modelling in
     * raw-data mode to observations in either raw-data or phase-plot mode
     * (see issue #487). "Time" here covers any of the time conventions
     * VStar carries (JD, HJD, BJD, ...).
     */
    private FunctionDomain functionDomain = FunctionDomain.TIME;

    /**
     * Match a "zeroPoint is &lt;number&gt;" header line as emitted by
     * {@code ApacheCommonsPolynomialFitCreatorPlugin.toVeLaString()} and
     * {@code PeriodAnalysisDerivedMultiPeriodicModel.toString()}. The number
     * is captured as a single token so it can be parsed with the locale-aware
     * {@link NumericPrecisionPrefs} format used to emit it.
     */
    private static final Pattern ZERO_POINT_PATTERN = Pattern
            .compile("(?im)^\\s*zeroPoint\\s+is\\s+(\\S+)");

    /**
     * Magnitudes above this are treated as time-domain zero points; values
     * below it as phase-style zero points (which AbstractModel sets to 0
     * in PHASE_PLOT mode). The threshold sits comfortably above the phase
     * range [0, 1] yet well below any time epoch in practical use:
     * full-precision JD/HJD/BJD (~2.4e6), MJD (~60000), and reduced
     * conventions some surveys use (e.g. Kepler's BKJD = BJD - 2454833 or
     * TESS's BTJD = BJD - 2457000), which currently sit in the low
     * thousands and only grow over time. Even early-mission BKJD values
     * (~0 in 2009) cleared 10 within months of launch. The radio is
     * user-overridable, so this is only a sensible default.
     */
    private static final double ZERO_POINT_DOMAIN_THRESHOLD = 10.0;

    public VeLaModelCreator() {
        super();
    }

    /**
     * @return The currently selected function domain.
     */
    public FunctionDomain getFunctionDomain() {
        return functionDomain;
    }

    /**
     * Set the function domain (intended for test/scripting use).
     */
    public void setFunctionDomain(FunctionDomain domain) {
        this.functionDomain = domain;
    }

    /**
     * Heuristic inference of a VeLa function's intended domain from its
     * source. We look for a line of the form "zeroPoint is &lt;number&gt;",
     * which both {@code ApacheCommonsPolynomialFitCreatorPlugin} and
     * {@code PeriodAnalysisDerivedMultiPeriodicModel} emit. A small
     * magnitude (typically 0, set by {@code AbstractModel} in PHASE_PLOT
     * mode) implies a phase-domain function; a time-magnitude value
     * (~2.4e6 for JD/HJD/BJD/...) implies a time domain.
     *
     * @param code the VeLa source to inspect; may be null
     * @return the inferred domain, or {@code null} if no marker was found
     *         (in which case callers should fall back to a default)
     */
    static FunctionDomain inferFunctionDomain(String code) {
        if (code == null) {
            return null;
        }
        Matcher m = ZERO_POINT_PATTERN.matcher(code);
        if (!m.find()) {
            return null;
        }
        String token = m.group(1);
        double value;
        try {
            // Prefer the locale-aware format used to emit the value.
            value = NumericPrecisionPrefs.getTimeOutputFormat().parse(token).doubleValue();
        } catch (ParseException ex) {
            try {
                value = Double.parseDouble(token);
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
        return Math.abs(value) < ZERO_POINT_DOMAIN_THRESHOLD ? FunctionDomain.PHASE
                : FunctionDomain.TIME;
    }

    @Override
    public String getDescription() {
        return "VeLa model creator";
    }

    @Override
    public String getDisplayName() {
        return "VeLa Model";
    }

    /**
     * @see org.aavso.tools.vstar.plugin.IPlugin#getDocName()
     */
    @Override
    public String getDocName() {
        return "VeLa Model Creator Plug-In.pdf";
    }

    @Override
    public AbstractModel getModel(List<ValidObservation> obs) {
        return new VeLaModel(obs);
    }

    class VeLaUnivariateRealFunction implements DifferentiableUnivariateRealFunction {

        private VeLaInterpreter vela;
        private String funcName;

        public VeLaUnivariateRealFunction(VeLaInterpreter vela, String funcName) {
            this.vela = vela;
            this.funcName = funcName;
        }

        /**
         * Return the value of the model function or its derivative.
         * 
         * @param t The time value.
         * @return The model value at time t.
         * @throws FunctionEvaluationException If there is an error during function
         *                                     evaluation.
         */
        @Override
        public double value(double t) throws FunctionEvaluationException {
            String funCall = funcName + "(" + NumericPrecisionPrefs.formatTime(t) + ")";
            Optional<Operand> result = vela.program(funCall);
            if (result.isPresent()) {
                return result.get().doubleVal();
            } else {
                throw new FunctionEvaluationException(t);
            }
        }

        /**
         * If the derivative (df) function doesn't exist, this will never be called
         * since we will bypass extrema determination.
         */
        @Override
        public UnivariateRealFunction derivative() {
            return new VeLaUnivariateRealFunction(vela, DERIV_FUNC_NAME);
        }
    }

    class VeLaModel extends AbstractModel {
        double zeroPoint;
        UnivariateRealFunction function;
        VeLaInterpreter vela;
        String velaModelFunctionStr;
        String modelName;

        VeLaModel(List<ValidObservation> obs) {
            super(obs);

            // Create a VeLa interpreter instance.
            vela = new VeLaInterpreter();

            zeroPoint = 0;
            if (Mediator.getInstance().getAnalysisType() == AnalysisType.PHASE_PLOT) {
                Collections.sort(this.obs, timeComparator);
            }

            String modelFuncStr = null;
            String modelNameStr = null;

            if (inTestMode()) {
                modelFuncStr = getTestModelFunc();
                modelNameStr = getTestModelName();
            } else {
                // Build a "Function domain" radio panel to embed in the
                // VeLa dialog. See issue #487: a VeLa function string does
                // not record whether t is a JD or a standard phase, so the
                // user must choose. We pre-select the radio by inspecting
                // the prior code's "zeroPoint is X" header, falling back
                // to the last-used domain when no marker is found, and
                // then keep the selection in sync with any subsequent
                // edits or pastes via a code-change listener.
                String priorCode = (velaDialog == null) ? null : velaDialog.getCode();
                FunctionDomain initial = inferFunctionDomain(priorCode);
                if (initial == null) {
                    initial = functionDomain;
                }
                boolean phaseAvailable = !obs.isEmpty()
                        && obs.get(0).getStandardPhase() != null;
                FunctionDomainPanel domainPanel = new FunctionDomainPanel(initial, phaseAvailable);

                final boolean phaseEnabled = phaseAvailable;
                velaDialog = new VeLaDialog(DIALOG_TITLE, priorCode, domainPanel,
                        currentCode -> {
                            FunctionDomain inferred = inferFunctionDomain(currentCode);
                            if (inferred != null && (inferred != FunctionDomain.PHASE || phaseEnabled)) {
                                domainPanel.setSelectedDomain(inferred);
                            }
                        });

                if (!velaDialog.isCancelled()) {
                    modelFuncStr = velaDialog.getCode();
                    modelNameStr = "VeLa model";
                    functionDomain = domainPanel.getSelectedDomain();
                }
            }

            // Bind TIMES consistently with the chosen function domain so
            // that user code referencing TIMES agrees with f(t)'s input.
            List<Operand> timesList = this.obs.stream()
                    .map(ob -> new Operand(Type.REAL, timeFor(ob)))
                    .collect(Collectors.toList());
            vela.bind("TIMES", new Operand(Type.LIST, timesList), true);

            List<Operand> magList = this.obs.stream().map(ob -> new Operand(Type.REAL, ob.getMag()))
                    .collect(Collectors.toList());
            Operand mags = new Operand(Type.LIST, magList);
            vela.bind("MAGS", mags, true);

            velaModelFunctionStr = modelFuncStr;
            modelName = modelNameStr;
        }

        /**
         * Return the time-coordinate value to feed into the user's f(t) for
         * the given observation, based on the selected function domain.
         * In TIME mode this returns whatever time convention the user
         * loaded into the JD field (JD, HJD, BJD, ...).
         */
        private double timeFor(ValidObservation ob) {
            switch (functionDomain) {
            case TIME:
                return ob.getJD();
            case PHASE:
                return ob.getStandardPhase();
            default:
                throw new AssertionError("Unhandled function domain: " + functionDomain);
            }
        }

        @Override
        public String getDescription() {
            return modelName + " applied to " + obs.get(0).getBand() + " series";
        }

        @Override
        public String getKind() {
            return "VeLa Model";
        }

        @Override
        public boolean hasFuncDesc() {
            return true;
        }

        @Override
        public String toString() {
            return toVeLaString();
        }

        @Override
        public String toVeLaString() {
            return velaModelFunctionStr;
        }

        @Override
        public ContinuousModelFunction getModelFunction() {
            return new ContinuousModelFunction(function, fit, zeroPoint);
        }

        /**
         * Display only the RMS for VeLa-applied models. The AIC/BIC values
         * computed in {@link AbstractModel#fitMetrics()} require a
         * meaningful degree-of-freedom count, which is not available for
         * arbitrary user-supplied VeLa code, so we omit them here.
         */
        @Override
        public void fitMetrics() {
            String key = LocaleProps.get("MODEL_INFO_FIT_METRICS_TITLE");
            if (functionStrMap.get(key) == null && !Double.isNaN(rms)) {
                functionStrMap.put(key, "RMS: " + NumericPrecisionPrefs.formatOther(rms));
            }
        }

        @Override
        public void execute() throws AlgorithmError {
            if (!interrupted) {
                try {
                    if (velaModelFunctionStr != null) {
                        // Evaluate the VeLa model code.
                        // A univariate function f(t:real):real is
                        // assumed to exist after this completes.
                        vela.program(velaModelFunctionStr);

                        String funcName = FUNC_NAME;

                        // Has a model function been defined?
                        if (!vela.lookupFunctions(FUNC_NAME).isPresent()) {
                            MessageBox.showErrorDialog("VeLa Model Error", "f(t:real):real undefined");
                        } else {
                            function = new VeLaUnivariateRealFunction(vela, funcName);

                            fit = new ArrayList<ValidObservation>();
                            residuals = new ArrayList<ValidObservation>();

                            String comment = "\n" + velaModelFunctionStr;

                            // Create fit and residual observations.
                            for (int i = 0; i < obs.size() && !interrupted; i++) {
                                ValidObservation ob = obs.get(i);

                                // Push an environment that makes the
                                // observation available to VeLa code.
                                vela.pushEnvironment(new VeLaValidObservationEnvironment(ob));

                                // Issue #487: a VeLa function string has no
                                // inherent way to indicate whether t is a
                                // time value (JD/HJD/BJD/...) or a
                                // standard phase, so the user chose via
                                // the "Function domain" panel. Evaluate
                                // f(t) accordingly. In PHASE_PLOT mode,
                                // timeCoordSource.getXCoord() returns
                                // phase, but that is only the right input
                                // when the function was itself defined in
                                // phase (e.g. a polynomial fit performed
                                // in phase-plot mode); functions generated
                                // in raw-data mode (e.g. Fourier or
                                // polynomial fits in time) must be
                                // evaluated at the observation's time.
                                double x = timeFor(ob);
                                double y = function.value(x);

                                collectObs(y, ob, comment);

                                // Pop the observation environment.
                                vela.popEnvironment();
                            }

                            functionStrMap.put(LocaleProps.get("MODEL_INFO_FUNCTION_TITLE"), toString());

                            // Compute fit metrics (RMS only; AIC/BIC require
                            // a meaningful degree-of-freedom count, which we
                            // do not have for arbitrary user-supplied VeLa
                            // code).
                            if (!residuals.isEmpty()) {
                                rootMeanSquare();
                                fitMetrics();
                            }

                            // Has a derivative function been defined?
                            // If so, carry out extrema determination.
                            if (vela.lookupFunctions(DERIV_FUNC_NAME).isPresent()) {
                                // Use a real VeLa resolution variable
                                // if it exists, else use a value of
                                // 0.1.
                                double resolution = 0.1;
                                Optional<Operand> resVar = vela.lookupBinding(RESOLUTION_VAR);
                                if (resVar.isPresent()) {
                                    switch (resVar.get().getType()) {
                                    case REAL:
                                        resolution = resVar.get().doubleVal();
                                        break;
                                    case INTEGER:
                                        resolution = resVar.get().intVal();
                                        break;
                                    default:
                                        MessageBox.showErrorDialog("VeLa Model Error", "Resolution must be numeric");
                                        break;
                                    }
                                }

                                ApacheCommonsDerivativeBasedExtremaFinder finder = new ApacheCommonsDerivativeBasedExtremaFinder(
                                        fit, (DifferentiableUnivariateRealFunction) function, timeCoordSource,
                                        zeroPoint, resolution);

                                String extremaStr = finder.toString();

                                if (extremaStr != null) {
                                    String title = LocaleProps.get("MODEL_INFO_EXTREMA_TITLE");

                                    functionStrMap.put(title, extremaStr);
                                }
                            }
                        }
                    }
                } catch (FunctionEvaluationException e) {
                    throw new AlgorithmError(e.getLocalizedMessage());
                }
            }
        }
    }

    // Plug-in test

    @Override
    public Boolean test() {
        boolean success = true;

        setTestMode(true);

        AnalysisType originalAnalysisType = Mediator.getInstance().getAnalysisType();

        try {
            success &= testRawMode();

            // Regression test for issue #487: in phase-plot mode the VeLa
            // function must be evaluated against the parameter domain
            // selected by the user (JD by default, optionally phase), not
            // against whatever coordinate source the current analysis mode
            // happens to expose.
            success &= testPhasePlotFunctionDomain();

            // Issue #487 follow-ups: domain inference from `zeroPoint is`
            // header, and RMS reporting for VeLa-applied models.
            success &= testInferFunctionDomain();
            success &= testFitMetricsIncludeRMS();
        } catch (Exception e) {
            success = false;
        } finally {
            Mediator.getInstance().setAnalysisType(originalAnalysisType);
            setTestMode(false);
        }

        return success;
    }

    private boolean testRawMode() {
        boolean result = true;
        try {
            Mediator.getInstance().setAnalysisType(AnalysisType.RAW_DATA);

            AbstractModel model = getModel(createObs());
            model.execute();

            result &= model.hasFuncDesc();
            String desc = getTestModelName() + " applied to Visual series";
            result &= model.getDescription().equals(desc);
            result &= !model.getFit().isEmpty();
            result &= !model.getResiduals().isEmpty();
            result &= model.getFit().size() == model.getResiduals().size();
            result &= Tolerance.areClose(12.34620932, model.getFit().get(0).getMag(), 1e-6, true);
        } catch (Exception e) {
            result = false;
        }
        return result;
    }

    private boolean testPhasePlotFunctionDomain() {
        boolean result = true;
        try {
            Mediator.getInstance().setAnalysisType(AnalysisType.PHASE_PLOT);

            // Identity function: allows an unambiguous check of whether f(t)
            // was called with a time value (~2.4e6) or a standard phase
            // (in [0, 1]).
            class IdentityCreator extends VeLaModelCreator {
                @Override
                protected String getTestModelFunc() {
                    return "f(t:real) : real { t }\n";
                }
            }

            // Default (TIME): fit magnitude must equal the observation time.
            IdentityCreator timeCreator = new IdentityCreator();
            timeCreator.setTestMode(true);
            AbstractModel timeModel = timeCreator.getModel(createPhasedObs());
            timeModel.execute();

            result &= !timeModel.getFit().isEmpty();
            result &= timeModel.getFit().size() == timeModel.getResiduals().size();

            // After phase-plot sorting by standard phase, the observation
            // with phase 0.1 (JD 2447121.5) comes first.
            double expectedTime = 2447121.5;
            result &= Tolerance.areClose(expectedTime, timeModel.getFit().get(0).getMag(), 1e-6, true);
            result &= Tolerance.areClose(11.0 - expectedTime, timeModel.getResiduals().get(0).getMag(), 1e-6, true);

            // Explicit PHASE domain: fit magnitude must be the phase.
            IdentityCreator phaseCreator = new IdentityCreator();
            phaseCreator.setTestMode(true);
            phaseCreator.setFunctionDomain(FunctionDomain.PHASE);
            AbstractModel phaseModel = phaseCreator.getModel(createPhasedObs());
            phaseModel.execute();

            double expectedPhase = 0.1;
            result &= Tolerance.areClose(expectedPhase, phaseModel.getFit().get(0).getMag(), 1e-6, true);
            result &= Tolerance.areClose(11.0 - expectedPhase, phaseModel.getResiduals().get(0).getMag(), 1e-6, true);
        } catch (Exception e) {
            result = false;
        }
        return result;
    }

    private boolean testInferFunctionDomain() {
        boolean result = true;

        // Hand-written code with no zeroPoint marker: no inference.
        result &= (inferFunctionDomain("f(t:real):real { t * 2 }") == null);
        result &= (inferFunctionDomain(null) == null);

        // Phase-mode polynomial output: zeroPoint is 0(.0) -> PHASE.
        result &= (inferFunctionDomain("zeroPoint is 0\nf(t:real):real { 1.5 }") == FunctionDomain.PHASE);
        result &= (inferFunctionDomain("zeroPoint is 0.0000\nf(t:real):real { 1.5 }") == FunctionDomain.PHASE);

        // Time-mode polynomial / Fourier output: zeroPoint at a typical
        // JD/HJD/BJD magnitude -> TIME.
        result &= (inferFunctionDomain("zeroPoint is 48680\nf(t:real):real { 0 }") == FunctionDomain.TIME);
        result &= (inferFunctionDomain("zeroPoint is 2451700.5\nf(t:real):real { 0 }") == FunctionDomain.TIME);

        // Reduced/modified time conventions (MJD ~60000, mission-reduced
        // epochs in the low thousands or hundreds) must also classify as
        // TIME, not PHASE. See user note: VStar doesn't formally support
        // these but users can still load such data.
        result &= (inferFunctionDomain("zeroPoint is 60123.5\nf(t:real):real { 0 }") == FunctionDomain.TIME);
        result &= (inferFunctionDomain("zeroPoint is 350.25\nf(t:real):real { 0 }") == FunctionDomain.TIME);

        return result;
    }

    private boolean testFitMetricsIncludeRMS() {
        boolean result = true;
        try {
            Mediator.getInstance().setAnalysisType(AnalysisType.RAW_DATA);

            // Constant function: residuals are non-zero (mag != 11), so
            // RMS must be computed and surfaced as a function-string entry.
            VeLaModelCreator creator = new VeLaModelCreator() {
                @Override
                protected String getTestModelFunc() {
                    return "f(t:real) : real { 11 }\n";
                }
            };
            creator.setTestMode(true);

            AbstractModel model = creator.getModel(createObs());
            model.execute();

            result &= !Double.isNaN(model.getRMS());
            String metrics = model.getFunctionStrings().get(LocaleProps.get("MODEL_INFO_FIT_METRICS_TITLE"));
            result &= metrics != null && metrics.startsWith("RMS:");
            // AIC/BIC must be omitted (no meaningful d.o.f. for arbitrary
            // user code), so the metrics string should not contain them.
            result &= metrics != null && !metrics.contains("AIC");
            result &= metrics != null && !metrics.contains("BIC");
        } catch (Exception e) {
            result = false;
        }
        return result;
    }

    protected String getTestModelFunc() {
        String func = "";

        func += "f(t:real) : real {\n";
        func += "  11.7340392\n";
        func += "  -0.6588158 * cos(2*PI*0.0017177*(t-2451700))\n";
        func += "  +1.3908874 * sin(2*PI*0.0017177*(t-2451700))";
        func += "}\n";

        return func;
    }

    protected String getTestModelName() {
        return "test model";
    }

    private List<ValidObservation> createObs() {
        List<ValidObservation> obs = new ArrayList<ValidObservation>();

        ValidObservation ob = new ValidObservation();
        ob.setMagnitude(new Magnitude(11, 0.1));
        ob.setDateInfo(new DateInfo(2447121.5));
        ob.setBand(SeriesType.Visual);
        ob.setObsCode("ABC");
        obs.add(ob);

        ob = new ValidObservation();
        ob.setMagnitude(new Magnitude(11.05, 0.02));
        ob.setDateInfo(new DateInfo(2447121.501));
        ob.setBand(SeriesType.Johnson_V);
        ob.setObsCode("XYZ");
        obs.add(ob);

        return obs;
    }

    private List<ValidObservation> createPhasedObs() {
        List<ValidObservation> obs = createObs();
        obs.get(0).setStandardPhase(0.1);
        obs.get(0).setPreviousCyclePhase(-0.9);
        obs.get(1).setStandardPhase(0.2);
        obs.get(1).setPreviousCyclePhase(-0.8);
        return obs;
    }

    /**
     * Radio-button panel embedded in the VeLa model dialog that lets the
     * user declare whether the parameter of f(t:real):real is a time value
     * (JD/HJD/BJD/...) or a standard phase. See issue #487.
     */
    @SuppressWarnings("serial")
    static class FunctionDomainPanel extends JPanel {
        private static final String TIME_LABEL = "Time";
        private static final String PHASE_LABEL = "Phase";

        private final JRadioButton timeButton;
        private final JRadioButton phaseButton;

        FunctionDomainPanel(FunctionDomain initial, boolean phaseAvailable) {
            setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
            setBorder(BorderFactory.createTitledBorder(null, "Function domain (t)",
                    TitledBorder.LEADING, TitledBorder.TOP));

            timeButton = new JRadioButton(TIME_LABEL);
            phaseButton = new JRadioButton(PHASE_LABEL);

            if (!phaseAvailable) {
                phaseButton.setEnabled(false);
                phaseButton.setToolTipText(
                        "Create a phase plot first (Analysis \u2192 Phase Plot)");
                timeButton.setSelected(true);
            } else {
                timeButton.setSelected(initial == FunctionDomain.TIME);
                phaseButton.setSelected(initial == FunctionDomain.PHASE);
            }

            ButtonGroup group = new ButtonGroup();
            group.add(timeButton);
            group.add(phaseButton);

            add(Box.createRigidArea(new Dimension(5, 0)));
            add(timeButton);
            add(Box.createRigidArea(new Dimension(10, 0)));
            add(phaseButton);
            add(Box.createHorizontalGlue());
        }

        FunctionDomain getSelectedDomain() {
            return phaseButton.isSelected() ? FunctionDomain.PHASE : FunctionDomain.TIME;
        }

        /**
         * Programmatically update the selected radio button. Used by the
         * VeLa dialog's code-change listener to keep the selection in sync
         * with newly pasted/edited code that contains a "zeroPoint is X"
         * marker.
         */
        void setSelectedDomain(FunctionDomain domain) {
            if (domain == FunctionDomain.PHASE) {
                phaseButton.setSelected(true);
            } else {
                timeButton.setSelected(true);
            }
        }
    }
}
