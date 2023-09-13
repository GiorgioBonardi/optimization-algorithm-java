package it.unibs.mao.optalg.mkfsp.grasp;

import gurobi.GRBException;
import it.unibs.mao.optalg.mkfsp.Instance;
import it.unibs.mao.optalg.mkfsp.localsearch.LocalSearch;
import it.unibs.mao.optalg.mkfsp.localsearch.TabuSearch;
import java.util.*;

public class GRASP {

    private static final int MAX_LOCAL_SEARCH_ITERATIONS = 30;
    private static final int MAX_TABU_SEARCH_ITERATIONS = 20;
    private static final double BETA_RCL = 0.3;
    private static boolean localsearchIsUsed = true;
    private static boolean tabusearchIsUsed = false;
    public static Solution grasp(Instance instance, int numIterations) throws GRBException {

        int nItems = instance.nItems();
        int[] bestSolution = new int[nItems];
        double bestObjectiveValue = Double.NEGATIVE_INFINITY;
        Random random = new Random();
        long startTime = System.nanoTime();

        int[] solutionConstructivePhase = new int[0];
        for (int iteration = 0; iteration < numIterations; iteration++) {
            solutionConstructivePhase = constructivePhase(instance, random);


            int[] solutionLocalSearch = LocalSearch.run(instance, solutionConstructivePhase, random, MAX_LOCAL_SEARCH_ITERATIONS);
            //solution = TabuSearch.run(instance, solution, random, MAX_TABU_SEARCH_ITERATIONS);

            double objectiveValueConstructivePhase = Utils.calculateObjectiveValue(instance, solutionConstructivePhase);
            double objectiveValueLocalSearch = Utils.calculateObjectiveValue(instance, solutionLocalSearch);

            System.out.println("---------- ITERAZIONE #" + (iteration+1) + " -------------");
            if(!Arrays.toString(solutionConstructivePhase).equalsIgnoreCase(Arrays.toString(solutionLocalSearch))) {
                System.out.println("Soluzione phase 1: " + Arrays.toString(solutionConstructivePhase));
                System.out.println("Soluzione LocalS.: " + Arrays.toString(solutionLocalSearch));
            }
            System.out.println("Soluzione phase 1: " + Arrays.toString(solutionConstructivePhase));

            if (objectiveValueLocalSearch > bestObjectiveValue) {
                bestObjectiveValue = objectiveValueLocalSearch;
                bestSolution = solutionLocalSearch.clone();
            }

        }

        long endTime = System.nanoTime();
        double elapsedTimeInSeconds = (endTime - startTime) / 1e9;

        System.out.println("---------- FINE ISTANZA -------------");

        return new Solution(bestSolution, bestObjectiveValue, elapsedTimeInSeconds, BETA_RCL, numIterations, MAX_LOCAL_SEARCH_ITERATIONS);
    }

    private static int[] constructivePhase(Instance instance, Random random) {
        int nItems = instance.nItems();
        int[] solution = new int[nItems];
        Arrays.fill(solution, -1);

        int nKnapsacks = instance.nKnapsacks();

        Set<Integer> availableFamily = new HashSet<>();
        for (int j = 0; j < instance.nFamilies(); j++) {
            availableFamily.add(j);
        }

        while (!availableFamily.isEmpty()) {
            //seleziono una famiglia random dalla lista RCL
            List<Integer> rcl = buildRCL(instance, availableFamily, BETA_RCL);
            int randomFamily = getRandomFamilyFromRCL(rcl, random);

            //ciclo su tutti gli item della famiglia
            int endItem = (randomFamily == instance.nFamilies() - 1) ? nItems: instance.firstItems()[randomFamily+1];

            int[] prevSolution = new int[nItems];
            System.arraycopy(solution, 0, prevSolution, 0, solution.length);

            int prevKnapsack = -1;
            for (int i = instance.firstItems()[randomFamily]; i < endItem; i++) {
                Set<Integer> availableKnapsacks = new HashSet<>();
                for (int k = 0; k < nKnapsacks; k++) {
                    availableKnapsacks.add(k);
                }
                boolean itemInserted = false;
                //if (solution[i] == -1) {
                    while(!availableKnapsacks.isEmpty()) {
                        /*
                        Cerco di mettere l'item nello stesso knapsack di prima in modo da diminuire le penalità
                        avendo gli item della stessa famiglia nello stesso knapsack
                         */
                        int randomKnapsack = (prevKnapsack != -1 && availableKnapsacks.size() == nKnapsacks) ? prevKnapsack : getRandomKnapsack(availableKnapsacks, random);

                        // Try inserting the item into the random knapsack, if it fits, break out of the loop
                        if (Utils.isAssignmentValid(instance, i, randomKnapsack, solution)) {
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
                        solution = prevSolution;
                        availableFamily.remove(randomFamily);
                        break;
                    }

                    if (i == endItem - 1 && itemInserted) {
                        availableFamily.remove(randomFamily);
                        break;
                    }
                //}
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

    private static int getRandomFamily(Set<Integer> availableFamily, Random random) {
        int size = availableFamily.size();
        int randomIndex = random.nextInt(size);
        int currentIndex = 0;
        for (int family : availableFamily) {
            if (currentIndex == randomIndex) {
                return family;
            }
            currentIndex++;
        }
        return -1; // In reality, this should never happen
    }

    private static List<Integer> buildRCL(Instance instance, Set<Integer> availableFamily, double beta) {
        List<Integer> rcl = new ArrayList<>();

        /*
        Random random = new Random();
        int strategy = random.nextInt(2);

        List<Integer> rankedFamilies = (strategy == 0) ? Utils.rankFamiliesByPenalties(instance, availableFamily) : Utils.rankFamiliesByProfits(instance, availableFamily);
        */

        //List<Integer> rankedFamilies = Utils.rankFamiliesByRatioProfitOverPenality(instance, availableFamily);
        List<Integer> rankedFamilies = Utils.rankFamiliesByPenalties(instance, availableFamily);

        int numBestElements = (int) (rankedFamilies.size() * beta);
        numBestElements = numBestElements > 0 ? numBestElements : 1;

        if (numBestElements == 1 && rankedFamilies.size() > 2) {
            numBestElements = 2;
        }

        for (int i = 0; i < numBestElements; i++) {
            rcl.add(rankedFamilies.get(i));
        }
        return rcl;
    }


    private static int getRandomFamilyFromRCL(List<Integer> rcl, Random random) {
        int size = rcl.size();
        int randomIndex = random.nextInt(size);
        return rcl.get(randomIndex);
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
