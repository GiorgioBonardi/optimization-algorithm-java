package it.unibs.mao.optalg.mkfsp.separationProblem;

import gurobi.GRBModel;
import gurobi.GRBVar;

/**
 * A utility class to hold the variables of the Gurobi model for the Separation Problem.
 * This class is used to return the variables after building the model.
 */
public class SeparationProblemModelVars {
    private final GRBModel model;
    private final GRBVar[] zvars;
    private final GRBVar[][] yvars;

    public SeparationProblemModelVars(GRBModel model, GRBVar[] zvars, GRBVar[][] yvars) {
        this.model = model;
        this.zvars = zvars;
        this.yvars = yvars;
    }

    public GRBModel getModel() {
        return model;
    }

    public GRBVar[] getZVars() {
        return zvars;
    }

    public GRBVar[][] getYVars() {
        return yvars;
    }
}
