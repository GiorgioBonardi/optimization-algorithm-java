package it.unibs.mao.optalg.mkfsp;

import java.util.Arrays;

public class Solution {

    private int[] solution;
    private double objectiveValue;

    public Solution(int[] solution, double objectiveValue) {
        this.solution = solution;
        this.objectiveValue = objectiveValue;
    }

    public void setSolution(int[] solution) {
        this.solution = solution;
    }

    public void setObjectiveValue(double objectiveValue) {
        this.objectiveValue = objectiveValue;
    }
    public int[] getSolution() {
        return solution;
    }

    public double getObjectiveValue() {
        return objectiveValue;
    }

    public String toString() {
        return "Solution: " + Arrays.toString(this.solution) + "\nObjective Value: " + this.objectiveValue;
    }


}
