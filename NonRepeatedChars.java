import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NonRepeatedChars {

    /**
     * Returns a list of non-repeated (unique) characters from the given string,
     * preserving their original order of appearance.
     *
     * Approach:
     *  1. Stream each character of the string.
     *  2. Collect into a LinkedHashMap to count frequency while preserving insertion order.
     *  3. Filter entries whose count == 1.
     *  4. Map back to characters.
     */
    public static List<Character> findNonRepeated(String input) {
        return input.chars()                                          // IntStream of char codes
                .mapToObj(c -> (char) c)                             // Stream<Character>
                .collect(Collectors.groupingBy(
                        Function.identity(),                          // group by character
                        LinkedHashMap::new,                           // preserve insertion order
                        Collectors.counting()                         // count occurrences
                ))
                .entrySet().stream()
                .filter(e -> e.getValue() == 1)                      // keep only unique chars
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        String[] testCases = {
                "programming",
                "aabbcc",
                "swiss",
                "java streams",
                "abcabc"
        };

        for (String input : testCases) {
            List<Character> result = findNonRepeated(input);
            System.out.printf("Input : \"%s\"%n", input);
            System.out.printf("Output: %s%n%n", result);
        }
    }
}
