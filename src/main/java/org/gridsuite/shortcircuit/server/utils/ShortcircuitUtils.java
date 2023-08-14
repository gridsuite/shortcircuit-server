package org.gridsuite.shortcircuit.server.utils;

import com.powsybl.iidm.network.extensions.util.FortescueUtil;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.shortcircuit.FortescueValue;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.Pair;

public final class ShortcircuitUtils {

    private ShortcircuitUtils() {
    }

    //TODO Remove this method in order to use FortescueValue::toThreePhaseValue once Powsybl core 6.0.0 version is out
    public static FortescueValue.ThreePhaseValue toThreePhaseValue(FortescueValue fortescueValue) {
        Vector2D positiveSequence = FortescueUtil.getCartesianFromPolar(fortescueValue.getPositiveMagnitude(), fortescueValue.getPositiveAngle());
        Vector2D zeroSequence = FortescueUtil.getCartesianFromPolar(fortescueValue.getZeroMagnitude(), fortescueValue.getZeroAngle());
        Vector2D negativeSequence = FortescueUtil.getCartesianFromPolar(fortescueValue.getNegativeMagnitude(), fortescueValue.getNegativeAngle());
        DenseMatrix mGfortescue = new DenseMatrix(6, 1);
        mGfortescue.add(0, 0, zeroSequence.getX());
        mGfortescue.add(1, 0, zeroSequence.getY());
        mGfortescue.add(2, 0, positiveSequence.getX());
        mGfortescue.add(3, 0, positiveSequence.getY());
        mGfortescue.add(4, 0, negativeSequence.getX());
        mGfortescue.add(5, 0, negativeSequence.getY());
        DenseMatrix mGphase = FortescueUtil.getFortescueMatrix().times(mGfortescue).toDense();
        Pair<Double, Double> phaseA = FortescueUtil.getPolarFromCartesian(mGphase.get(0, 0), mGphase.get(1, 0));
        Pair<Double, Double> phaseB = FortescueUtil.getPolarFromCartesian(mGphase.get(2, 0), mGphase.get(3, 0));
        Pair<Double, Double> phaseC = FortescueUtil.getPolarFromCartesian(mGphase.get(4, 0), mGphase.get(5, 0));
        return fortescueValue.new ThreePhaseValue(phaseA.getKey() / Math.sqrt(3.0D), phaseB.getKey() / Math.sqrt(3.0D), phaseC.getKey() / Math.sqrt(3.0D), phaseA.getValue(), phaseB.getValue(), phaseC.getValue());
    }
}
