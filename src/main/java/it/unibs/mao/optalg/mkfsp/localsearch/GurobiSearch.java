package it.unibs.mao.optalg.mkfsp.localsearch;

import gurobi.*;
import it.unibs.mao.optalg.mkfsp.FeasibilityCheck;
import it.unibs.mao.optalg.mkfsp.Instance;
import it.unibs.mao.optalg.mkfsp.grasp.Utils;

import java.util.*;

import it.unibs.mao.optalg.mkfsp.Model;
import it.unibs.mao.optalg.mkfsp.ModelVars;

public class GurobiSearch {
    private static final double INT_TOLERANCE = 1e-6;
    public static int[] run(Instance instance, int[] initialSolution, double timeLimit, HashMap<Integer, Integer> splitForFamily) {
        GRBEnv env = null;
        GRBModel model = null;
        int[] firstItems = instance.firstItems();

        try {
            env = new GRBEnv();
            env.set(GRB.IntParam.OutputFlag, 1);
            final ModelVars modelVars = Model.build(instance, env);
            model = modelVars.model();
            model.set(GRB.DoubleParam.TimeLimit, timeLimit);

            //dovrei provare a fissare solo le X famiglie migliori
            //Fix x_j variables to 1 if family is selected in the Grasp solution
            /*for (int i = 0; i < firstItems.length; i++) {
                int item = firstItems[i];
                if (initialSolution[item] != -1) {
                    //set xvars[i] LB = 1
                    modelVars.xvars()[i].set(GRB.DoubleAttr.LB, 1);
                }
            }

             */

            //Set initial solution of GRASP in Gurobi
            for (int i = 0; i < instance.nItems(); ++i) {
                for (int k = 0; k < instance.nKnapsacks(); ++k) {
                    if(initialSolution[i] == k) {
                        modelVars.yvars()[i][k].set(GRB.DoubleAttr.Start, 1);
                    } else {
                        modelVars.yvars()[i][k].set(GRB.DoubleAttr.Start, 0);
                    }
                }
            }

            for (int j : splitForFamily.keySet()) {
                model.addConstr(modelVars.svars()[j], GRB.LESS_EQUAL, splitForFamily.get(j), "_splits");
            }

            model.optimize();

            if (model.get(GRB.IntAttr.SolCount) > 0) {
                final int nItems = instance.nItems();
                final int nKnapsacks = instance.nKnapsacks();
                final GRBVar[][] yvars = modelVars.yvars();
                final double binLb = 1 - INT_TOLERANCE;
                final double binUb = 1 + INT_TOLERANCE;
                final int[] solution = new int[nItems];
                Arrays.fill(solution, -1);
                for (int i = 0; i < nItems; ++i) {
                    for (int k = 0; k < nKnapsacks; ++k) {
                        final double value = yvars[i][k].get(GRB.DoubleAttr.X);
                        if (binLb <= value && value <= binUb) {
                            solution[i] = k;
                            break;
                        }
                    }
                }
                final double objValue = model.get(GRB.DoubleAttr.ObjVal);
                final FeasibilityCheck check = instance.checkFeasibility(solution, objValue);


                System.out.println("\nSolution: " + objValue + " (valid: " + check.isValid() + ")");
                if (!check.isValid()) {
                    for (final String errMsg: check.errorMessages()) {
                        System.out.println("  - " + errMsg);
                    }
                } else {
                    return solution;
                }

            } else {
                final int statusCode =  model.get(GRB.IntAttr.Status);
                System.out.println("\nGurobi terminated with status code: " + statusCode);
            }

        } catch (GRBException e) {
            throw new RuntimeException(e);
        } finally {
            if (model != null) {
                model.dispose();
            }
        }
        return null;
    }
}
