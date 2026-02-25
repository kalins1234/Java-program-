import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SecondRepeatedChar {

    /**
     * Finds the second repeated character in a string using Stream API.
     *
     * A "repeated character" is one that appears more than once.
     * We want the SECOND such character (in order of first appearance).
     *
     * Example: "programming"
     *   p-1, r-2, o-1, g-2, a-1, m-2, i-1, n-1
     *   First  repeated char  → 'r'  (first char with count > 1)
     *   Second repeated char  → 'g'  (second char with count > 1)
     */
    public static Optional<Character> findSecondRepeated(String input) {

        // Step 1 – Build a frequency map that preserves insertion order
        Map<Character, Long> frequencyMap = input.chars()           // IntStream of UTF-16 code units
                .mapToObj(c -> (char) c)                            // Stream<Character>
                .collect(Collectors.groupingBy(
                        Function.identity(),                         // key   = the character itself
                        LinkedHashMap::new,                          // use LinkedHashMap to keep first-seen order
                        Collectors.counting()                        // value = how many times it appears
                ));

        // Step 2 – Stream the ordered entries, keep only repeated chars, skip the first one
        return frequencyMap.entrySet()
                .stream()                                            // Stream<Map.Entry<Character, Long>>
                .filter(e -> e.getValue() > 1)                      // keep only chars that appear more than once
                .map(Map.Entry::getKey)                              // extract just the Character
                .skip(1)                                             // skip the FIRST repeated char → land on the second
                .findFirst();                                        // return it wrapped in Optional (empty if none)
    }

    public static void main(String[] args) {
        String[] testCases = {
                "programming",   // r(2), g(2), m(2)  → second repeated = 'g'
                "aabbcc",        // a(2), b(2), c(2)  → second repeated = 'b'
                "swiss",         // s(3)              → only one repeated char → empty
                "java streams",  // a(3), s(2)        → second repeated = 's'
                "abcabc",        // a(2), b(2), c(2)  → second repeated = 'b'
                "hello world"    // l(3), o(2)        → second repeated = 'o'
        };

        for (String input : testCases) {
            Optional<Character> result = findSecondRepeated(input);
            System.out.printf("Input : \"%s\"%n", input);
            System.out.printf("Output: %s%n%n",
                    result.map(String::valueOf).orElse("No second repeated character found"));
        }
    }
}
