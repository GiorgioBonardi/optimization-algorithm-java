package it.unibs.mao.optalg.mkfsp.grasp;

import gurobi.GRBException;
import it.unibs.mao.optalg.mkfsp.Instance;

import java.util.*;

public class GRASP {

    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_LOCAL_SEARCH_ITERATIONS = 20;
    private static final int MAX_TABU_SEARCH_ITERATIONS = 20;

    public static Solution grasp(Instance instance, int numIterations) throws GRBException {
        int nItems = instance.nItems();
        int[] bestSolution = new int[nItems];
        double bestObjectiveValue = Double.NEGATIVE_INFINITY;
        Random random = new Random();
        long startTime = System.nanoTime();

        for (int iteration = 0; iteration < numIterations; iteration++) {
            int[] solution = constructivePhase(instance, random);
            //solution = localSearch(instance, solution, random);

            solution = tabuSearch(instance, solution, random);

            double objectiveValue = calculateObjectiveValue(instance, solution);
            if (objectiveValue > bestObjectiveValue) {
                bestObjectiveValue = objectiveValue;
                bestSolution = solution.clone();
            }

        }

        long endTime = System.nanoTime();
        double elapsedTimeInSeconds = (endTime - startTime) / 1e9;

        return new Solution(bestSolution, bestObjectiveValue, elapsedTimeInSeconds);
    }

    private static int[] constructivePhase(Instance instance, Random random) throws GRBException {
        int nItems = instance.nItems();
        int[] solution = new int[nItems];
        Arrays.fill(solution, -1);
        int nKnapsacks = instance.nKnapsacks();

        Set<Integer> avaiableFamily = new HashSet<>();
        for (int j = 0; j < instance.nFamilies(); j++) {
            avaiableFamily.add(j);
        }

        while (!avaiableFamily.isEmpty()) {
            //seleziono una famiglia random
            int j = getRandomFamily(avaiableFamily, random);
            //ciclo su tutti gli item della famiglia
            int endItem = (j == instance.nFamilies() - 1) ? nItems: instance.firstItems()[j+1];

            int[] solutionPre = new int[nItems];
            System.arraycopy(solution, 0, solutionPre, 0, solution.length);

            int prevKnapsack = -1;
            for (int i = instance.firstItems()[j]; i < endItem; i++) {
                Set<Integer> availableKnapsacks = new HashSet<>();
                for (int k = 0; k < nKnapsacks; k++) {
                    availableKnapsacks.add(k);
                }
                boolean itemInserted = false;
                if (solution[i] == -1) {
                    while(!availableKnapsacks.isEmpty()) { //finchè non ho knapsack disponibili
                        /*
                        Cerco di mettere l'item nello stesso knapsack di prima in modo da diminuire le penalità
                        avendo gli item della stessa famiglia nello stesso knapsack
                         */
                        int randomKnapsack = (prevKnapsack != -1 && availableKnapsacks.size() == nKnapsacks) ? prevKnapsack : getRandomKnapsack(availableKnapsacks, random);

                        // Try inserting the item into the random knapsack, if it fits, break out of the loop
                        if (isAssignmentValid(instance, i, randomKnapsack, solution)) {
                            solution[i] = randomKnapsack;
                            itemInserted = true;
                            prevKnapsack = randomKnapsack;
                            break;
                        } else {
                            availableKnapsacks.remove(randomKnapsack);
                        }
                    }
                    if (!itemInserted) {
                       //devo rimettere la soluzione come era prima
                        solution = solutionPre;
                        avaiableFamily.remove(j);
                        break;
                    }

                    //controllo se è l'ultimo elemento della famiglia
                    if (i == endItem - 1 && itemInserted) {
                        avaiableFamily.remove(j);
                        break;
                    }
                }
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

    private static int getRandomFamily(Set<Integer> avaiableFamily, Random random) {
        int size = avaiableFamily.size();
        int randomIndex = random.nextInt(size);
        int currentIndex = 0;
        for (int family : avaiableFamily) {
            if (currentIndex == randomIndex) {
                return family;
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

        final int nItems = instance.nItems();
        final int nFamilies = instance.nFamilies();
        final int nKnapsacks = instance.nKnapsacks();
        final int nResources = instance.nResources();
        final int[] firstItems = instance.firstItems();

        final Map<Integer,Set<Integer>> splits = new HashMap<>();
        final int[][] usedResources = new int[nKnapsacks][nResources];
        for (int j = 0; j < nFamilies; ++j) {
            final int firstItem = firstItems[j];
            final int endItem = j+1 < nFamilies ? firstItems[j+1] : nItems;
            int itemCount = 0;
            for (int i = firstItem; i < endItem; ++i) {
                final int k = solution[i];
                if (-1 < k && k < nKnapsacks) {
                    itemCount += 1;
                    for (int r = 0; r < nResources; ++r) {
                        usedResources[k][r] += instance.items()[i][r];
                    }
                    splits.computeIfAbsent(j, (key) -> new HashSet<>()).add(k);
                }
            }

            final int familySize = endItem - firstItem;
            if (itemCount == familySize) {
                objectiveValue += instance.profits()[j] - instance.penalties()[j] * (splits.get(j).size() - 1);
            }
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
    //TODO: DA CONTROLLARE
    private static int[] tabuSearch(Instance instance, int[] initialSolution, Random random) throws GRBException {
        int[] bestSolution = initialSolution.clone();
        double bestObjectiveValue = calculateObjectiveValue(instance, bestSolution);

        int nItems = instance.nItems();
        int tabuListSize = nItems / 2; // Dimensione lista tabu (puoi sperimentare con diverse dimensioni)
        List<int[]> tabuList = new ArrayList<>();

        for (int iteration = 0; iteration < MAX_TABU_SEARCH_ITERATIONS; iteration++) {
            List<int[]> candidateSolutions = new ArrayList<>();

            for (int i = 0; i < nItems; i++) {
                int currentItemFamily = findItemFamily(instance, i);
                for (int k = 0; k < instance.nKnapsacks(); k++) {
                    if (isAssignmentValid(instance, i, k, bestSolution) &&
                            (currentItemFamily == -1 || isWholeFamilyAssigned(instance, currentItemFamily, k, bestSolution)) &&
                            !isInTabuList(i, k, tabuList)) {
                        int[] newSolution = bestSolution.clone();
                        newSolution[i] = k;
                        double newObjectiveValue = calculateObjectiveValue(instance, newSolution);

                        candidateSolutions.add(newSolution);
                    }
                }
            }

            if (candidateSolutions.isEmpty()) {
                break; // Nessuna soluzione candidata valida
            }

            int[] nextSolution = Collections.max(candidateSolutions, Comparator.comparingDouble(sol -> calculateObjectiveValue(instance, sol)));

            tabuList.add(nextSolution);
            if (tabuList.size() > tabuListSize) {
                tabuList.remove(0); // Rimuovi la soluzione più vecchia dalla lista tabu
            }

            double nextObjectiveValue = calculateObjectiveValue(instance, nextSolution);
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
