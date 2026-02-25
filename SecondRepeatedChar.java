import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SecondRepeatedChar {

    public static Optional<Character> findSecondRepeated(String input) {
        return input.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.groupingBy(
                        Function.identity(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .skip(1)
                .findFirst();
    }

    public static void main(String[] args) {
        String[] testCases = {
                "programming",
                "aabbcc",
                "swiss",
                "java streams",
                "abcabc",
                "hello world",
                "aabbc"
        };

        for (String input : testCases) {
            Optional<Character> result = findSecondRepeated(input);
            System.out.printf("Input : \"%s\"%n", input);
            System.out.printf("Output: %s%n%n",
                    result.isPresent() ? result.get() : "No second repeated character found");
        }
    }
}
