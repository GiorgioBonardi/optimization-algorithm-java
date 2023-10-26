package it.unibs.mao.optalg.mkfsp;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import gurobi.GRBException;
import it.unibs.mao.optalg.mkfsp.grasp.GRASP;
import it.unibs.mao.optalg.mkfsp.grasp.Solution;

public class Main {
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

    /*
    final List<Path> pathsTemp = new ArrayList<>();
    pathsTemp.add(Path.of(INSTANCES_DIR + "/instance01.json"));
     */

    // Solve each instance
    System.out.print(
        "Solving " + paths.size() + " instances, (execution id: '" + executionId + "').\nPress ENTER to continue"
    );
    System.in.read();

    final Path outputDir = OUTPUT_DIR.resolve(executionId);
    Files.createDirectories(outputDir);

    try {
      //Iterazione su tutte le istanze
      for (final Path path: paths) {
        System.out.println("------------------------------------------------------------");
        System.out.println("Solving instance '" + path.getFileName() + "'");
        System.out.println("------------------------------------------------------------");
        final Instance instance = Instance.load(path);


        // Call GRASP algorithm
        Solution solution = GRASP.grasp(instance);
        System.out.println(solution);

        //Salvataggio GRASP su file
        Path filePath = outputDir.resolve(instance.id() + ".json");

        // Usa Jackson per convertire l'oggetto Solution in una stringa JSON
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(solution);

        // Salva la stringa JSON nel file
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
          writer.write(json);
        }


      }

    } catch (RuntimeException e) {
      System.out.println(e.getMessage());
    }
  }
}
