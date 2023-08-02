package it.unibs.mao.optalg.mkfsp;

import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBVar;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

public class GRASP {

    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_LOCAL_SEARCH_ITERATIONS = 20;

    public static Solution grasp(Instance instance, int numIterations) throws GRBException {
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

        return new Solution(bestSolution, bestObjectiveValue);
    }

    private static int[] constructivePhase(Instance instance, Random random) throws GRBException {
        int nItems = instance.nItems();
        int[] solution = new int[nItems];
        Arrays.fill(solution, -1);
        int nKnapsacks = instance.nKnapsacks();

        while (true) {
            boolean feasibleSolution = true;

            //seleziono una famiglia random
            int j = random.nextInt(instance.nFamilies() - 1);
            //ciclo su tutti gli item della famiglia
            for (int i = instance.firstItems()[j]; i < instance.firstItems()[j+1]; i++) {
                Set<Integer> availableKnapsacks = new HashSet<>();
                for (int k = 0; k < nKnapsacks; k++) {
                    availableKnapsacks.add(k);
                }
                boolean itemInserted = false;
                if (solution[i] == -1) {
                    while(!availableKnapsacks.isEmpty()) { //finchè non ho knapsack disponibili
                        int randomKnapsack = getRandomKnapsack(availableKnapsacks, random);
                        // Try inserting the item into the random knapsack, if it fits, break out of the loop
                        if (isAssignmentValid(instance, i, randomKnapsack, solution)) {
                            solution[i] = randomKnapsack;
                            itemInserted = true;
                            break;
                        } else {
                            availableKnapsacks.remove(randomKnapsack);
                        }
                    }
                    if (!itemInserted) {
                       //devo rimettere la soluzione come era prima
                        //devo anche uscire con un break immagino
                        feasibleSolution = false; //da vedere meglio anche questo
                    }
                }
            }

            if (!feasibleSolution) {
                break;
            }
        }

        return solution;
    }

    // Method to get a random knapsack from the available ones
    private static int getRandomKnapsack(Set<Integer> availableKnapsacks, Random random) {
        int size = availableKnapsacks.size();
        int randomIndex = random.nextInt(size);
        int currentIndex = 0;
        for (int knapsack : availableKnapsacks) {
            if (currentIndex == randomIndex) {
                return knapsack;
            }
            currentIndex++;
        }
        return -1; // In reality, this should never happen
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
            int isFamilySelected = 0;

            for (int item : familyItems) {
                if (item != -1) {
                    knapsackCounts[item]++;
                    isFamilySelected = 1;
                }
            }

            int numSplits = (int) Arrays.stream(knapsackCounts).filter(count -> count > 0).count() - 1;
            objectiveValue += instance.profits()[j] * isFamilySelected - instance.penalties()[j] * numSplits;
        }

        return objectiveValue;
    }

    //NON UTILIZZATO
    private static double calculateFamilySplitPenalty(Instance instance, int family, int[] solution) {
        int[] knapsackCounts = new int[instance.nKnapsacks()];
        int[] familyItems = Arrays.copyOfRange(solution, instance.firstItems()[family], instance.firstItems()[family + 1]);

        for (int item : familyItems) {
            if (item != -1) {
                knapsackCounts[item]++;
            }
        }

        int numSplits = (int) Arrays.stream(knapsackCounts).filter(count -> count > 0).count() - 1;
        return instance.penalties()[family] * numSplits;
    }
}
