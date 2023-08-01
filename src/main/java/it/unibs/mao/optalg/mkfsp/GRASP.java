package it.unibs.mao.optalg.mkfsp;

import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBVar;

import java.util.Arrays;
import java.util.Random;

public class GRASP {

    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_LOCAL_SEARCH_ITERATIONS = 20;

    public static int[] grasp(Instance instance, int numIterations) throws GRBException {
        int nItems = instance.nItems();
        int[] bestSolution = new int[nItems];
        double bestObjectiveValue = Double.NEGATIVE_INFINITY;
        Random random = new Random();

        for (int iteration = 0; iteration < numIterations; iteration++) {
            int[] solution = constructivePhase(instance, random);
            solution = localSearch(instance, solution, random);

            double objectiveValue = calculateObjectiveValue(instance, solution);
            if (objectiveValue > bestObjectiveValue) {
                bestObjectiveValue = objectiveValue;
                bestSolution = solution.clone();
            }
        }

        return bestSolution;
    }

    private static int[] constructivePhase(Instance instance, Random random) throws GRBException {
        int nItems = instance.nItems();
        int[] solution = new int[nItems];
        Arrays.fill(solution, -1);

        while (true) {
            boolean feasibleSolution = false;
            int j = random.nextInt(instance.nFamilies() - 1);

            for (int i = instance.firstItems()[j]; i < nItems; i++) {
                if (solution[i] == -1) {
                    int k = random.nextInt(instance.nKnapsacks());
                    double cost = calculateFamilySplitPenalty(instance, j, solution);
                    if (isAssignmentValid(instance, i, k, solution)) {
                        solution[i] = k;
                        solution = localSearch(instance, solution, random);
                        feasibleSolution = true;
                        break;
                    }
                }
            }

            if (!feasibleSolution) {
                break;
            }
        }

        return solution;
    }

    private static boolean isAssignmentValid(Instance instance, int i, int k, int[] solution) {
        int nResources = instance.nResources();
        int[][] items = instance.items();
        int[] knapsackCapacity = instance.knapsacks()[k];
        int[] usedResources = new int[nResources];

        for (int item = 0; item < solution.length; item++) {
            if (solution[item] == k) {
                for (int r = 0; r < nResources; r++) {
                    usedResources[r] += items[item][r];
                }
            }
        }

        for (int r = 0; r < nResources; r++) {
            if (usedResources[r] + items[i][r] > knapsackCapacity[r]) {
                return false;
            }
        }

        return true;
    }

    private static int[] localSearch(Instance instance, int[] initialSolution, Random random) throws GRBException {
        int nItems = instance.nItems();
        int[] currentSolution = initialSolution.clone();
        double currentObjectiveValue = calculateObjectiveValue(instance, currentSolution);

        for (int iteration = 0; iteration < MAX_LOCAL_SEARCH_ITERATIONS; iteration++) {
            int i = random.nextInt(nItems);
            int k = random.nextInt(instance.nKnapsacks());

            if (isAssignmentValid(instance, i, k, currentSolution)) {
                int[] newSolution = currentSolution.clone();
                newSolution[i] = k;
                double newObjectiveValue = calculateObjectiveValue(instance, newSolution);

                if (newObjectiveValue > currentObjectiveValue) {
                    currentSolution = newSolution;
                    currentObjectiveValue = newObjectiveValue;
                }
            }
        }

        return currentSolution;
    }

    private static double calculateObjectiveValue(Instance instance, int[] solution) {
        double objectiveValue = 0.0;

        for (int j = 0; j < instance.nFamilies() - 1; j++) {
            int familySize = instance.firstItems()[j + 1] - instance.firstItems()[j];
            int[] knapsackCounts = new int[instance.nKnapsacks()];
            int[] familyItems = Arrays.copyOfRange(solution, instance.firstItems()[j], instance.firstItems()[j + 1]);

            for (int item : familyItems) {
                if (item != -1) {
                    knapsackCounts[item]++;
                }
            }

            int numSplits = (int) Arrays.stream(knapsackCounts).filter(count -> count > 0).count() - 1;
            objectiveValue += instance.profits()[j] - instance.penalties()[j] * numSplits;
        }

        return objectiveValue;
    }

    private static double calculateFamilySplitPenalty(Instance instance, int family, int[] solution) {
        int[] knapsackCounts = new int[instance.nKnapsacks()];
        int[] familyItems = Arrays.copyOfRange(solution, instance.firstItems()[family], instance.firstItems()[family + 1]);

        for (int item : familyItems) {
            if (item != -1) {
                knapsackCounts[item]++;
            }
        }

        int numSplits = (int) Arrays.stream(knapsackCounts).filter(count -> count > 0).count();
        return instance.penalties()[family] * numSplits;
    }
}
