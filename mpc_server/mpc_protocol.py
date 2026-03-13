"""
mpc_protocol.py — Core Multi-Party Computation (MPC) Cryptographic Primitives
===============================================================================

Implements:
  • Shamir's Secret Sharing  — (k, n)-threshold scheme over a prime field
  • Additive Secret Sharing  — simple n-out-of-n XOR / modular additive scheme
  • Secure Sum               — coordinator reconstructs the sum of n private inputs
  • Secure Average           — secure sum divided by party count
  • Secure Comparison        — millionaire's-style: determines who holds the larger value
  • Beaver Triple Generation — random (a, b, c=a·b) triples for multiplication

All arithmetic is performed in GF(p) where p is a 127-bit Mersenne prime.
"""

import random
import secrets
from dataclasses import dataclass, field
from typing import Optional

# ---------------------------------------------------------------------------
# Prime field
# ---------------------------------------------------------------------------

# 2^127 - 1  (Mersenne prime — efficient for modular reduction)
PRIME: int = 2**127 - 1


def _mod(x: int) -> int:
    return x % PRIME


def _inv(x: int) -> int:
    """Modular inverse using Fermat's little theorem (p is prime)."""
    return pow(x, PRIME - 2, PRIME)


# ---------------------------------------------------------------------------
# Shamir's Secret Sharing
# ---------------------------------------------------------------------------

@dataclass
class ShamirShare:
    party_id: int       # x-coordinate (1-based)
    value: int          # y-coordinate = poly(party_id) mod p


def shamir_split(secret: int, n: int, k: int) -> list[ShamirShare]:
    """Split *secret* into *n* shares with threshold *k*.

    Any *k* shares can reconstruct the secret; fewer than *k* reveal nothing.

    Args:
        secret: The integer secret (0 ≤ secret < PRIME).
        n:      Total number of shares to produce.
        k:      Reconstruction threshold (k ≤ n).

    Returns:
        List of *n* ShamirShare objects.

    Raises:
        ValueError: If k > n or values are out of range.
    """
    if not (1 <= k <= n):
        raise ValueError(f"k={k} must satisfy 1 ≤ k ≤ n={n}")
    if not (0 <= secret < PRIME):
        raise ValueError(f"secret must be in [0, PRIME)")

    # Random degree-(k-1) polynomial: f(x) = secret + a1·x + a2·x² + ...
    coeffs = [secret] + [secrets.randbelow(PRIME) for _ in range(k - 1)]

    def _eval(x: int) -> int:
        result, power = 0, 1
        for c in coeffs:
            result = _mod(result + c * power)
            power = _mod(power * x)
        return result

    return [ShamirShare(party_id=i, value=_eval(i)) for i in range(1, n + 1)]


def shamir_reconstruct(shares: list[ShamirShare]) -> int:
    """Reconstruct the secret from *k* or more Shamir shares via Lagrange interpolation.

    Args:
        shares: At least *k* ShamirShare objects.

    Returns:
        The reconstructed secret integer.
    """
    secret = 0
    xs = [s.party_id for s in shares]
    for i, si in enumerate(shares):
        xi = si.party_id
        num, den = 1, 1
        for j, xj in enumerate(xs):
            if i != j:
                num = _mod(num * (-xj))
                den = _mod(den * (xi - xj))
        lagrange = _mod(num * _inv(den))
        secret = _mod(secret + si.value * lagrange)
    return secret


# ---------------------------------------------------------------------------
# Additive Secret Sharing  (n-out-of-n)
# ---------------------------------------------------------------------------

def additive_split(secret: int, n: int) -> list[int]:
    """Split *secret* into *n* additive shares: s1 + s2 + ... + sn ≡ secret (mod p).

    Args:
        secret: Integer secret (0 ≤ secret < PRIME).
        n:      Number of parties.

    Returns:
        List of *n* share integers, each in [0, PRIME).
    """
    if n <= 0:
        raise ValueError("n must be ≥ 1")
    shares = [secrets.randbelow(PRIME) for _ in range(n - 1)]
    last = _mod(secret - sum(shares))
    shares.append(last)
    return shares


def additive_reconstruct(shares: list[int]) -> int:
    """Reconstruct secret from all n additive shares.

    Args:
        shares: Complete list of additive shares.

    Returns:
        The original secret integer.
    """
    return _mod(sum(shares))


# ---------------------------------------------------------------------------
# Secure Sum Protocol
# ---------------------------------------------------------------------------

def secure_sum_combine(partial_sums: list[int]) -> int:
    """Combine partial sums contributed by each party to get the global sum.

    In the additive-sharing protocol each party pi holds one share of every
    other party's secret.  Each pi adds its column of shares and sends the
    partial sum to the coordinator.  The coordinator calls this function.

    Args:
        partial_sums: One partial sum per party (each already reduced mod p).

    Returns:
        Sum of all private inputs, mod PRIME.
    """
    return _mod(sum(partial_sums))


def secure_average(partial_sums: list[int], n_parties: int) -> float:
    """Compute the arithmetic mean of *n_parties* private values.

    Args:
        partial_sums: Partial sums from each party.
        n_parties:    Number of contributing parties.

    Returns:
        The floating-point average.
    """
    total = secure_sum_combine(partial_sums)
    # Convert from field element back to a signed integer if > PRIME//2
    signed = total if total <= PRIME // 2 else total - PRIME
    return signed / n_parties


# ---------------------------------------------------------------------------
# Beaver Triples  (for secure multiplication)
# ---------------------------------------------------------------------------

@dataclass
class BeaverTriple:
    """A random triple (a, b, c) with c = a · b mod p, split into party shares."""
    a_shares: list[int]
    b_shares: list[int]
    c_shares: list[int]


def generate_beaver_triple(n: int) -> BeaverTriple:
    """Generate a Beaver multiplication triple for *n* parties.

    Args:
        n: Number of parties.

    Returns:
        BeaverTriple whose a/b/c_shares are additive sharings of random a, b, a·b.
    """
    a = secrets.randbelow(PRIME)
    b = secrets.randbelow(PRIME)
    c = _mod(a * b)
    return BeaverTriple(
        a_shares=additive_split(a, n),
        b_shares=additive_split(b, n),
        c_shares=additive_split(c, n),
    )


def secure_multiply_open(
    x_shares: list[int],
    y_shares: list[int],
    triple: BeaverTriple,
) -> list[int]:
    """Compute additive shares of x·y using a Beaver triple.

    Returns shares of the product — call additive_reconstruct to get the value.

    Args:
        x_shares: Additive shares of x (one per party).
        y_shares: Additive shares of y (one per party).
        triple:   A fresh BeaverTriple (consumed; never reuse a triple).

    Returns:
        Additive shares of x·y.
    """
    n = len(x_shares)
    # d_i = x_i - a_i,  e_i = y_i - b_i
    d_shares = [_mod(x_shares[i] - triple.a_shares[i]) for i in range(n)]
    e_shares = [_mod(y_shares[i] - triple.b_shares[i]) for i in range(n)]

    d = additive_reconstruct(d_shares)   # x - a   (revealed)
    e = additive_reconstruct(e_shares)   # y - b   (revealed)

    # z_i = c_i + d·b_i + e·a_i  +  (d·e / n)  for party 0 only
    z_shares = []
    for i in range(n):
        zi = _mod(triple.c_shares[i] + d * triple.b_shares[i] + e * triple.a_shares[i])
        if i == 0:
            zi = _mod(zi + d * e)
        z_shares.append(zi)
    return z_shares


# ---------------------------------------------------------------------------
# Secure Comparison — Millionaire's Protocol (simplified, 2-party)
# ---------------------------------------------------------------------------

@dataclass
class ComparisonMask:
    masked_a: int   # a + r  (sent publicly)
    r: int          # random mask (stays private with party A)


def comparison_mask_value(a: int) -> ComparisonMask:
    """Party A masks its private value *a* with a random blinding factor.

    Args:
        a: Party A's private integer (0 ≤ a < PRIME).

    Returns:
        A ComparisonMask containing the public masked value and private mask r.
    """
    r = secrets.randbelow(PRIME)
    return ComparisonMask(masked_a=_mod(a + r), r=r)


def comparison_check(masked_a: int, b: int, r: int) -> dict:
    """Coordinator / Party B evaluates the comparison after unmasking.

    In a real protocol this would use OT or garbled circuits.  This simplified
    version is educational and assumes an honest-but-curious coordinator.

    Args:
        masked_a: Masked value received from Party A.
        b:        Party B's private value.
        r:        The mask r revealed by Party A after B commits.

    Returns:
        Dict with 'a_greater', 'b_greater', 'equal' booleans.
    """
    a = _mod(masked_a - r)
    # Convert field elements to signed integers for comparison
    sa = a if a <= PRIME // 2 else a - PRIME
    sb = b if b <= PRIME // 2 else b - PRIME
    return {
        "a_greater": sa > sb,
        "b_greater": sb > sa,
        "equal": sa == sb,
        "difference": abs(sa - sb),
    }


# ---------------------------------------------------------------------------
# Utility helpers
# ---------------------------------------------------------------------------

def int_to_field(value: int) -> int:
    """Map an arbitrary Python integer into GF(p)."""
    return _mod(value)


def field_to_signed_int(fe: int) -> int:
    """Interpret a field element as a signed integer centred at 0."""
    return fe if fe <= PRIME // 2 else fe - PRIME


def generate_session_id() -> str:
    """Return a cryptographically secure 16-byte hex session identifier."""
    return secrets.token_hex(16)
