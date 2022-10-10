package it.unibs.mao.optalg.mkfsp;

/**
 * An immutable object that holds the result of the feasibility analysis on
 * a candidate solution for a MKFSP instance.
 *
 * @param isValid        wheter the analysed solution is feasible or not
 * @param errorMessages  an array containing one entry for each detected error
 * @see   Instance.checkFeasibility
 */
public record FeasibilityCheck(boolean isValid, String[] errorMessages) {}
