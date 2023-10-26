package it.unibs.mao.optalg.mkfsp;

import gurobi.*;
import it.unibs.mao.optalg.mkfsp.grasp.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A utility class with one static method that builds the Gurobi model for
 * a MKFSP instance. This class can not be instantiated.
 */
public class Model {

  private Model() {
    // no-op
  }

  /**
   * Constructs a Gurobi model for the given MKFSP instance.
   *
   * @param instance  an instance of the MKFSP problem
   * @param env       the Gurobi environment used to build the GRBModel instance
   * @return
   * @throws GRBException
   * @see ModelVars
   */
  public static ModelVars build(final Instance instance, final GRBEnv env) throws GRBException {
    final GRBModel model = new GRBModel(env);
    model.set(GRB.IntAttr.ModelSense, GRB.MAXIMIZE);

    final int nItems = instance.nItems();
    final int nFamilies = instance.nFamilies();
    final int nKnapsacks = instance.nKnapsacks();
    final int[] profits = instance.profits();
    final int[] penalties = instance.penalties();
    final int[] firstItems = instance.firstItems();
    final int[][] items = instance.items();
    final int[][] knapsacks = instance.knapsacks();
    final int nResources = instance.nResources();

    // Add model variables
    final GRBVar[] xvars = new GRBVar[nFamilies];
    final GRBVar[][] yvars = new GRBVar[nItems][nKnapsacks];
    final GRBVar[][] zvars = new GRBVar[nFamilies][nKnapsacks];
    final GRBVar[] svars = new GRBVar[nFamilies];

    for (int j = 0; j < nFamilies; ++j) {
      xvars[j] = model.addVar(0, 1, profits[j], GRB.BINARY, "x["+j+"]");
      svars[j] = model.addVar(0, GRB.INFINITY, -penalties[j], GRB.INTEGER, "s["+j+"]");
      for (int k = 0; k < nKnapsacks; ++k) {
        zvars[j][k] = model.addVar(0, 1, 0, GRB.BINARY, "z["+j+","+k+"]");
        final int endItem = (j+1 < firstItems.length) ? firstItems[j+1] : nItems;
        for (int i = firstItems[j]; i < endItem; ++i) {
          yvars[i][k] = model.addVar(0, 1, 0, GRB.INTEGER, "y["+i+","+k+"]");
        }
      }
    }

    for (int j = 0, m = firstItems.length; j < m; ++j) {
      final int firstItem = firstItems[j];
      final int endItem = (j+1 < firstItems.length) ? firstItems[j+1] : nItems;

      // Add family integrity constraints
      for (int i = firstItem; i < endItem; ++i) {
        final GRBLinExpr lhs = new GRBLinExpr();
        for (int k = 0; k < nKnapsacks; ++k) {
          lhs.addTerm(1, yvars[i][k]);
        }
        model.addConstr(lhs, GRB.EQUAL, xvars[j], "_f");
      }

      final GRBLinExpr zsLhs = new GRBLinExpr();
      zsLhs.addConstant(-1);

      // Add logical constraints between yvars and zvars
      for (int k = 0; k < nKnapsacks; ++k) {
        int nMaxItems = endItem - firstItem; // Initialize nMaxItems to Family cardinality

        for (int r = 0; r < nResources; ++r) {
          int totalConsumed = 0; // Initialize total resource consumption

          //Add ascending sorting of the items !!!!
          Set<Integer> availableItems = new HashSet<>();
          for (int i = firstItem; i < endItem; ++i) {
            availableItems.add(i);
          }

          List<Integer> itemList = Utils.rankItemsByResource(instance, availableItems, r);

          for (int i = 0; i < itemList.size(); ++i) {
            totalConsumed = totalConsumed + items[i][r];
            if(totalConsumed > knapsacks[k][r]) {
              nMaxItems = Math.min(nMaxItems, i);
            }
          }
        }
        zsLhs.addTerm(1, zvars[j][k]);

        final GRBLinExpr yzLhs = new GRBLinExpr();
        for (int i = firstItem; i < endItem; ++i) {
          yzLhs.addTerm(1, yvars[i][k]);
        }
        final GRBLinExpr _rhs = new GRBLinExpr();
        //_rhs.addTerm(endItem - firstItem, zvars[j][k]);
        _rhs.addTerm(nMaxItems, zvars[j][k]);
        model.addConstr(yzLhs, GRB.LESS_EQUAL, _rhs, "_z");
      }


      // Add logical constraints between zvars and svars
      model.addConstr(zsLhs, GRB.LESS_EQUAL, svars[j], "_s");

      // Add new constraints between svars and cardinality of Fj
      model.addConstr(svars[j], GRB.LESS_EQUAL, (endItem - firstItem) - 1, "_new");
    }

    // Add maximum capacity constraints
    for (int k = 0; k < nKnapsacks; ++k) {
      final int[] knapsackCapacities = knapsacks[k];
      for (int r = 0, q = knapsackCapacities.length; r < q; ++r) {
        final GRBLinExpr lhs = new GRBLinExpr();
        for (int i = 0; i < nItems; ++i) {
          lhs.addTerm(items[i][r], yvars[i][k]);
        }
        model.addConstr(lhs, GRB.LESS_EQUAL, knapsackCapacities[r], "_r");
      }
    }

    final CallbackExecutionInfo executionInfo = new CallbackExecutionInfo();
    model.setCallback(new MkfspCallback(executionInfo));
    model.update();

    return new ModelVars(model, xvars, yvars, zvars, svars, executionInfo);
  }

  public static class CallbackExecutionInfo {
    public double startTime;
    public double objValue;
    public List<double[]> history = new ArrayList<>();
  }

  private static class MkfspCallback extends GRBCallback {
    private CallbackExecutionInfo executionInfo;

    public MkfspCallback(final CallbackExecutionInfo executionInfo){
      this.executionInfo = executionInfo;
    }

    @Override
    protected void callback() {
      if (Double.isNaN(executionInfo.startTime)) {
        executionInfo.startTime = System.currentTimeMillis();
      }

      try {
        if (where == GRB.CB_MIPSOL) {
          final double newMipsolObj = getDoubleInfo(GRB.CB_MIPSOL_OBJ);
          final double elapsed = getDoubleInfo(GRB.CB_RUNTIME);
          executionInfo.history.add(new double[] {elapsed, newMipsolObj});
          System.out.println("TTB: " + (executionInfo.history.get(executionInfo.history.size() - 1)[0]) + "s  Nuova MIP incumbent: " + executionInfo.history.get(executionInfo.history.size() - 1)[1]);


        }
      } catch (final Exception e) {
        // TODO
      }
    }
  }
}