package it.unibs.mao.optalg.mkfsp;

import gurobi.*;

/**
 * A utility class with one static method that builds the Gurobi model for the Separation Problem instance.
 * This class cannot be instantiated.
 */
public class SeparationProblemModel {
    private SeparationProblemModel() {
        // no-op
    }

    /**
     * Constructs a Gurobi model for the given Separation Problem instance.
     *
     * @param instance  an instance of the Separation Problem
     * @param env       the Gurobi environment used to build the GRBModel instance
     * @param eps       the penalty term to control the importance of penalties
     * @return
     * @throws GRBException
     * @see SeparationProblemModelVars
     */
    public static SeparationProblemModelVars build(Instance instance, GRBEnv env, double eps, int k) throws GRBException {
        final GRBModel model = new GRBModel(env);
        model.set(GRB.IntAttr.ModelSense, GRB.MAXIMIZE);

        final int nItems = instance.nItems();
        final int[][] items = instance.items();
        final int nKnapsacks = instance.nKnapsacks();
        final int nResources = instance.nResources();
        final int[][] knapsacks = instance.knapsacks();

        // Add model variables
        final GRBVar[] zvars = new GRBVar[nItems];
        final GRBVar[][] yvars = new GRBVar[nItems][nKnapsacks];

        for (int j = 0; j < nItems; ++j) {
            zvars[j] = model.addVar(0, 1, 0, GRB.BINARY, "z["+j+"]");
            yvars[j][k] = model.addVar(0, 1, 0, GRB.BINARY, "y["+j+","+k+"]");
        }

        // Add objective function
        final GRBLinExpr objExpr = new GRBLinExpr();
        for (int j = 0; j < nItems; ++j) {
            objExpr.addTerm(1, zvars[j]);
        }
        model.setObjective(objExpr, GRB.MAXIMIZE); //Nota: qua è maximize e sotto il vincolo è stato cambiato di segno ma va bene?

        // Add resource constraints
        final int[] knapsackCapacities = knapsacks[k];
        for (int r = 0; r < nResources; ++r) {
            final GRBLinExpr lhs = new GRBLinExpr();
            for (int j = 0; j < nItems; ++j) {
                lhs.addTerm(items[j][r], zvars[j]);
            }
            lhs.addConstant(-eps * knapsackCapacities[r]);
            model.addConstr(lhs, GRB.LESS_EQUAL, knapsackCapacities[r], "_r_" + k + "_" + r);
        }

        model.update();

        return new SeparationProblemModelVars(model, zvars, yvars);
    }
}
