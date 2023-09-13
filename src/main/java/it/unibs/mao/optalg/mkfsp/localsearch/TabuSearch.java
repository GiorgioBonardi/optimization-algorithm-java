package it.unibs.mao.optalg.mkfsp.localsearch;

import gurobi.GRBException;
import it.unibs.mao.optalg.mkfsp.Instance;
import it.unibs.mao.optalg.mkfsp.grasp.Utils;

import java.util.*;

public class TabuSearch {

    public static int[] run(Instance instance, int[] initialSolution, Random random, int totalIterations) throws GRBException {
        int[] bestSolution = initialSolution.clone();
        double bestObjectiveValue = Utils.calculateObjectiveValue(instance, bestSolution);

        int nItems = instance.nItems();
        int tabuListSize = nItems / 2; // Dimensione lista tabu (puoi sperimentare con diverse dimensioni)
        List<int[]> tabuList = new ArrayList<>();

        for (int iteration = 0; iteration < totalIterations; iteration++) {
            List<int[]> candidateSolutions = new ArrayList<>();

            for (int i = 0; i < nItems; i++) {
                int currentItemFamily = findItemFamily(instance, i);
                for (int k = 0; k < instance.nKnapsacks(); k++) {
                    if (Utils.isAssignmentValid(instance, i, k, bestSolution) &&
                            (currentItemFamily == -1 || isWholeFamilyAssigned(instance, currentItemFamily, k, bestSolution)) &&
                            !isInTabuList(i, k, tabuList)) {
                        int[] newSolution = bestSolution.clone();
                        newSolution[i] = k;
                        double newObjectiveValue = Utils.calculateObjectiveValue(instance, newSolution);

                        candidateSolutions.add(newSolution);
                    }
                }
            }

            if (candidateSolutions.isEmpty()) {
                break; // Nessuna soluzione candidata valida
            }

            int[] nextSolution = Collections.max(candidateSolutions, Comparator.comparingDouble(sol -> Utils.calculateObjectiveValue(instance, sol)));

            tabuList.add(nextSolution);
            if (tabuList.size() > tabuListSize) {
                tabuList.remove(0); // Rimuovi la soluzione più vecchia dalla lista tabu
            }

            double nextObjectiveValue = Utils.calculateObjectiveValue(instance, nextSolution);
            if (nextObjectiveValue > bestObjectiveValue) {
                bestSolution = nextSolution;
                bestObjectiveValue = nextObjectiveValue;
            }
        }

        return bestSolution;
    }

    private static int findItemFamily(Instance instance, int item) {
        for (int j = 0; j < instance.nFamilies(); j++) {
            int familyStart = instance.firstItems()[j];
            int familyEnd = (j + 1 < instance.nFamilies()) ? instance.firstItems()[j + 1] : instance.nItems();
            if (item >= familyStart && item < familyEnd) {
                return j;
            }
        }
        return -1; // L'elemento non appartiene a nessuna famiglia
    }

    private static boolean isWholeFamilyAssigned(Instance instance, int family, int knapsack, int[] solution) {
        int familyStart = instance.firstItems()[family];
        int familyEnd = (family + 1 < instance.nFamilies()) ? instance.firstItems()[family + 1] : instance.nItems();
        for (int i = familyStart; i < familyEnd; i++) {
            if (solution[i] != knapsack) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInTabuList(int i, int k, List<int[]> tabuList) {
        for (int[] solution : tabuList) {
            if (solution[i] == k) {
                return true;
            }
        }
        return false;
    }
}
