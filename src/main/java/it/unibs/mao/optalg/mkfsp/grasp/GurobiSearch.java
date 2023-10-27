package it.unibs.mao.optalg.mkfsp.grasp;

import gurobi.*;
import it.unibs.mao.optalg.mkfsp.FeasibilityCheck;
import it.unibs.mao.optalg.mkfsp.Instance;
import it.unibs.mao.optalg.mkfsp.grasp.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import it.unibs.mao.optalg.mkfsp.Model;
import it.unibs.mao.optalg.mkfsp.ModelVars;

public class GurobiSearch {
    private static final double INT_TOLERANCE = 1e-6;
    public static int[] run(Instance instance, int[] initialSolution, double timeLimit, HashMap<Integer, Integer> splitForFamily, Path outputDir) throws RuntimeException  {
        GRBEnv env = null;
        GRBModel model = null;

        try {
            env = new GRBEnv();
            env.set(GRB.IntParam.OutputFlag, 1);
            final ModelVars modelVars = Model.build(instance, env);
            model = modelVars.model();
            model.set(GRB.StringParam.LogFile, outputDir.resolve(instance.id() + ".log").toString());

            model.set(GRB.DoubleParam.TimeLimit, timeLimit);

            List<Integer> familiesToSet = Utils.getBestFamiliesUsedBySplit(instance, initialSolution);
            List<Integer> familiesToBan = Utils.getWorstFamiliesNotUsedBySpecialGain(instance, initialSolution);

            for (int i = 0; i < instance.nFamilies(); ++i) {
                if(familiesToSet.contains(i)) {
                    modelVars.xvars()[i].set(GRB.DoubleAttr.LB, 1);
                } else if(familiesToBan.contains(i)) {
                    modelVars.xvars()[i].set(GRB.DoubleAttr.UB, 0);
                }
            }

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

                //TTB - Time To Best
                Model.CallbackExecutionInfo executionInfo = modelVars.executionInfo();
                List<double[]> history = executionInfo.history;

                System.out.println("\nSolution: " + objValue + " (valid: " + check.isValid() + ")" + " TTB: " +
                        (history.get(history.size() - 1)[0]) + "s MIP Incumbent: " + history.get(history.size() - 1)[1]);

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
        throw new RuntimeException();
    }
}
