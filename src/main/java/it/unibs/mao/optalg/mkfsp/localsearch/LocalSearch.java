package it.unibs.mao.optalg.mkfsp.localsearch;

import it.unibs.mao.optalg.mkfsp.Instance;
import it.unibs.mao.optalg.mkfsp.grasp.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class LocalSearch {
    public static int[] run(Instance instance, int[] initialSolution, Random random, int totalIterations) {
        int nItems = instance.nItems();
        int[] currentSolution = initialSolution.clone();
        double currentObjectiveValue = Utils.calculateObjectiveValue(instance, currentSolution);

        Set<Integer> availableFamily = new HashSet<>();
        for (int j = 0; j < instance.nFamilies(); j++) {
            boolean addFamily = false;
            int range = (j == instance.nFamilies() - 1) ? nItems - instance.firstItems()[j] : instance.firstItems()[j+1] - instance.firstItems()[j];
            for (int i = instance.firstItems()[j]; i < instance.firstItems()[j] + range; i++) {
                if ( currentSolution[i] == -1) addFamily = true;
            }
            if (addFamily) availableFamily.add(j);
        }

        List<Integer> rankedFamilies = Utils.rankFamiliesByProfits(instance, availableFamily);

        int bestFamily = (int) rankedFamilies.get(0);
        int firstItemBestFamily = instance.firstItems()[bestFamily];
        int lastItemBestFamily = (bestFamily == instance.nFamilies()) ? instance.firstItems()[bestFamily+1] : instance.nItems();
        int rangeItemsBestFamily = lastItemBestFamily - firstItemBestFamily;

        for (int iteration = 0; iteration < totalIterations; iteration++) {
            int i;
            do {
                int i_randomRange = random.nextInt(rangeItemsBestFamily);
                i = i_randomRange + firstItemBestFamily;
            } while (currentSolution[i] != -1);

            int k = random.nextInt(instance.nKnapsacks());

            if (Utils.isAssignmentValid(instance, i, k, currentSolution)) {
                int[] newSolution = currentSolution.clone();
                newSolution[i] = k;
                double newObjectiveValue = Utils.calculateObjectiveValue(instance, newSolution);

                if (newObjectiveValue > currentObjectiveValue) {
                    currentSolution = newSolution;
                    currentObjectiveValue = newObjectiveValue;
                }
            }
        }

        return currentSolution;
    }
}
