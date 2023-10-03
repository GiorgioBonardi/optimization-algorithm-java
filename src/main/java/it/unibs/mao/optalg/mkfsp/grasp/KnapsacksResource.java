package it.unibs.mao.optalg.mkfsp.grasp;

import java.util.ArrayList;
import java.util.List;

public class KnapsacksResource {
    private int nKnapsacks;
    private List<int[]> resources;
    public KnapsacksResource(int numberOfKnapsacks) {
        this.nKnapsacks = numberOfKnapsacks;
        this.resources = new ArrayList<>();
        for (int i = 0; i < numberOfKnapsacks; i++) {
            // Inizializziamo ciascun knapsack con un array vuoto di risorse
            this.resources.add(new int[0]);
        }
    }

    public int getnKnapsacks() {
        return nKnapsacks;
    }

    public List<int[]> getResources() {
        return resources;
    }

    public void setResources(int knapsackIndex, int[] newResources) {
        if (knapsackIndex >= 0 && knapsackIndex < nKnapsacks) {
            resources.set(knapsackIndex, newResources);
        } else {
            throw new IllegalArgumentException("Indice del knapsack non valido");
        }
    }

    public void removeResources(int[] itemResources, int knapsack){
        for(int r = 0; r < itemResources.length; r++){
            this.resources.get(knapsack)[r] -= itemResources[r];
        }
    }
    public void addResources(int[] itemResources, int knapsack) {
        for(int r = 0; r < itemResources.length; r++){
            this.resources.get(knapsack)[r] += itemResources[r];
        }
    }
}

