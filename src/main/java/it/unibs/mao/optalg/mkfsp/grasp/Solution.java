package it.unibs.mao.optalg.mkfsp.grasp;

import java.io.Serializable;
import java.util.Arrays;

public class Solution implements Serializable {
    private int[] solution;
    private double objectiveValue;
    private double elapsedTimeInSecond;
    private int numberIterationsGrasp;

    private int numberIterationsLocalSearch;

    public Solution(int[] solution, double objectiveValue, double elapsedTimeInSecond, int numberIterationsGrasp,
                    int numberIterationsLocalSearch) {
        this.solution = solution;
        this.objectiveValue = objectiveValue;
        this.elapsedTimeInSecond = elapsedTimeInSecond;
        this.numberIterationsGrasp = numberIterationsGrasp;
        this.numberIterationsLocalSearch = numberIterationsLocalSearch;
    }

    public int[] getSolution() {
        return solution;
    }

    public double getObjectiveValue() {
        return objectiveValue;
    }

    public double getElapsedTimeInSecond() { return  elapsedTimeInSecond; };

    public int getNumberIterationsGrasp() { return numberIterationsGrasp; };

    public int getNumberIterationsLocalSearch() { return numberIterationsLocalSearch; };
    public String toString() {
        return "Solution: " + Arrays.toString(this.solution) + "\nObjective Value: " + this.objectiveValue + "\nElasped Time: " + this.elapsedTimeInSecond + "s" +
                "\n#Grasp iterations: " + this.numberIterationsGrasp + "\n#Local search iterations: " + this.numberIterationsLocalSearch;
    }

}
