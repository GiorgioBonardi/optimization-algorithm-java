package it.unibs.mao.optalg.mkfsp.grasp;

import java.io.Serializable;
import java.util.Arrays;

public class Solution implements Serializable {
    private final int[] solution;
    private final double objectiveValue;
    private final double elapsedTimeInSecond;

    public Solution(int[] solution, double objectiveValue, double elapsedTimeInSecond) {
        this.solution = solution;
        this.objectiveValue = objectiveValue;
        this.elapsedTimeInSecond = elapsedTimeInSecond;
    }

    public int[] getSolution() {
        return solution;
    }

    public double getObjectiveValue() {
        return objectiveValue;
    }

    public double getElapsedTimeInSecond() { return  elapsedTimeInSecond; };

    public String toString() {
        return "Solution: " + Arrays.toString(this.solution) + "\nObjective Value: " + this.objectiveValue + "\nElasped Time: " + this.elapsedTimeInSecond + "s";
    }

}
