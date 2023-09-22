package it.unibs.mao.optalg.mkfsp.grasp;

import it.unibs.mao.optalg.mkfsp.Instance;

import java.util.*;

public class Utils {

    public static double calculateObjectiveValue(Instance instance, int[] solution) {
        double objectiveValue = 0.0;

        final int nItems = instance.nItems();
        final int nFamilies = instance.nFamilies();
        final int nKnapsacks = instance.nKnapsacks();
        final int nResources = instance.nResources();
        final int[] firstItems = instance.firstItems();

        final Map<Integer, Set<Integer>> splits = new HashMap<>();
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

    public static boolean isAssignmentValid(Instance instance, int i, int k, int[] solution) {
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

    public static List<Integer> rankFamiliesByProfits(Instance instance, Set<Integer> availableFamily) {
        List<Integer> familyIndices = new ArrayList<>();
        for (int family : availableFamily) {
            familyIndices.add(family);
        }

        familyIndices.sort(Comparator.comparingDouble((Integer familyIndex) -> instance.profits()[familyIndex]).reversed());

        return familyIndices;
    }

    public static List<Integer> rankFamiliesByPenalties(Instance instance, Set<Integer> availableFamily) {
        List<Integer> familyIndices = new ArrayList<>();
        for (int family : availableFamily) {
            familyIndices.add(family);
        }

        familyIndices.sort(Comparator.comparingDouble((Integer familyIndex) -> instance.penalties()[familyIndex]).reversed());
        return familyIndices;
    }

    public static List<Integer> rankFamiliesBySpecialGain(Instance instance, Set<Integer> availableFamily) {
        List<Integer> familyIndices = new ArrayList<>();
        for (int family : availableFamily) {
            familyIndices.add(family);
        }

        familyIndices.sort(Comparator.comparingDouble((Integer familyIndex) -> {
            int endItem = (familyIndex == instance.nFamilies() - 1) ? instance.nItems(): instance.firstItems()[familyIndex+1];
            int nItems = endItem - instance.firstItems()[familyIndex];
            double gain = (instance.profits()[familyIndex] - (nItems - 1) * instance.penalties()[familyIndex]);
            return gain;
        }).reversed());
        return familyIndices;
    }

    public static List<Integer> rankFamiliesByRatioProfitOverPenality(Instance instance, Set<Integer> availableFamily) {
        List<Integer> familyIndices = new ArrayList<>();
        for (int family : availableFamily) {
            familyIndices.add(family);
        }

        familyIndices.sort(Comparator.comparingDouble((Integer familyIndex) ->
                instance.profits()[familyIndex]/instance.penalties()[familyIndex]).reversed());
        return familyIndices;
    }
}
