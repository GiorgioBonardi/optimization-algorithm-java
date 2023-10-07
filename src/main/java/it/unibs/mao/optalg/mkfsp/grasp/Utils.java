package it.unibs.mao.optalg.mkfsp.grasp;

import it.unibs.mao.optalg.mkfsp.Instance;

import java.util.*;
import java.util.stream.Collectors;

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

    public static List<Integer> getBestFamiliesUsedBySplit(Instance instance, int[] initialSolution) {
        HashMap<Integer, Integer> familiesWithSplit = calculateSplitForEachFamily(instance, initialSolution);

        // ordino le famiglie in ordine crescente rispetto allo split
        List<Map.Entry<Integer, Integer>> entryList = new ArrayList<>(familiesWithSplit.entrySet());

        // Ordina la lista in base ai valori (split) in ordine crescente
        Collections.sort(entryList, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> entry1, Map.Entry<Integer, Integer> entry2) {
                return entry1.getValue().compareTo(entry2.getValue());
            }
        });

        // Estrai le chiavi ordinate
        List<Integer> orderedFamilies = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : entryList) {
            orderedFamilies.add(entry.getKey());
        }

        int halfIndex = (int) (orderedFamilies.size() * 0.5);

        // Estrai la prima metà della lista
        List<Integer> firstHalf = orderedFamilies.subList(0, halfIndex);

        return firstHalf;
    }

    public static List<Integer> getWorstFamiliesNotUsedBySpecialGain(Instance instance, int[] initialSolution) {
        Set<Integer> availableFamilies = new HashSet<>();
        for (int i=0; i<instance.nFamilies(); i++) {
            int firstItem = instance.firstItems()[i];

            if (initialSolution[firstItem] == -1) {
                availableFamilies.add(i);
            }
        }
        // sono in ordine decrescente
        List<Integer> familiesNotUsedBySpecialGain = rankFamiliesBySpecialGain(instance, availableFamilies);

        int halfIndex = (int) (familiesNotUsedBySpecialGain.size() * 0.75);
        // Più è basso più famiglie prendo

        // Estrai la prima metà della lista
        List<Integer> lastHalf = familiesNotUsedBySpecialGain.subList(halfIndex, familiesNotUsedBySpecialGain.size());

        return lastHalf;
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

    public static List<Integer> sortFamiliesByPenalties(Instance instance, Set<Integer> availableFamily) {
        return availableFamily.stream()
                .sorted(Comparator.comparingDouble((Integer familyIndex) -> instance.penalties()[familyIndex]).reversed())
                .collect(Collectors.toCollection(LinkedList::new));
    }
    public static List<Integer> rankFamiliesBySpecialGain(Instance instance, Set<Integer> availableFamily) {
        List<Integer> familyIndices = new ArrayList<>();
        for (int family : availableFamily) {
            familyIndices.add(family);
        }

        familyIndices.sort(Comparator.comparingDouble((Integer familyIndex) -> {
            int endItem = (familyIndex == instance.nFamilies() - 1) ? instance.nItems(): instance.firstItems()[familyIndex+1];
            int nItems = endItem - instance.firstItems()[familyIndex];
            double gain = (instance.profits()[familyIndex] - (double) ((nItems - 1) * instance.penalties()[familyIndex]) / 2);
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
