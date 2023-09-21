package it.unibs.mao.optalg.mkfsp.grasp;

import gurobi.GRBException;
import it.unibs.mao.optalg.mkfsp.Instance;
import it.unibs.mao.optalg.mkfsp.localsearch.LocalSearch;
import it.unibs.mao.optalg.mkfsp.localsearch.TabuSearch;
import java.util.*;

public class GRASP {

    private static final int MAX_LOCAL_SEARCH_ITERATIONS = 30;
    private static final int MAX_TABU_SEARCH_ITERATIONS = 20;

    // creare una lista di beta
    private static final double[] BETA_LIST = {0.3, 0.4, 0.5, 0.6, 0.7};
    private static final double BETA_RCL = 0.3;
    private static boolean localsearchIsUsed = true;
    private static boolean tabusearchIsUsed = false;
    private static KnapsacksResource knapRes = null;
    public static Solution grasp(Instance instance, int numIterations) throws GRBException {

        int nItems = instance.nItems();
        int[] bestSolution = new int[nItems];
        double bestObjectiveValue = Double.NEGATIVE_INFINITY;
        Random random = new Random();
        long startTime = System.nanoTime();

        int[] solutionConstructivePhase = new int[0];
        for (int iteration = 0; iteration < numIterations; iteration++) {
            solutionConstructivePhase = constructivePhase(instance, random);


            //int[] solutionLocalSearch = LocalSearch.run(instance, solutionConstructivePhase, random, MAX_LOCAL_SEARCH_ITERATIONS);
            //solution = TabuSearch.run(instance, solution, random, MAX_TABU_SEARCH_ITERATIONS);

            double objectiveValueConstructivePhase = Utils.calculateObjectiveValue(instance, solutionConstructivePhase);
            //double objectiveValueLocalSearch = Utils.calculateObjectiveValue(instance, solutionLocalSearch);

            /*
            System.out.println("---------- ITERAZIONE #" + (iteration+1) + " -------------");
            if(!Arrays.toString(solutionConstructivePhase).equalsIgnoreCase(Arrays.toString(solutionLocalSearch))) {
                System.out.println("Soluzione phase 1: " + Arrays.toString(solutionConstructivePhase));
                System.out.println("Soluzione LocalS.: " + Arrays.toString(solutionLocalSearch));
            }
            System.out.println("Soluzione phase 1: " + Arrays.toString(solutionConstructivePhase));
            */

            if (objectiveValueConstructivePhase > bestObjectiveValue) {
                bestObjectiveValue = objectiveValueConstructivePhase;
                bestSolution = solutionConstructivePhase.clone();
            }

        }

        long endTime = System.nanoTime();
        double elapsedTimeInSeconds = (endTime - startTime) / 1e9;

        System.out.println("---------- FINE ISTANZA -------------");

        return new Solution(bestSolution, bestObjectiveValue, elapsedTimeInSeconds, numIterations, MAX_LOCAL_SEARCH_ITERATIONS);
    }

    private static int[] constructivePhase(Instance instance, Random random) {
        int nItems = instance.nItems();
        int nKnapsacks = instance.nKnapsacks();
        int[] solution = new int[nItems];
        int initialFamilies = instance.nFamilies();

        //creo l'oggetto risorse del knapsack
        knapRes = new KnapsacksResource(nKnapsacks);
        for(int i = 0; i < nKnapsacks; i++){
            knapRes.setResources(i, instance.knapsacks()[i]);
        }
        //famiglie disponibili
        Set<Integer> availableFamily = new HashSet<>();
        for (int j = 0; j < initialFamilies; j++) {
            availableFamily.add(j);
        }
        //seleziono una BETA random dalla lista
        /*
        int randomIndex = random.nextInt(BETA_LIST.length);
        double BETA_RCL = BETA_LIST[randomIndex];
        */
        Arrays.fill(solution, -1);

        while (!availableFamily.isEmpty()) {
            //seleziono una famiglia random dalla lista RCL
            List<Integer> rcl = buildRCL(instance, availableFamily, BETA_RCL);
            int randomFamily = getRandomFamilyFromRCL(rcl, random);
            int firstItem = instance.firstItems()[randomFamily];
            int endItem = (randomFamily == instance.nFamilies() - 1) ? nItems: instance.firstItems()[randomFamily+1];
            boolean completelyFit = false;

            Set<Integer> availableKnapsacks = new HashSet<>();
            for (int k = 0; k < nKnapsacks; k++) {
                availableKnapsacks.add(k);
            }

            while(!availableKnapsacks.isEmpty()) {
                //da cambiare, non va preso random
                int randomKnapsack = getRandomKnapsack(availableKnapsacks, random);
                int[] knapsackCapacity = knapRes.getResources().get(randomKnapsack);

                if (familyCompletelyFitKnapsack(firstItem, endItem, randomKnapsack, instance)) {
                    //se la famiglia ci sta completamente allora inserisco tutti i suoi item nel knapsack
                    for(int i=firstItem; i < endItem; i++) {
                        solution[i] = randomKnapsack;
                        knapRes.removeResources(instance.items()[i], randomKnapsack); //possibile ottimizzazione
                    }
                    //tolgo la famiglia da quelle disponibili
                    availableFamily.remove(randomFamily);

                    completelyFit = true;
                } else {
                    availableKnapsacks.remove(randomKnapsack);
                }
            }
            //calcolo Njr
            //Njr ci sta in un knaps? se si ok se no:

            if(!completelyFit){
                //calcolo Njr
                int[] necessaryResources = new int[instance.nResources()];
                for(int r = 0; r < instance.nResources(); r++){
                    for(int i=firstItem; i < endItem; i++) {
                        necessaryResources[r] += instance.items()[i][r];
                    }
                }
                //trova il max tra ri/Nji
                int maxItemIndex = -1;
                int maxResourceIndex = -1;
                double maxValue = 0;
                for(int r = 0; r < instance.nResources(); r++){
                    for(int i=firstItem; i < endItem; i++) {
                        double currentValue = instance.items()[i][r]/necessaryResources[r];
                        if(currentValue > maxValue){
                            maxValue = currentValue;
                            maxItemIndex = i;
                            maxResourceIndex = r;
                        }
                    }
                }
                //metto il max nel knapsack nel quale rimangono meno risorse per la specifica risorsa scelta in teoria
                //scelta knapsack
                int minKnapsack = -1;
                int minGap =  1000;
                int mostDangerousResource = instance.items()[maxItemIndex][maxResourceIndex];

                for(int k = 0; k < nKnapsacks; k++){
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

                if (minKnapsack == -1) {
                    availableFamily.remove(randomFamily);
                } else {
                    solution[maxItemIndex] = minKnapsack;
                    //ora aggiorno Njr
                    //ora provo a inserire tutta la famiglia senza quell'oggetto
                }
                //ora devo controllare se ci stanno anche le altre risorse
            }
        }

        return solution;
    }

    private static boolean familyCompletelyFitKnapsack(int firstItem, int lastItem, int k, Instance instance){
        int nResources = instance.nResources();
        int[] knapsackCapacity = instance.knapsacks()[k];
        int[][] items = instance.items();
        int[] usedResources = new int[nResources];

        for(int item=firstItem; item < lastItem; item++) {
            for (int r = 0; r < nResources; r++) {
                usedResources[r] += items[item][r];
            }
        }
        for (int r = 0; r < nResources; r++) {
            if (usedResources[r] > knapsackCapacity[r]) {
                return false;
            }
        }

        return true;
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
