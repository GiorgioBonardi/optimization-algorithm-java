package it.unibs.mao.optalg.mkfsp.grasp;

import java.io.Serializable;
import java.util.Arrays;

public class Solution implements Serializable {
    private int[] solution;
    private double objectiveValue;
    private double elapsedTimeInSecond;
    private double rcl;
    private int numberIterationsGrasp;

    private int numberIterationsLocalSearch;

    public Solution(int[] solution, double objectiveValue, double elapsedTimeInSecond, double rcl, int numberIterationsGrasp,
                    int numberIterationsLocalSearch) {
        this.solution = solution;
        this.objectiveValue = objectiveValue;
        this.elapsedTimeInSecond = elapsedTimeInSecond;
        this.rcl = rcl;
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

    public double getRcl() { return rcl; };

    public int getNumberIterationsGrasp() { return numberIterationsGrasp; };

    public int getNumberIterationsLocalSearch() { return numberIterationsLocalSearch; };
    public String toString() {
        return "Solution: " + Arrays.toString(this.solution) + "\nObjective Value: " + this.objectiveValue + "\nElasped Time: " + this.elapsedTimeInSecond + "s" +
                "\n#Grasp iterations: " + this.numberIterationsGrasp + "\nRCL: " + this.rcl + "\n#Local search iterations: " + this.numberIterationsLocalSearch;
    }

}
