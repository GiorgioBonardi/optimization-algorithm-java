package it.unibs.mao.optalg.mkfsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBModel;
import gurobi.GRBVar;

public class Main {
  private static final double INT_TOLERANCE = 1e-6;
  private static final double TIME_LIMIT = 60;
  private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE_TIME
      .withZone(ZoneOffset.UTC);

  public static final Path INSTANCES_DIR = Path.of("instances");
  public static final Path OUTPUT_DIR = Path.of("output");

  /**
   * A simple example of how to use the functions provided in this codebase.
   *
   * Scan the `INSTANCES_DIR` directory reading each MKFSP instance data file
   * and solve the associated Gurobi ILP model with a time limit of 60 seconds.
   *
   * @param args  Not used
   * @throws GRBException
   * @throws IOException
   */
  public static void main(final String[] args) throws GRBException, IOException {
    final Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
    // Windows does not allow the character ':' in file names
    final String executionId = DTF.format(now).replace(':', '_');

    // Collect instance file paths
    final List<Path> paths = new ArrayList<>();
    try(final Stream<Path> stream = Files.walk(INSTANCES_DIR)) {
      final Iterator<Path> it = stream.iterator();
      while (it.hasNext()) {
        final Path path = it.next();
        if (path.toString().endsWith(".json")) {
          paths.add(path);
        }
      }
    }
    paths.sort(null);

    // Solve each instance
    System.out.print(
        "Solving " + paths.size() + " instances with a time limit of " + TIME_LIMIT +
        " seconds (execution id: '" + executionId + "').\nPress ENTER to continue"
    );
    System.in.read();

    final Path outputDir = OUTPUT_DIR.resolve(executionId);
    Files.createDirectories(outputDir);
    GRBEnv env = null;
    try {
      env = new GRBEnv();
      for (final Path path: paths) {
        // TODO: handle all runtime exceptions

        System.out.println("------------------------------------------------------------");
        System.out.println("Solving instance '" + path.getFileName() + "'");
        System.out.println("------------------------------------------------------------");
        final Instance instance = Instance.load(path);
        GRBModel model = null;
        try {
          final ModelVars modelVars = Model.build(instance, env);
          model = modelVars.model();
          model.set(GRB.StringParam.LogFile, outputDir.resolve(instance.id() + ".log").toString());
          model.set(GRB.DoubleParam.TimeLimit, 60);
          model.optimize();
          model.write(outputDir.resolve(instance.id() + ".json").toString());

          if (model.get(GRB.IntAttr.SolCount) > 0) {
            final int nItems = instance.nItems();
            final int nKnapsacks = instance.nKnapsacks();
            final GRBVar[][] yvars = modelVars.yvars();
            final double binLb = 1 - INT_TOLERANCE;
            final double binUb = 1 + INT_TOLERANCE;
            final int[] solution = new int[nItems];
            Arrays.fill(solution, -1);
            for (int i = 0; i < nItems; ++i) {
              for (int k = 0; k < nKnapsacks; ++k) {
                final double value = yvars[i][k].get(GRB.DoubleAttr.X);
                if (binLb <= value && value <= binUb) {
                  solution[i] = k;
                  break;
                }
              }
            }
            final double objValue = model.get(GRB.DoubleAttr.ObjVal);
            final FeasibilityCheck check = instance.checkFeasibility(solution, objValue);

            System.out.println("\nSolution: " + objValue + " (valid: " + check.isValid() + ")");
            if (!check.isValid()) {
              for (final String errMsg: check.errorMessages()) {
                System.out.println("  - " + errMsg);
              }
            }
          } else {
            // TODO: handle all status codes
            final int statusCode =  model.get(GRB.IntAttr.Status);
            System.out.println("\nGurobi terminated with status code: " + statusCode);
          }

        } finally {
          if (model != null) {
            model.dispose();
          }
        }
      }

    } finally {
      if (env != null) {
        env.dispose();
      }
    }
  }
}
