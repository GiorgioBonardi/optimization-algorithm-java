package it.unibs.mao.optalg.mkfsp.grasp;

import gurobi.GRBException;
import it.unibs.mao.optalg.mkfsp.Instance;
import it.unibs.mao.optalg.mkfsp.localsearch.GurobiSearch;

import java.util.*;

public class GRASP {

    private static final int MAX_LOCAL_SEARCH_ITERATIONS = 30;
    private static final int CONSTRUCTIVE_ITERATION = 1000;
    private static final int MAX_TABU_SEARCH_ITERATIONS = 20;

    private static final double TIME_LIMIT_GRASP = 200000; //milliseconds
    private static final double DEFAULT_TIME_LIMIT_GUROBI = 400; //seconds
    // creare una lista di beta
    private static final double[] BETA_LIST = {0.1, 0.2, 0.3};
    private static final double BETA_RCL = 0.3;
    private static boolean localsearchIsUsed = true;
    private static boolean tabusearchIsUsed = false;
    private static KnapsacksResource knapRes = null;
    private static final int MAX_STALE_ITERATIONS = 2000;
    private static final long STALE_TIME = 30000; //milliseconds
    public static Solution grasp(Instance instance, int numIterations) throws GRBException {

        int nItems = instance.nItems();
        int[] bestSolution = new int[nItems];
        double bestObjectiveValue = Double.NEGATIVE_INFINITY;
        Random random = new Random();
        long startTime = System.nanoTime();

        int[] solutionConstructivePhase = new int[0];

        long timer = System.currentTimeMillis();
        while(System.currentTimeMillis() - timer < TIME_LIMIT_GRASP){
        //for (int iteration = 0; iteration < numIterations; iteration++) {
            solutionConstructivePhase = constructivePhase(instance, random);
            double objectiveValueConstructivePhase = Utils.calculateObjectiveValue(instance, solutionConstructivePhase);
            System.out.println("NUOVA ITERAZIONE");
            System.out.println("OBJ DATO A GUROBI: " + objectiveValueConstructivePhase);
            int[] solutionGurobiSearch = GurobiSearch.run(instance, solutionConstructivePhase, 0, null);


            //int[] solutionLocalSearch = LocalSearch.run(instance, solutionConstructivePhase, random, MAX_LOCAL_SEARCH_ITERATIONS);
            //solution = TabuSearch.run(instance, solution, random, MAX_TABU_SEARCH_ITERATIONS);

            double objectiveGurobiSearch = solutionGurobiSearch != null ? Utils.calculateObjectiveValue(instance, solutionGurobiSearch) : Double.NEGATIVE_INFINITY;

            double solValueTaken = 0;
            int[] solTaken;
            if(objectiveGurobiSearch > objectiveValueConstructivePhase) {
                solValueTaken = objectiveGurobiSearch;
                solTaken = solutionGurobiSearch;
                System.out.println("SOL GUROBI");
            } else {
                solValueTaken = objectiveValueConstructivePhase;
                solTaken = solutionConstructivePhase;
                System.out.println("SOL GRASP");
            }

            if (solValueTaken > bestObjectiveValue) {
                bestObjectiveValue = solValueTaken;
                bestSolution = solTaken.clone();
            }

        }

        long endTime = System.nanoTime();
        double elapsedTimeInSeconds = (endTime - startTime) / 1e9;

        return new Solution(bestSolution, bestObjectiveValue, elapsedTimeInSeconds, numIterations, MAX_LOCAL_SEARCH_ITERATIONS);
    }

    public static Solution grasp2(Instance instance, int numIterations) throws GRBException {
        /*
        Eseguo la Constructive Phase per TIME_LIMIT_GRASP (oppure esce per stale iteration)
        Successivamente chiamo Gurobi per Y secondi + tempo rimanente
        (Parametri da settare in base all'istanza)
         */
        
        int nItems = instance.nItems();
        int[] bestSolution = new int[nItems];
        double bestObjectiveValue = Double.NEGATIVE_INFINITY;
        Random random = new Random();
        long startTime = System.nanoTime();
        //int nStale = 0;

        int[] solutionConstructivePhase = new int[0];

        long timer = System.currentTimeMillis();
        long timerStale = timer;
        int contIteration = 1;
        double timeToBest = 0;
        int[] bestSolConstructivePhase = new int[0];
        double bestObjectiveConstructivePhase = Double.NEGATIVE_INFINITY;
        double elapsedTimeMillis = System.currentTimeMillis() - timer;

        while(elapsedTimeMillis < TIME_LIMIT_GRASP && (System.currentTimeMillis() - timerStale) < STALE_TIME) {
            //System.out.println("ITERAZIONE INTERNA N_" + contIteration);
            solutionConstructivePhase = constructivePhase(instance, random);
            double objectiveValueConstructivePhase = Utils.calculateObjectiveValue(instance, solutionConstructivePhase);

            if(objectiveValueConstructivePhase > bestObjectiveConstructivePhase) {
                bestObjectiveConstructivePhase = objectiveValueConstructivePhase;
                bestSolConstructivePhase = solutionConstructivePhase;
                timerStale = System.currentTimeMillis();
            }

            contIteration++;
            elapsedTimeMillis = System.currentTimeMillis() - timer;
        }
        //System.out.println("SOL DATA A GUROBI: " + bestObjectiveConstructivePhase);
        double additionalSeconds = (TIME_LIMIT_GRASP - elapsedTimeMillis) / 1000;

        if(System.currentTimeMillis() - timerStale >= STALE_TIME) {
            System.out.println("GRASP ended because of STALE Iterations! Additional time to Gurobi: " + additionalSeconds);
        } else {
            System.out.println("GRASP ended because of time limits");
        }

        System.out.println("Obj Value GRASP: " + bestObjectiveConstructivePhase);

        double totalTimeLimitGurobi = DEFAULT_TIME_LIMIT_GUROBI + additionalSeconds;

        HashMap<Integer, Integer> splitForFamilies = calculateSplitForEachFamily(instance, bestSolConstructivePhase);

        int[] solutionGurobiSearch = GurobiSearch.run(instance, bestSolConstructivePhase, totalTimeLimitGurobi, splitForFamilies);
        double objectiveGurobiSearch = solutionGurobiSearch != null ? Utils.calculateObjectiveValue(instance, solutionGurobiSearch) : Double.NEGATIVE_INFINITY;

        System.out.println("SOL TROVATA DA GUROBI: " + objectiveGurobiSearch);

            /*if (objectiveGurobiSearch > bestObjectiveValue) {
                bestObjectiveValue = objectiveGurobiSearch;
                bestSolution = solutionGurobiSearch.clone();
                timeToBest = System.currentTimeMillis() - timer;
            }

             */
        System.out.println("TIME TO BEST: " + timeToBest);
        long endTime = System.nanoTime();
        double elapsedTimeInSeconds = (endTime - startTime) / 1e9;

        return new Solution(solutionGurobiSearch, objectiveGurobiSearch, elapsedTimeInSeconds, numIterations, MAX_LOCAL_SEARCH_ITERATIONS);
    }
    private static int[] constructivePhase(Instance instance, Random random) {
        int nItems = instance.nItems();
        int nKnapsacks = instance.nKnapsacks();
        int[] solution = new int[nItems];
        int initialFamilies = instance.nFamilies();

        //creo l'oggetto risorse del knapsack
        /*
        NOTA: questo non va perchè copia il riferimento a instance.knapsacks()[i]
        knapRes = new KnapsacksResource(nKnapsacks);
        for(int i = 0; i < nKnapsacks; i++){
            knapRes.setResources(i, instance.knapsacks()[i]);
        }
         */

        knapRes = new KnapsacksResource(nKnapsacks);
        for (int i = 0; i < nKnapsacks; i++) {
            int[] knapsackValue = new int[instance.nResources()];
            System.arraycopy(instance.knapsacks()[i], 0, knapsackValue, 0, instance.knapsacks()[i].length);
            knapRes.setResources(i, knapsackValue);
        }

        //famiglie disponibili
        Set<Integer> availableFamily = new HashSet<>();
        for (int j = 0; j < initialFamilies; j++) {
            availableFamily.add(j);
        }

        //seleziono una BETA random dalla lista
        int randomIndex = random.nextInt(BETA_LIST.length);
        double BETA_RCL = BETA_LIST[randomIndex];

        Arrays.fill(solution, -1);

        while (!availableFamily.isEmpty()) {
            //seleziono una famiglia random dalla lista RCL
            List<Integer> rcl = buildRCL(instance, availableFamily, BETA_RCL);
            int randomFamily = getRandomFamilyFromRCL(rcl, random);

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

            solution = recursiveFitFamily(instance, random, nKnapsacks, randomFamily, itemsToInsert, necessaryResources, solution, availableFamily);

            if(solution == null) {
                solution = prevSolution;
            }
        }

        return solution;
    }

    private static int[] recursiveFitFamily(Instance instance, Random random, int nKnapsacks, int randomFamily, Set<Integer> itemsToInsert, int[] necessaryResources, int[] solution, Set<Integer> availableFamily) {
        boolean completelyFit;
        completelyFit = hasWholeFamilyBeenFitted(nKnapsacks, randomFamily, itemsToInsert, necessaryResources, solution, random, availableFamily, instance);
        //Njr ci sta in un knaps? se si ok se no:

        if(!completelyFit && itemsToInsert.size() == 1) return null;

        if(!completelyFit){
            //trova il max tra ri/Nji
            int maxItemIndex = -1;
            int maxResourceIndex = -1;
            double maxValue = 0;
            for(int r = 0; r < instance.nResources(); r++) {
                for (int i : itemsToInsert) {
                    //ALTERNATIVA 1:
                    double currentValue = (double) instance.items()[i][r] / necessaryResources[r];
                    if(currentValue > maxValue) {
                        maxValue = currentValue;
                        maxItemIndex = i;
                        maxResourceIndex = r;
                    }
                }


                //ALTERNATIVA 2:
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

            //metto il max nel knapsack nel quale rimangono meno risorse per la specifica risorsa scelta in teoria
            //scelta knapsack
            int minKnapsack = -1;
            int minGap =  10000; //TODO: mettere double minGap = double.POSITIVE INIFINITY
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
                //devo uscire con la soluzione di prima immagino
                return null;
            } else {
                solution[maxItemIndex] = minKnapsack;
                //aggiorno la lista di item da inserire
                itemsToInsert.remove(maxItemIndex);
                //ora aggiorno Njr
                for(int r = 0; r < instance.nResources(); r++){
                    necessaryResources[r] -= instance.items()[maxItemIndex][r];
                }
                //aggiorno anche knapRes
                knapRes.removeResources(instance.items()[maxItemIndex], minKnapsack);

                //ora provo a inserire tutta la famiglia senza quell'oggetto
                //Chiamata ricorsiva(?)
                return recursiveFitFamily(instance, random, nKnapsacks, randomFamily, itemsToInsert, necessaryResources, solution, availableFamily);
            }

            //???ora devo controllare se ci stanno anche le altre risorse
        } else {
            return solution;
        }
    }

    private static boolean hasWholeFamilyBeenFitted(int nKnapsacks, int randomFamily, Set<Integer> itemsToInsert , int[] necessaryResources, int[] solution, Random random, Set<Integer> availableFamily, Instance instance) {
        Set<Integer> availableKnapsacks = new HashSet<>();
        for (int k = 0; k < nKnapsacks; k++) {
            availableKnapsacks.add(k);
        }

        while(!availableKnapsacks.isEmpty()) {
            //da cambiare, non va preso random
            int randomKnapsack = getRandomKnapsack(availableKnapsacks, random);
            int[] knapsackCapacity = knapRes.getResources().get(randomKnapsack);

            if (wholeFamilyFits(necessaryResources, knapsackCapacity, instance)) {
                //se la famiglia ci sta completamente allora inserisco tutti i suoi item nel knapsack
                /*
                for(int i=firstItem; i < endItem; i++) {
                    solution[i] = randomKnapsack;
                    knapRes.removeResources(instance.items()[i], randomKnapsack); //possibile ottimizzazione
                }
                 */
                for (int i : itemsToInsert) {
                    solution[i] = randomKnapsack;
                    //update residual capacity
                    knapRes.removeResources(instance.items()[i], randomKnapsack);
                }
                //tolgo la famiglia da quelle disponibili
                availableFamily.remove(randomFamily);
                return true;
            } else {
                availableKnapsacks.remove(randomKnapsack);
            }
        }
        return false;
    }

    private static boolean wholeFamilyFits(int[] necessaryResources, int[] knapsackCapacity, Instance instance){
        int nResources = instance.nResources();
        /*
        int[][] items = instance.items();
        int[] usedResources = new int[nResources];

        for(int item=firstItem; item < lastItem; item++) {
            for (int r = 0; r < nResources; r++) {
                usedResources[r] += items[item][r];
            }
        }

         */
        for (int r = 0; r < nResources; r++) {
            if (necessaryResources[r] > knapsackCapacity[r]) {
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

    public static HashMap<Integer, Integer> calculateSplitForEachFamily(Instance instance, int[] solution) {
        HashMap<Integer, Integer> splitForFamily = new HashMap<>();

        final int nItems = instance.nItems();
        final int nFamilies = instance.nFamilies();
        final int nKnapsacks = instance.nKnapsacks();
        final int nResources = instance.nResources();
        final int[] firstItems = instance.firstItems();

        final Map<Integer, Set<Integer>> splits = new HashMap<>();
        final int[][] usedResources = new int[nKnapsacks][nResources];
        for (int j = 0; j < nFamilies; ++j) {
            final int firstItem = firstItems[j];
            if (solution[firstItem] != -1) {
                final int endItem = j+1 < nFamilies ? firstItems[j+1] : nItems;

                for (int i = firstItem; i < endItem; ++i) {
                    final int k = solution[i];
                    if (-1 < k && k < nKnapsacks) {
                        for (int r = 0; r < nResources; ++r) {
                            usedResources[k][r] += instance.items()[i][r];
                        }
                        splits.computeIfAbsent(j, (key) -> new HashSet<>()).add(k);
                    }
                }

                splitForFamily.put(j, splits.get(j).size()-1);
            }
        }

        return splitForFamily;
    }
}
