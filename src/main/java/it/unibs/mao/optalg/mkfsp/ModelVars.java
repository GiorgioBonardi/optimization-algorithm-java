package it.unibs.mao.optalg.mkfsp;

import gurobi.GRBModel;
import gurobi.GRBVar;

/**
 * An immutable class that holds the Gurobi model of a MKFSP instance together
 * with its related variable objects divided by family.
 */
public record ModelVars(
    GRBModel model,
    GRBVar[] xvars,
    GRBVar[][] yvars,
    GRBVar[][] zvars,
    GRBVar[] svars
) {}
