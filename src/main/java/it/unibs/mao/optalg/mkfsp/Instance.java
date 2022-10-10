package it.unibs.mao.optalg.mkfsp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An immutable class that holds a MKFSP instance data.
 */
public record Instance(
    String id,
    @JsonProperty("n_items") int nItems,
    @JsonProperty("n_families")int nFamilies,
    @JsonProperty("n_knapsacks") int nKnapsacks,
    @JsonProperty("n_resources") int nResources,
    int[] profits,
    int[] penalties,
    @JsonProperty("first_items") int[] firstItems,
    int[][] items,
    int[][] knapsacks) {

  // Property naming strategies do not work with Java record, see this issue
  //   https://github.com/FasterXML/jackson-databind/issues/2992
  // A temporary workaround is to manually annotate the fields with the wrong
  // naming pattern. When bug is fixed it will be enough to use
  //   .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  private static final ObjectMapper OBJ_MAPPER = new ObjectMapper();

  /**
   * Reads a JSON file containing a MKFSP instance data and returns the
   * corresponding Instance object.
   *
   * @param path  the path to the JSON file
   * @return      the instance object
   * @throws IOException
   */
  public static Instance load(final Path path) throws IOException {
    return OBJ_MAPPER.readValue(path.toFile(), Instance.class);
  }

  /**
   * Checks wheter the given solution is feasible.
   *
   * @param solution  an array containing {@code nItems} integers.
   *                  {@code solution[i]} must be the 0-based index of the
   *                  knapsack where item {@code i} is loaded or {@code -1} if
   *                  the item is not selected.
   * @param objValue  the value of the solution.
   * @return          an object describing the feasibility of the given
   *                  {@code solution}
   * @see FeasibilityCheck
   */
  public FeasibilityCheck checkFeasibility(final int[] solution, final double objValue) {
    if (solution == null) {
      final String errMsg = "Solution can not be null";
      return new FeasibilityCheck(false, new String[] { errMsg });
    } else if (solution.length != nItems) {
      final String errMsg = "Solution contains " + solution.length + " items instead of " + nItems;
      return new FeasibilityCheck(false, new String[] { errMsg });
    }

    boolean isValid = true;
    final List<String> errorMessages = new ArrayList<>();

    int expectedObjValue = 0;
    final Map<Integer,Set<Integer>> splits = new HashMap<>();
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
            usedResources[k][r] += items[i][r];
          }
          splits.computeIfAbsent(j, (key) -> new HashSet<>()).add(k);
        } else if (k != -1) {
          isValid = false;
          errorMessages.add(
              "Item " + i + " is associated to knapsack k = " + k +
              " (expected: 0 <= k < " + nKnapsacks + " or k = -1)"
          );
        }
      }

      final int familySize = endItem - firstItem;
      if (itemCount == familySize) {
        expectedObjValue += profits[j] - penalties[j] * (splits.get(j).size() - 1);
      } else if (itemCount != 0) {
        isValid = false;
        errorMessages.add(
            "Family " + j + " is only partially selected: found " +
            itemCount + " items out of " + familySize);
      }
    }

    for (int k = 0; k < nKnapsacks; ++k) {
      for (int r = 0; r < nResources; ++r) {
        if (knapsacks[k][r] < usedResources[k][r]) {
          isValid = false;
          errorMessages.add(
              "Knapsack " + k + " with a maximum capacity of " + knapsacks[k][r] +
              " units for resource " + r + " is loaded with " + usedResources[k][r] + " units"
          );
        }
      }
    }

    if (objValue != expectedObjValue) {
      isValid = false;
      errorMessages.add(
          "Objective value is " + objValue + " != " +
          expectedObjValue + " (expected objective value)"
      );
    }

    return new FeasibilityCheck(isValid, errorMessages.toArray(new String[0]));
  }
}
