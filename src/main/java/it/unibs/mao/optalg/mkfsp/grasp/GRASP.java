package it.unibs.mao.optalg.mkfsp.grasp;

import it.unibs.mao.optalg.mkfsp.FeasibilityCheck;
import it.unibs.mao.optalg.mkfsp.Instance;
import it.unibs.mao.optalg.mkfsp.localsearch.GurobiSearch;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GRASP {
    private static final double TIME_LIMIT_GRASP = 200000; //milliseconds
    private static final long STALE_TIME = 30000; //milliseconds

    private static final double DEFAULT_TIME_LIMIT_GUROBI = 600; //seconds

    private static final double[] BETA_LIST = {0.1, 0.2, 0.3};
    private static KnapsacksResource knapRes = null;

    public static Solution grasp(Instance instance) throws RuntimeException, IOException {
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

        int[] solutionGurobiSearch = GurobiSearch.run(instance, bestSolConstructivePhase, totalTimeLimitGurobi, splitForFamilies);
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

        //int countInsertedFamilies = 0; //Used for defragmentation

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
            //solution = iterativeFitFamily(instance, randomFamily, itemsToInsert, necessaryResources, solution, random);
            if(solution == null) {
                solution = prevSolution;
            }

            /*
            //Defragmentation
            if(solution == null) {
                solution = prevSolution;
            }
            else {
                countInsertedFamilies++;
            }
            if (countInsertedFamilies == 3) {
                defragmentationKnapsack(instance, solution, knapRes);
                countInsertedFamilies = 0;
            }
             */

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
                    return null; //TODO: credo succeda "spesso". Non so se va bene fare throw di un expection "spesso"
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
                /*
                AL posto che fare /N_jr divido per la risorsa r_esima di ogni knapsack
                e prendo il valore maggiore
                 */
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

    private static boolean wholeFamilyFits( Instance instance, int[] necessaryResources, int[] knapsackCapacity) {
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

    private static int getRandomFamilyFromRCL(List<Integer> rcl, Random random) {
        int size = rcl.size();
        int randomIndex = random.nextInt(size);
        return rcl.get(randomIndex);
    }


    private static void defragmentationKnapsack(Instance instance, int[] solution, KnapsacksResource knapRes){
        //HashMap contenente numero di knapsack e lista di item che contiene
        HashMap<Integer, Set<Integer>> knapsackContent = new HashMap<Integer, Set<Integer>>();
        //knapsackFamilyOccurences contiene quante famiglie diverse contiene un knapsack (per ordinare i knapsack)
        HashMap<Integer, Integer> knapsackDistinctFamilies = new HashMap<Integer, Integer>();
        //knapsackUsed contiene gli indici degli item usati
        Set<Integer> knapsackUsed = new HashSet<Integer>();
        updateKnapsackUsed(knapsackUsed, solution);

        while (!knapsackUsed.isEmpty()) {
            int worstKnapsack = getWorstKnapsack(knapsackContent, knapsackDistinctFamilies, knapsackUsed, instance, solution, knapRes);
            fixKnapsack(knapsackContent, knapsackUsed, instance, solution, knapRes, worstKnapsack);
            knapsackUsed.remove(worstKnapsack);
        }
    }

    private static void updateKnapsackUsed(Set<Integer> knapsackUsed, int[] solution){
        //knapsack usati
        for (int i = 0; i < solution.length; i ++) {
            if (!knapsackUsed.contains(solution[i]) && solution[i] != -1) {
                knapsackUsed.add(solution[i]);
            }
        }
    }
    private static int getWorstKnapsack(HashMap<Integer, Set<Integer>> knapsackContent, HashMap<Integer, Integer> knapsackDistinctFamilies, Set<Integer> knapsackUsed, Instance instance, int[] solution, KnapsacksResource knapRes){
        knapsackContent.clear();
        knapsackDistinctFamilies.clear();
        //per ogni knapsack genero la lista di item che contiene
        for (int k : knapsackUsed) {
            Set<Integer> families = new HashSet<Integer>();
            Set<Integer> items = new HashSet<Integer>();
            int familyOccurences = 0;

            for (int j = 0; j < instance.nFamilies(); j++) {
                int startItem = instance.firstItems()[j];
                int endItem = j + 1 < instance.nFamilies() ? instance.firstItems()[j + 1] : instance.nItems();

                for (int i = startItem; i < endItem; i++) {
                    if(solution[i] == k){
                        if(!families.contains(j)){
                            familyOccurences++;
                        }
                        families.add(j);
                        items.add(i);
                    }
                }
            }
            knapsackContent.put(k, items);
            knapsackDistinctFamilies.put(k, familyOccurences);
        }
        //ora knapsackContent contiene tutti i knapsack con le corrispettive famiglie

        //ordino knapsackFamilyOccurences
        List<Integer> sortedKnapsacks = knapsackDistinctFamilies.entrySet()
                .stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Integer> orderedByDistinctFamilies = new ArrayList<>(sortedKnapsacks);

        //prendo il knapsack peggiore e i sui item
        return orderedByDistinctFamilies.get(0);
    }

    private static void fixKnapsack(HashMap<Integer, Set<Integer>> knapsackContent, Set<Integer> knapsackUsed, Instance instance, int[] solution, KnapsacksResource knapRes, int worstKnapsack){
        Set<Integer> itemsToMove = knapsackContent.get(worstKnapsack);

        while (!itemsToMove.isEmpty()) {
            int maxItemIndex = -1;
            double maxValue = 0;
            for (int r = 0; r < instance.nResources(); r++) {
                for (int i : itemsToMove) {
                    double currentValue = (double) instance.items()[i][r] / instance.knapsacks()[worstKnapsack][r];
                    if (currentValue > maxValue) {
                        maxValue = currentValue;
                        maxItemIndex = i;
                    }

                }
            }

            //ordino i knapsack in base a quanti parenti di maxItemIndex contengono
            //maxItemFamily è l'indice della famiglia di maxItemIndex
            int maxItemFamily = getFamilyFromItem(instance, maxItemIndex);

            HashMap<Integer, Integer> knapsackRelativesOccurences = new HashMap<Integer, Integer>();
            for (int k : knapsackUsed) {
                if (k != worstKnapsack) {
                    int countRelatives = 0;
                    for (int i : knapsackContent.get(k)) {
                        if (getFamilyFromItem(instance, i) == maxItemFamily) { //TODO: inserire codice estrai famiglie
                            countRelatives++;
                        }
                    }
                    if(countRelatives != 0) {
                        knapsackRelativesOccurences.put(k, countRelatives);
                    }
                }
            }

            //ordino i knapsack in base a quanti parenti di maxItemIndex contengono
            List<Integer> sortedByRelatives = knapsackRelativesOccurences.entrySet()
                    .stream()
                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            List<Integer> orderedByRelatives = new ArrayList<>(sortedByRelatives);

            //cerco di spostare maxItemIndex in un altro knapsack
            for (int k : orderedByRelatives) {
                boolean fit = true;

                for (int r = 0; r < instance.nResources(); r++) {
                    if(knapRes.getResources().get(k)[r] < instance.items()[maxItemIndex][r]) {
                        fit = false;
                        break;
                    }
                }
                if (fit) {
                    //System.out.println("ITEM: " + maxItemIndex + " from knapsack: " + worstKnapsack + " to knapsack: " + k);
                    //sposto maxItemIndex in k
                    solution[maxItemIndex] = k;
                    //aggiorno le risorse rimanenti
                    knapRes.removeResources(instance.items()[maxItemIndex], k);
                    knapRes.addResources(instance.items()[maxItemIndex], worstKnapsack);
                    break;
                }
            }
            itemsToMove.remove(maxItemIndex);
        }
    }

    private static int getFamilyFromItem(Instance instance, int itemIndex) {
        int familyIndex = instance.nFamilies() - 1;
        for (int i = 0; i < instance.firstItems().length; i++) {
            if (itemIndex < instance.firstItems()[i]) {
                return (i - 1);
            }
        }
        return familyIndex;
    }

}
