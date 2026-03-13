"""
Remote MCP Server — Java Algorithms & Utilities
Built with FastMCP 3.x using streamable-HTTP (remote) transport.

Run:
    python server.py                          # default: http://0.0.0.0:8000/mcp
    HOST=0.0.0.0 PORT=8080 python server.py   # custom host/port
"""

import os
from collections import Counter, OrderedDict
from typing import Optional

from fastmcp import FastMCP

# ---------------------------------------------------------------------------
# Server initialisation
# ---------------------------------------------------------------------------

mcp = FastMCP(
    name="Java Algorithms MCP Server",
    instructions=(
        "An MCP server that exposes algorithm utilities (array merging, "
        "character analysis, sorting, searching) together with a few "
        "general-purpose helpers. All tools are stateless and safe to call "
        "in any order."
    ),
    version="1.0.0",
)

# ===========================================================================
# TOOLS
# ===========================================================================

# ---------------------------------------------------------------------------
# Array tools
# ---------------------------------------------------------------------------


@mcp.tool
def merge_sorted_arrays(arr1: list[int], arr2: list[int]) -> list[int]:
    """Merge two sorted integer arrays into a single sorted array.

    Uses the two-pointer technique (O(n+m) time, O(n+m) space).

    Args:
        arr1: First sorted array of integers.
        arr2: Second sorted array of integers.

    Returns:
        A new sorted array containing all elements from both arrays.
    """
    result: list[int] = []
    i = j = 0
    while i < len(arr1) and j < len(arr2):
        if arr1[i] <= arr2[j]:
            result.append(arr1[i])
            i += 1
        else:
            result.append(arr2[j])
            j += 1
    result.extend(arr1[i:])
    result.extend(arr2[j:])
    return result


@mcp.tool
def binary_search(arr: list[int], target: int) -> int:
    """Search for a target value in a sorted array using binary search.

    Args:
        arr: A sorted list of integers to search in.
        target: The integer value to find.

    Returns:
        The zero-based index of the target, or -1 if not found.
    """
    lo, hi = 0, len(arr) - 1
    while lo <= hi:
        mid = (lo + hi) // 2
        if arr[mid] == target:
            return mid
        elif arr[mid] < target:
            lo = mid + 1
        else:
            hi = mid - 1
    return -1


@mcp.tool
def find_duplicates(arr: list[int]) -> list[int]:
    """Return all duplicate integers in the array (values that appear more than once).

    Args:
        arr: List of integers.

    Returns:
        Sorted list of integers that appear more than once (each reported once).
    """
    counts = Counter(arr)
    return sorted(val for val, cnt in counts.items() if cnt > 1)


# ---------------------------------------------------------------------------
# String / character tools
# ---------------------------------------------------------------------------


@mcp.tool
def find_non_repeated_chars(text: str) -> list[str]:
    """Return all characters that appear exactly once in the input, preserving order.

    Mirrors the Java NonRepeatedChars implementation using a linked frequency map.

    Args:
        text: The input string to analyse.

    Returns:
        Ordered list of characters (as single-character strings) that appear
        exactly once in *text*.
    """
    freq: dict[str, int] = OrderedDict()
    for ch in text:
        freq[ch] = freq.get(ch, 0) + 1
    return [ch for ch, cnt in freq.items() if cnt == 1]


@mcp.tool
def find_second_repeated_char(text: str) -> Optional[str]:
    """Find the second character (by first-appearance order) that appears more than once.

    Mirrors the Java SecondRepeatedChar implementation.

    Args:
        text: The input string to analyse.

    Returns:
        The second repeated character, or None if fewer than two repeated
        characters exist.
    """
    freq: dict[str, int] = OrderedDict()
    for ch in text:
        freq[ch] = freq.get(ch, 0) + 1

    repeated = [ch for ch, cnt in freq.items() if cnt > 1]
    return repeated[1] if len(repeated) >= 2 else None


@mcp.tool
def count_char_frequencies(text: str) -> dict[str, int]:
    """Count the frequency of every character in the input string.

    Args:
        text: The string to analyse.

    Returns:
        A mapping from character to occurrence count, ordered by first appearance.
    """
    freq: dict[str, int] = OrderedDict()
    for ch in text:
        freq[ch] = freq.get(ch, 0) + 1
    return dict(freq)


@mcp.tool
def is_anagram(s: str, t: str) -> bool:
    """Check whether two strings are anagrams of each other (case-sensitive).

    Args:
        s: First string.
        t: Second string.

    Returns:
        True if *s* and *t* contain exactly the same characters with the same
        frequencies, False otherwise.
    """
    return Counter(s) == Counter(t)


@mcp.tool
def reverse_string(text: str) -> str:
    """Reverse the characters in a string.

    Args:
        text: The string to reverse.

    Returns:
        The reversed string.
    """
    return text[::-1]


# ---------------------------------------------------------------------------
# Sorting tools
# ---------------------------------------------------------------------------


@mcp.tool
def bubble_sort(arr: list[int]) -> list[int]:
    """Sort a list of integers using bubble sort.

    Args:
        arr: Unsorted list of integers.

    Returns:
        A new sorted list (ascending order).
    """
    a = list(arr)
    n = len(a)
    for i in range(n):
        for j in range(n - i - 1):
            if a[j] > a[j + 1]:
                a[j], a[j + 1] = a[j + 1], a[j]
    return a


@mcp.tool
def quick_sort(arr: list[int]) -> list[int]:
    """Sort a list of integers using quicksort (Lomuto partition scheme).

    Args:
        arr: Unsorted list of integers.

    Returns:
        A new sorted list (ascending order).
    """
    def _qs(a: list[int], lo: int, hi: int) -> None:
        if lo < hi:
            pivot = a[hi]
            i = lo - 1
            for j in range(lo, hi):
                if a[j] <= pivot:
                    i += 1
                    a[i], a[j] = a[j], a[i]
            a[i + 1], a[hi] = a[hi], a[i + 1]
            p = i + 1
            _qs(a, lo, p - 1)
            _qs(a, p + 1, hi)

    result = list(arr)
    if result:
        _qs(result, 0, len(result) - 1)
    return result


# ---------------------------------------------------------------------------
# Math / number theory tools
# ---------------------------------------------------------------------------


@mcp.tool
def is_prime(n: int) -> bool:
    """Check whether an integer is a prime number.

    Args:
        n: The integer to test (must be >= 0).

    Returns:
        True if *n* is prime, False otherwise.
    """
    if n < 2:
        return False
    if n < 4:
        return True
    if n % 2 == 0 or n % 3 == 0:
        return False
    i = 5
    while i * i <= n:
        if n % i == 0 or n % (i + 2) == 0:
            return False
        i += 6
    return True


@mcp.tool
def fibonacci(n: int) -> list[int]:
    """Return the first *n* Fibonacci numbers.

    Args:
        n: How many Fibonacci numbers to generate (must be >= 1, max 100).

    Returns:
        List of the first *n* Fibonacci numbers starting from 0.

    Raises:
        ValueError: If *n* is less than 1 or greater than 100.
    """
    if not (1 <= n <= 100):
        raise ValueError("n must be between 1 and 100 inclusive.")
    seq = [0, 1]
    while len(seq) < n:
        seq.append(seq[-1] + seq[-2])
    return seq[:n]


@mcp.tool
def gcd(a: int, b: int) -> int:
    """Compute the Greatest Common Divisor of two non-negative integers.

    Args:
        a: First non-negative integer.
        b: Second non-negative integer.

    Returns:
        The GCD of *a* and *b*.
    """
    while b:
        a, b = b, a % b
    return a


# ===========================================================================
# RESOURCES
# ===========================================================================


@mcp.resource("repo://algorithms/overview")
def algorithms_overview() -> str:
    """Overview of the algorithm implementations available in this repository."""
    return """\
# Java Algorithms Repository — Overview

## Implemented Algorithms

### Array Operations
- **MergeSortedArrays** — Two-pointer merge of two sorted integer arrays (O(n+m)).

### String / Character Analysis
- **NonRepeatedChars** — Finds characters appearing exactly once (LinkedHashMap, O(n)).
- **SecondRepeatedChar** — Finds the second character appearing more than once (LinkedHashMap + Stream, O(n)).

### Algo-Trading
- Signal generation and back-testing utilities (see `algo-trading/` directory).

## MCP Tools (this server)
All Java algorithms above are exposed as MCP tools plus additional helpers:
- `merge_sorted_arrays`, `binary_search`, `find_duplicates`
- `find_non_repeated_chars`, `find_second_repeated_char`, `count_char_frequencies`, `is_anagram`, `reverse_string`
- `bubble_sort`, `quick_sort`
- `is_prime`, `fibonacci`, `gcd`
"""


@mcp.resource("repo://algorithms/{name}")
def algorithm_detail(name: str) -> str:
    """Return implementation notes for a specific algorithm by name.

    Args:
        name: Algorithm name (e.g. 'merge_sorted_arrays', 'find_non_repeated_chars').
    """
    details: dict[str, str] = {
        "merge_sorted_arrays": (
            "Two-pointer merge: iterate both arrays simultaneously, always picking the "
            "smaller head element. O(n+m) time, O(n+m) space."
        ),
        "find_non_repeated_chars": (
            "Build a LinkedHashMap to count character frequencies while preserving "
            "insertion order, then filter entries with count == 1. O(n) time."
        ),
        "find_second_repeated_char": (
            "Same LinkedHashMap frequency pass, then stream the entries keeping only "
            "those with count > 1, skip(1) to land on the second, findFirst(). O(n)."
        ),
        "binary_search": "Classic iterative binary search. O(log n) time.",
        "bubble_sort": "O(n²) comparison sort — useful for teaching; avoid on large inputs.",
        "quick_sort": "Lomuto partition quicksort. O(n log n) average, O(n²) worst case.",
        "fibonacci": "Iterative generation in O(n) time and O(n) space.",
        "is_prime": "Trial division up to sqrt(n) with 6k±1 optimisation. O(√n).",
        "gcd": "Euclidean algorithm. O(log min(a,b)).",
    }
    info = details.get(name.lower())
    if info:
        return f"## {name}\n\n{info}"
    return f"No detail found for algorithm '{name}'. Available: {', '.join(details)}."


# ===========================================================================
# PROMPTS
# ===========================================================================


@mcp.prompt
def algorithm_explainer(algorithm_name: str) -> str:
    """Generate a prompt that asks Claude to explain an algorithm in depth.

    Args:
        algorithm_name: The name of the algorithm to explain.
    """
    return (
        f"Please explain the '{algorithm_name}' algorithm in detail. Cover:\n"
        "1. What problem it solves\n"
        "2. Step-by-step walkthrough with a small example\n"
        "3. Time and space complexity\n"
        "4. Common use cases and trade-offs compared to alternatives"
    )


@mcp.prompt
def code_review_prompt(language: str = "Java") -> str:
    """Generate a prompt for reviewing algorithm implementations.

    Args:
        language: Programming language of the code to review (default: Java).
    """
    return (
        f"Review the following {language} algorithm implementation and provide feedback on:\n"
        "1. Correctness — does it handle all edge cases?\n"
        "2. Time and space complexity\n"
        "3. Code readability and naming conventions\n"
        "4. Any potential improvements or alternative approaches\n\n"
        "Paste the code below:"
    )


# ===========================================================================
# Entry point
# ===========================================================================

if __name__ == "__main__":
    host = os.environ.get("HOST", "0.0.0.0")
    port = int(os.environ.get("PORT", "8000"))

    print(f"Starting Java Algorithms MCP Server on http://{host}:{port}/mcp")
    mcp.run(
        transport="http",
        host=host,
        port=port,
    )
