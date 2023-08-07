package it.unibs.mao.optalg.mkfsp.separationProblem;

import gurobi.*;
import it.unibs.mao.optalg.mkfsp.Instance;
import it.unibs.mao.optalg.mkfsp.separationProblem.SeparationProblemModel;
import it.unibs.mao.optalg.mkfsp.separationProblem.SeparationProblemModelVars;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainSeparationProblem {

    public static final Path INSTANCES_DIR = Path.of("instances");

    public static void main(String[] args) {
        try {
            // Load the instance
            Instance instance = Instance.load(Path.of(INSTANCES_DIR + "/example.json"));

            // Crea l'ambiente Gurobi
            GRBEnv env = new GRBEnv();
            env.set(GRB.IntParam.OutputFlag, 0); // Disabilita gli output di Gurobi

            // Solve the Separation Problem and get the S for each knapsack
            double eps = 1; // Set the epsilon value for the Separation Problem
            Map<Integer, Set<Integer>> separationProblemSolutions = new HashMap<>();
            for (int k = 0; k < instance.nKnapsacks(); ++k) {
                SeparationProblemModelVars separationVars = SeparationProblemModel.build(instance, env, eps, k);
                GRBModel separationModel = separationVars.getModel();
                separationModel.optimize();

                // Get the selected items for knapsack k
                Set<Integer> S = new HashSet<>();
                GRBVar[] zValues = separationVars.getZVars();
                for (int j = 0; j < instance.nItems(); ++j) {
                    if (zValues[j].get(GRB.DoubleAttr.X) > 0.5) { //Controlla questa riga
                        S.add(j);
                    }
                }
                separationProblemSolutions.put(k, S);
            }

            // Add the constraints to the main model based on the solutions from the Separation Problem
            // You can modify the ModelVars class to include the main model and add the constraints there
            System.out.println(separationProblemSolutions);
            // Clean up
            env.dispose();
        } catch (IOException | GRBException e) {
            e.printStackTrace();
        }
    }
}

