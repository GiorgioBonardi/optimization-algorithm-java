package it.unibs.mao.optalg.mkfsp.grasp;

import it.unibs.mao.optalg.mkfsp.FeasibilityCheck;
import it.unibs.mao.optalg.mkfsp.Instance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class GRASP {
    private static final double TIME_LIMIT_GRASP = 200000; //milliseconds
    private static final long STALE_TIME = 30000; //milliseconds

    private static final double DEFAULT_TIME_LIMIT_GUROBI = 600; //seconds

    private static final double[] BETA_LIST = {0.1, 0.2, 0.3};
    private static KnapsacksResource knapRes = null;

    public static Solution grasp(Instance instance, Path outputDir) throws RuntimeException, IOException {
        long startTime = System.currentTimeMillis();
        int[] solutionConstructivePhase = new int[0];

        long timer = System.currentTimeMillis();
        long timerStale = timer;

        int[] bestSolConstructivePhase = new int[0];
        double bestObjectiveConstructivePhase = Double.NEGATIVE_INFINITY;
        double elapsedTimeMillis = System.currentTimeMillis() - timer;

        Random random = new Random();

        while(elapsedTimeMillis < TIME_LIMIT_GRASP && (System.currentTimeMillis() - timerStale) < STALE_TIME) {
            solutionConstructivePhase = constructivePhase(instance, random);
            double objectiveValueConstructivePhase = Utils.calculateObjectiveValue(instance, solutionConstructivePhase);

            if(objectiveValueConstructivePhase > bestObjectiveConstructivePhase) {
                bestObjectiveConstructivePhase = objectiveValueConstructivePhase;
                bestSolConstructivePhase = solutionConstructivePhase;
                timerStale = System.currentTimeMillis();
            }

            elapsedTimeMillis = System.currentTimeMillis() - timer;
        }

        //Feasibility check
        final FeasibilityCheck check = instance.checkFeasibility(bestSolConstructivePhase, bestObjectiveConstructivePhase);

        if (!check.isValid()) {
            for (final String errMsg: check.errorMessages()) {
                System.out.println("  - " + errMsg);
            }
            throw new RuntimeException("Solution found by GRASP not feasible");
        }

        double additionalSeconds = (TIME_LIMIT_GRASP - elapsedTimeMillis) / 1000;

        if(System.currentTimeMillis() - timerStale >= STALE_TIME) {
            System.out.println("GRASP ended because of STALE Iterations! Additional time to Gurobi: " + additionalSeconds);
        } else {
            System.out.println("GRASP ended because of time limits");
        }

        System.out.println("Obj Value GRASP: " + bestObjectiveConstructivePhase);

        double totalTimeLimitGurobi = DEFAULT_TIME_LIMIT_GUROBI + additionalSeconds;

        HashMap<Integer, Integer> splitForFamilies = Utils.calculateSplitForEachFamily(instance, bestSolConstructivePhase);

        int[] solutionGurobiSearch = GurobiSearch.run(instance, bestSolConstructivePhase, totalTimeLimitGurobi, splitForFamilies, outputDir);
        double objectiveGurobiSearch = Utils.calculateObjectiveValue(instance, solutionGurobiSearch);
        System.out.println("SOL TROVATA DA GUROBI: " + objectiveGurobiSearch);

        long endTime = System.currentTimeMillis();
        double elapsedTimeInSeconds = (double) (endTime - startTime) / 1000;

        return new Solution(solutionGurobiSearch, objectiveGurobiSearch, elapsedTimeInSeconds);
    }
    private static int[] constructivePhase(Instance instance, Random random) {
        int nItems = instance.nItems();
        int nKnapsacks = instance.nKnapsacks();
        int nFamilies = instance.nFamilies();
        int[] solution = new int[nItems];

        // Initialization of 'knapRes'
        knapRes = new KnapsacksResource(nKnapsacks);
        for (int i = 0; i < nKnapsacks; i++) {
            int[] knapsackValue = new int[instance.nResources()];
            System.arraycopy(instance.knapsacks()[i], 0, knapsackValue, 0, instance.knapsacks()[i].length);
            knapRes.setResources(i, knapsackValue);
        }

        Set<Integer> availableFamily = new HashSet<>();
        for (int j = 0; j < nFamilies; j++) {
            availableFamily.add(j);
        }

        //Select random BETA
        int randomIndex = random.nextInt(BETA_LIST.length);
        double BETA_RCL = BETA_LIST[randomIndex];
        
        Arrays.fill(solution, -1);

        List<Integer> sortedFamilyList = Utils.sortFamiliesByPenalties(instance, availableFamily);

        while (!sortedFamilyList.isEmpty()) {
            List<Integer> rcl = buildRCL(sortedFamilyList, BETA_RCL);
            int randomFamilyIndex = random.nextInt(rcl.size());
            int randomFamily = rcl.get(randomFamilyIndex);

            int firstItem = instance.firstItems()[randomFamily];
            int endItem = (randomFamily == instance.nFamilies() - 1) ? nItems: instance.firstItems()[randomFamily+1];

            Set<Integer> itemsToInsert = new HashSet<>();
            for(int i=firstItem; i < endItem; i++) {
                itemsToInsert.add(i);
            }

            int[] prevSolution = new int[nItems];
            System.arraycopy(solution, 0, prevSolution, 0, solution.length);

            //calcolo Njr
            int[] necessaryResources = new int[instance.nResources()];
            for(int r = 0; r < instance.nResources(); r++) {
                for(int i=firstItem; i < endItem; i++) {
                    necessaryResources[r] += instance.items()[i][r];
                }
            }

            solution = recursiveFitFamily(instance, randomFamily, itemsToInsert, necessaryResources, solution, random);
            //Alternative to the recursive method, same performance
            //solution = iterativeFitFamily(instance, randomFamily, itemsToInsert, necessaryResources, solution, random);
            if(solution == null) {
                solution = prevSolution;
            }

            sortedFamilyList.remove(randomFamilyIndex);
        }

        return solution;
    }


    private static int[] recursiveFitFamily(Instance instance, int family, Set<Integer> itemsToInsert, int[] necessaryResources, int[] solution, Random random) {
        //First you try to insert the problematic item into an used knapsack
        ArrayList<Integer> knapsackUsed = getKnapsackUsedForFamily(instance, solution, family);
        int knapsackForWholeFamily = findKnapsackToFitWholeFamily(instance, necessaryResources, knapsackUsed, random);

        if (knapsackForWholeFamily == -1) {
            ArrayList<Integer> availableKnapsacks = new ArrayList<>();
            for (int k = 0; k < instance.nKnapsacks(); k++) {
                availableKnapsacks.add(k);
            }
            knapsackForWholeFamily = findKnapsackToFitWholeFamily(instance, necessaryResources, availableKnapsacks, random);

        }
        if(knapsackForWholeFamily != -1) {
            for (int i : itemsToInsert) {
                solution[i] = knapsackForWholeFamily;
                //update residual capacity
                knapRes.removeResources(instance.items()[i], knapsackForWholeFamily);
            }
            return solution;
        }

        if(itemsToInsert.size() == 1)
            return null;

        int[] indexes = findMostProblematicItem(instance, itemsToInsert, necessaryResources);
        int maxItemIndex = indexes[0];
        int maxResourceIndex = indexes[1];

        int selectedKnapsack = findBestKnapsackForProblematicItem(instance, maxItemIndex, maxResourceIndex);

        if (selectedKnapsack == -1) {
            return null;
        } else {
            //Update solution and resources
            solution[maxItemIndex] = selectedKnapsack;
            itemsToInsert.remove(maxItemIndex);

            for(int r = 0; r < instance.nResources(); r++){
                necessaryResources[r] -= instance.items()[maxItemIndex][r];
            }
            knapRes.removeResources(instance.items()[maxItemIndex], selectedKnapsack);

            return recursiveFitFamily(instance, family, itemsToInsert, necessaryResources, solution, random);
        }
    }

    private static int[] iterativeFitFamily(Instance instance, int family, Set<Integer> itemsToInsert, int[] necessaryResources, int[] solution, Random random) {
        do {
            //First you try to insert the problematic item into a used knapsack
            ArrayList<Integer> knapsackUsed = getKnapsackUsedForFamily(instance, solution, family);
            int knapsackForWholeFamily = findKnapsackToFitWholeFamily(instance, necessaryResources, knapsackUsed, random);

            if (knapsackForWholeFamily == -1) {
                ArrayList<Integer> availableKnapsacks = new ArrayList<>();
                for (int k = 0; k < instance.nKnapsacks(); k++) {
                    availableKnapsacks.add(k);
                }
                knapsackForWholeFamily = findKnapsackToFitWholeFamily(instance, necessaryResources, availableKnapsacks, random);

            }
            if (knapsackForWholeFamily != -1) {
                for (int i : itemsToInsert) {
                    solution[i] = knapsackForWholeFamily;
                    //update residual capacity
                    knapRes.removeResources(instance.items()[i], knapsackForWholeFamily);
                }
                break;
            } else if (itemsToInsert.size() == 1) {
                return null;
            } else {
                int[] indexes = findMostProblematicItem(instance, itemsToInsert, necessaryResources);
                int maxItemIndex = indexes[0];
                int maxResourceIndex = indexes[1];

                int selectedKnapsack = findBestKnapsackForProblematicItem(instance, maxItemIndex, maxResourceIndex);

                if (selectedKnapsack == -1) {
                    return null;
                } else {
                    //Update solution and resources
                    solution[maxItemIndex] = selectedKnapsack;
                    itemsToInsert.remove(maxItemIndex);

                    for (int r = 0; r < instance.nResources(); r++) {
                        necessaryResources[r] -= instance.items()[maxItemIndex][r];
                    }
                    knapRes.removeResources(instance.items()[maxItemIndex], selectedKnapsack);
                }
            }
        } while (!itemsToInsert.isEmpty());
        return solution;
    }

    /**
     *
     * @param instance
     * @param itemsToInsert
     * @param necessaryResources
     * @return An array of 2 item where the first one is the index of the problematic item and the second one is the index of the resources
     */
    private static int[] findMostProblematicItem(Instance instance, Set<Integer> itemsToInsert, int[] necessaryResources) {
        //trova il max tra ri/Nji
        int[] indexes = new int[2];

        int maxItemIndex = -1;
        int maxResourceIndex = -1;
        double maxValue = 0;
        for(int r = 0; r < instance.nResources(); r++) {
            //ALTERNATIVE 1:
            for (int i : itemsToInsert) {
                double currentValue = (double) instance.items()[i][r] / necessaryResources[r];
                if(currentValue > maxValue) {
                    maxValue = currentValue;
                    maxItemIndex = i;
                    maxResourceIndex = r;
                }
            }


            //ALTERNATIVE 2:
            //double currentValue = (double) instance.items()[i][r] / resCapacity[k][r];
            /*for (int i : itemsToInsert) {
                for (int k = 0; k < nKnapsacks; k++) {
                    double currentValue = (double) instance.items()[i][r] / knapRes.getResources().get(k)[r];
                    if (currentValue > maxValue) {
                        maxValue = currentValue;
                        maxItemIndex = i;
                        maxResourceIndex = r;
                    }
                }
            }
             */
        }
        indexes[0] = maxItemIndex;
        indexes[1] = maxResourceIndex;
        return indexes;
    }


    private static int findBestKnapsackForProblematicItem(Instance instance, int maxItemIndex, int maxResourceIndex) {
        int minKnapsack = -1;
        int minGap =  Integer.MAX_VALUE;
        int mostDangerousResource = instance.items()[maxItemIndex][maxResourceIndex];

        for(int k = 0; k < instance.nKnapsacks(); k++){
            boolean fit = true;
            int currentGap = knapRes.getResources().get(k)[maxResourceIndex] - mostDangerousResource;

            if(currentGap < minGap && currentGap >= 0){
                for(int r = 0; r < instance.nResources(); r++){
                    if(r != maxResourceIndex){
                        if(knapRes.getResources().get(k)[r] < instance.items()[maxItemIndex][r]){
                            fit = false;
                        }
                    }
                }
                if(fit){
                    minGap = currentGap;
                    minKnapsack = k;
                }
            }
        }
        return minKnapsack;
    }


    private static int findKnapsackToFitWholeFamily(Instance instance, int[] necessaryResources, List<Integer> availableKnapsacks, Random random) {
        while(!availableKnapsacks.isEmpty()) {
            int randomIndex = random.nextInt(availableKnapsacks.size());
            int randomKnapsack = availableKnapsacks.get(randomIndex);

            int[] knapsackCapacity = knapRes.getResources().get(randomKnapsack);

            if (wholeFamilyFits(instance, necessaryResources, knapsackCapacity)) {
                return randomKnapsack;
            } else {
                availableKnapsacks.remove(randomIndex);
            }
        }
        return -1;
    }

    private static boolean wholeFamilyFits(Instance instance, int[] necessaryResources, int[] knapsackCapacity) {
        for (int r = 0; r < instance.nResources(); r++) {
            if (necessaryResources[r] > knapsackCapacity[r]) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> buildRCL(List<Integer> sortedFamilyList, double beta) {
        int numBestElements = (int) (sortedFamilyList.size() * beta);
        numBestElements = numBestElements > 0 ? numBestElements : 1;

        if (numBestElements == 1 && sortedFamilyList.size() > 2) {
            numBestElements = 2;
        }

        return new ArrayList<>(sortedFamilyList.subList(0, numBestElements));
    }

    private static ArrayList<Integer> getKnapsackUsedForFamily(Instance instance, int[] solution, int family) {
        int startItem = instance.firstItems()[family];
        int endItem = (family == instance.nFamilies() - 1) ? instance.nItems(): instance.firstItems()[family+1];

        ArrayList<Integer> knapsackUsed = new ArrayList<>();
        for (int i = startItem; i < endItem; i++) {
            if (solution[i] != -1) {
                knapsackUsed.add(solution[i]);
            }
        }
        return knapsackUsed;
    }
}
