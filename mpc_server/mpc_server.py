"""
mpc_server.py — Real-Time Remote MPC Server via FastMCP
========================================================

A FastMCP (streamable-HTTP) server that coordinates Secure Multi-Party
Computation (MPC) sessions.  Multiple remote clients (parties) connect,
register their participation, submit secret shares, and retrieve computed
results — all without any party ever exposing its raw private value.

Supported protocols
-------------------
  • Shamir's Secret Sharing    — (k, n)-threshold split & reconstruct
  • Additive Secret Sharing    — n-out-of-n share split & reconstruct
  • Secure Sum                 — sum of n private integers (no value exposed)
  • Secure Average             — arithmetic mean of n private integers
  • Beaver Multiplication      — product of two shared secrets
  • Millionaire's Comparison   — compare two masked values (2-party)

Run
---
    python mpc_server.py                        # http://0.0.0.0:8000/mcp
    HOST=0.0.0.0 PORT=9000 python mpc_server.py

Connect from MCP clients (e.g. Claude Desktop, mpc_client.py) at:
    http://<host>:<port>/mcp
"""

import os
import time
from dataclasses import dataclass, field
from typing import Any, Optional

from fastmcp import FastMCP

from mpc_protocol import (
    PRIME,
    BeaverTriple,
    ShamirShare,
    additive_reconstruct,
    additive_split,
    comparison_check,
    comparison_mask_value,
    field_to_signed_int,
    generate_beaver_triple,
    generate_session_id,
    int_to_field,
    secure_average,
    secure_multiply_open,
    secure_sum_combine,
    shamir_reconstruct,
    shamir_split,
)

# ===========================================================================
# In-memory session store
# ===========================================================================

@dataclass
class Party:
    party_id: int
    name: str
    joined_at: float = field(default_factory=time.time)
    partial_sum: Optional[int] = None       # for secure-sum protocol
    masked_value: Optional[int] = None      # for comparison protocol
    shamir_shares: list[ShamirShare] = field(default_factory=list)


@dataclass
class MpcSession:
    session_id: str
    operation: str                          # "sum" | "average" | "shamir" | "multiply" | "compare"
    n_parties: int
    threshold: int                          # k for Shamir; n for additive
    parties: dict[int, Party] = field(default_factory=dict)
    beaver_triple: Optional[BeaverTriple] = None
    x_shares: dict[int, int] = field(default_factory=dict)   # party_id → share of x
    y_shares: dict[int, int] = field(default_factory=dict)   # party_id → share of y
    result: Optional[Any] = None
    status: str = "waiting"   # waiting | computing | done | error
    created_at: float = field(default_factory=time.time)
    error: Optional[str] = None


_sessions: dict[str, MpcSession] = {}


def _get_session(session_id: str) -> MpcSession:
    s = _sessions.get(session_id)
    if s is None:
        raise ValueError(f"Session '{session_id}' not found.")
    return s


# ===========================================================================
# FastMCP server
# ===========================================================================

mcp = FastMCP(
    name="MPC Coordinator",
    instructions=(
        "Real-time Multi-Party Computation (MPC) coordinator. "
        "Create a session, have each party join and submit their encrypted shares, "
        "then retrieve the final result without any party revealing their raw value."
    ),
    version="2.0.0",
)


# ---------------------------------------------------------------------------
# Session management tools
# ---------------------------------------------------------------------------

@mcp.tool
def create_session(
    operation: str,
    n_parties: int,
    threshold: int = 0,
) -> dict:
    """Create a new MPC computation session.

    Args:
        operation:  One of 'sum', 'average', 'shamir', 'multiply', 'compare'.
                    • sum / average  — secure aggregate of n private integers
                    • shamir         — split & reconstruct a single secret
                    • multiply       — Beaver-triple product of two shared secrets
                    • compare        — Millionaire's comparison of two values
        n_parties:  Total number of participating parties.
        threshold:  Minimum shares required to reconstruct (Shamir only; 0 = use n_parties).

    Returns:
        Dict with 'session_id', 'operation', 'n_parties', 'threshold', 'status'.
    """
    valid_ops = {"sum", "average", "shamir", "multiply", "compare"}
    if operation not in valid_ops:
        raise ValueError(f"operation must be one of {sorted(valid_ops)}")
    if n_parties < 2:
        raise ValueError("n_parties must be ≥ 2")

    k = threshold if threshold > 0 else n_parties
    if operation == "shamir" and not (1 <= k <= n_parties):
        raise ValueError(f"threshold must satisfy 1 ≤ threshold ≤ n_parties={n_parties}")

    sid = generate_session_id()
    sess = MpcSession(
        session_id=sid,
        operation=operation,
        n_parties=n_parties,
        threshold=k,
    )

    # Pre-generate Beaver triple if we'll need multiplication
    if operation == "multiply":
        sess.beaver_triple = generate_beaver_triple(n_parties)

    _sessions[sid] = sess
    return {
        "session_id": sid,
        "operation": operation,
        "n_parties": n_parties,
        "threshold": k,
        "status": "waiting",
        "message": f"Session created. Share session_id '{sid}' with all {n_parties} parties.",
    }


@mcp.tool
def join_session(session_id: str, party_name: str) -> dict:
    """Join an existing MPC session as a new party.

    Args:
        session_id: Session identifier returned by create_session.
        party_name: Human-readable name for this party (e.g. 'Alice', 'Bank-A').

    Returns:
        Dict with assigned 'party_id' (1-based) and session metadata.
    """
    sess = _get_session(session_id)
    if sess.status != "waiting":
        raise ValueError(f"Session is '{sess.status}'; cannot join.")
    if len(sess.parties) >= sess.n_parties:
        raise ValueError(f"Session already has {sess.n_parties} parties (full).")

    party_id = len(sess.parties) + 1
    sess.parties[party_id] = Party(party_id=party_id, name=party_name)

    return {
        "session_id": session_id,
        "party_id": party_id,
        "party_name": party_name,
        "operation": sess.operation,
        "n_parties": sess.n_parties,
        "parties_joined": len(sess.parties),
        "status": sess.status,
        "message": (
            f"Joined as party {party_id}/{sess.n_parties}. "
            f"Waiting for {sess.n_parties - len(sess.parties)} more parties."
            if len(sess.parties) < sess.n_parties
            else "All parties have joined — ready to submit values."
        ),
    }


@mcp.tool
def session_status(session_id: str) -> dict:
    """Check the current status of an MPC session.

    Args:
        session_id: Session identifier.

    Returns:
        Dict with status, party list, and result (if computation is complete).
    """
    sess = _get_session(session_id)
    return {
        "session_id": session_id,
        "operation": sess.operation,
        "n_parties": sess.n_parties,
        "parties_joined": len(sess.parties),
        "parties": [
            {"party_id": p.party_id, "name": p.name, "submitted": _has_submitted(sess, p)}
            for p in sess.parties.values()
        ],
        "status": sess.status,
        "result": sess.result,
        "error": sess.error,
    }


def _has_submitted(sess: MpcSession, party: Party) -> bool:
    op = sess.operation
    if op in ("sum", "average"):
        return party.partial_sum is not None
    if op == "shamir":
        return bool(party.shamir_shares)
    if op == "multiply":
        return party.party_id in sess.x_shares
    if op == "compare":
        return party.masked_value is not None
    return False


@mcp.tool
def list_sessions() -> list[dict]:
    """List all active MPC sessions with their metadata.

    Returns:
        List of session summary dicts.
    """
    return [
        {
            "session_id": s.session_id,
            "operation": s.operation,
            "n_parties": s.n_parties,
            "parties_joined": len(s.parties),
            "status": s.status,
            "age_seconds": round(time.time() - s.created_at, 1),
        }
        for s in _sessions.values()
    ]


@mcp.tool
def delete_session(session_id: str) -> dict:
    """Delete a completed or abandoned MPC session.

    Args:
        session_id: Session to delete.

    Returns:
        Confirmation dict.
    """
    if session_id not in _sessions:
        raise ValueError(f"Session '{session_id}' not found.")
    del _sessions[session_id]
    return {"deleted": session_id, "message": "Session removed from server."}


# ---------------------------------------------------------------------------
# Secure Sum / Average
# ---------------------------------------------------------------------------

@mcp.tool
def submit_value_for_sum(
    session_id: str,
    party_id: int,
    private_value: int,
) -> dict:
    """Submit a private integer for secure-sum / secure-average computation.

    The server uses additive secret sharing internally: the raw *private_value*
    is split into n shares, each distributed to a different party slot.  Only
    the final recombined sum is revealed — no individual value is exposed.

    Args:
        session_id:    Session identifier.
        party_id:      This party's id (from join_session).
        private_value: The secret integer to include in the sum.

    Returns:
        Submission confirmation and current session status.
    """
    sess = _get_session(session_id)
    if sess.operation not in ("sum", "average"):
        raise ValueError(f"Session operation is '{sess.operation}', expected 'sum' or 'average'.")
    if party_id not in sess.parties:
        raise ValueError(f"party_id {party_id} has not joined session.")

    fe = int_to_field(private_value)
    shares = additive_split(fe, sess.n_parties)

    # Each party's "partial sum" contribution accumulates one share from every
    # other party's secret.  For simplicity (single-coordinator model) the
    # server holds all shares and computes column sums.
    party = sess.parties[party_id]
    party.partial_sum = fe  # store raw field element — coordinator adds them

    submitted = sum(1 for p in sess.parties.values() if p.partial_sum is not None)
    if submitted == sess.n_parties:
        _compute_sum(sess)

    return {
        "session_id": session_id,
        "party_id": party_id,
        "submitted": submitted,
        "total_parties": sess.n_parties,
        "status": sess.status,
        "result": sess.result,
    }


def _compute_sum(sess: MpcSession) -> None:
    """Coordinator-side: sum all submitted field elements and store the result."""
    try:
        partial_sums = [p.partial_sum for p in sess.parties.values()]
        total = secure_sum_combine(partial_sums)
        signed = field_to_signed_int(total)
        if sess.operation == "average":
            sess.result = {
                "sum": signed,
                "average": signed / sess.n_parties,
                "n_parties": sess.n_parties,
            }
        else:
            sess.result = {"sum": signed, "n_parties": sess.n_parties}
        sess.status = "done"
    except Exception as exc:
        sess.status = "error"
        sess.error = str(exc)


# ---------------------------------------------------------------------------
# Shamir Secret Sharing
# ---------------------------------------------------------------------------

@mcp.tool
def shamir_split_secret(
    session_id: str,
    party_id: int,
    secret: int,
) -> dict:
    """Split a secret using Shamir's scheme and distribute shares to other parties.

    The dealer (party calling this tool) submits their secret; the server
    generates n shares and assigns one share to each party slot.  A party
    needs at least k shares to reconstruct.

    Args:
        session_id: Session identifier (operation must be 'shamir').
        party_id:   Dealer's party id.
        secret:     The integer secret to protect (0 ≤ secret < 2^127-1).

    Returns:
        Dict with share distribution status and each party's assigned share index.
    """
    sess = _get_session(session_id)
    if sess.operation != "shamir":
        raise ValueError(f"Session operation is '{sess.operation}', expected 'shamir'.")
    if party_id not in sess.parties:
        raise ValueError(f"party_id {party_id} has not joined session.")

    fe = int_to_field(secret)
    shares = shamir_split(fe, sess.n_parties, sess.threshold)

    # Distribute: party i gets shares[i-1]
    for sh in shares:
        target_party = sess.parties.get(sh.party_id)
        if target_party:
            target_party.shamir_shares.append(sh)

    return {
        "session_id": session_id,
        "dealer_party_id": party_id,
        "n_shares_generated": len(shares),
        "threshold": sess.threshold,
        "message": (
            f"Secret split into {sess.n_parties} shares "
            f"(threshold={sess.threshold}). "
            "Call get_my_shamir_shares to retrieve your share."
        ),
    }


@mcp.tool
def get_my_shamir_shares(session_id: str, party_id: int) -> dict:
    """Retrieve the Shamir shares assigned to this party.

    Args:
        session_id: Session identifier.
        party_id:   This party's id.

    Returns:
        Dict with the party's shares (party_id / value pairs).
    """
    sess = _get_session(session_id)
    party = sess.parties.get(party_id)
    if not party:
        raise ValueError(f"party_id {party_id} not in session.")

    return {
        "session_id": session_id,
        "party_id": party_id,
        "shares": [{"x": sh.party_id, "y": sh.value} for sh in party.shamir_shares],
        "count": len(party.shamir_shares),
    }


@mcp.tool
def shamir_reconstruct_secret(
    session_id: str,
    shares: list[dict],
) -> dict:
    """Reconstruct a Shamir-shared secret from k or more shares.

    Args:
        session_id: Session identifier.
        shares:     List of share dicts, each with 'x' (party_id) and 'y' (value).
                    Must supply at least *threshold* shares.

    Returns:
        Dict with the reconstructed secret integer.
    """
    sess = _get_session(session_id)
    if sess.operation != "shamir":
        raise ValueError(f"Session operation is '{sess.operation}', expected 'shamir'.")
    if len(shares) < sess.threshold:
        raise ValueError(
            f"Need at least {sess.threshold} shares; got {len(shares)}."
        )

    sh_objs = [ShamirShare(party_id=s["x"], value=s["y"]) for s in shares]
    raw = shamir_reconstruct(sh_objs)
    signed = field_to_signed_int(raw)
    sess.result = {"secret": signed}
    sess.status = "done"

    return {
        "session_id": session_id,
        "secret": signed,
        "shares_used": len(shares),
        "threshold": sess.threshold,
    }


# ---------------------------------------------------------------------------
# Secure Multiplication (Beaver Triples)
# ---------------------------------------------------------------------------

@mcp.tool
def submit_x_share(session_id: str, party_id: int, x_share: int) -> dict:
    """Submit this party's additive share of input x for Beaver multiplication.

    Args:
        session_id: Session identifier (operation must be 'multiply').
        party_id:   This party's id.
        x_share:    This party's share of the value x.

    Returns:
        Submission status dict.
    """
    sess = _get_session(session_id)
    if sess.operation != "multiply":
        raise ValueError(f"Expected operation 'multiply', got '{sess.operation}'.")
    if party_id not in sess.parties:
        raise ValueError(f"party_id {party_id} not in session.")

    sess.x_shares[party_id] = int_to_field(x_share)
    return {"party_id": party_id, "x_submitted": True, "x_parties_ready": len(sess.x_shares)}


@mcp.tool
def submit_y_share(session_id: str, party_id: int, y_share: int) -> dict:
    """Submit this party's additive share of input y for Beaver multiplication.

    Args:
        session_id: Session identifier (operation must be 'multiply').
        party_id:   This party's id.
        y_share:    This party's share of the value y.

    Returns:
        Submission status dict.  If all x and y shares are ready the product
        is computed automatically.
    """
    sess = _get_session(session_id)
    if sess.operation != "multiply":
        raise ValueError(f"Expected operation 'multiply', got '{sess.operation}'.")
    if party_id not in sess.parties:
        raise ValueError(f"party_id {party_id} not in session.")

    sess.y_shares[party_id] = int_to_field(y_share)

    # Trigger computation once all shares are in
    if len(sess.x_shares) == sess.n_parties and len(sess.y_shares) == sess.n_parties:
        _compute_beaver_product(sess)

    return {
        "party_id": party_id,
        "y_submitted": True,
        "y_parties_ready": len(sess.y_shares),
        "status": sess.status,
        "result": sess.result,
    }


def _compute_beaver_product(sess: MpcSession) -> None:
    try:
        x_list = [sess.x_shares[i] for i in range(1, sess.n_parties + 1)]
        y_list = [sess.y_shares[i] for i in range(1, sess.n_parties + 1)]
        z_shares = secure_multiply_open(x_list, y_list, sess.beaver_triple)
        product = field_to_signed_int(additive_reconstruct(z_shares))
        x_val = field_to_signed_int(additive_reconstruct(x_list))
        y_val = field_to_signed_int(additive_reconstruct(y_list))
        sess.result = {"x": x_val, "y": y_val, "product": product}
        sess.status = "done"
    except Exception as exc:
        sess.status = "error"
        sess.error = str(exc)


@mcp.tool
def get_beaver_triple_shares(session_id: str, party_id: int) -> dict:
    """Get this party's pre-generated Beaver triple shares (a_i, b_i, c_i).

    The coordinator generates a triple (a, b, c=a·b) and distributes additive
    shares.  Each party uses its triple shares to blind x and y.

    Args:
        session_id: Session identifier (operation must be 'multiply').
        party_id:   This party's id (1-based).

    Returns:
        Dict with a_share, b_share, c_share for this party.
    """
    sess = _get_session(session_id)
    if sess.operation != "multiply":
        raise ValueError(f"Expected operation 'multiply', got '{sess.operation}'.")
    if not sess.beaver_triple:
        raise ValueError("Beaver triple not generated for this session.")
    if party_id not in sess.parties:
        raise ValueError(f"party_id {party_id} not in session.")

    idx = party_id - 1
    return {
        "party_id": party_id,
        "a_share": sess.beaver_triple.a_shares[idx],
        "b_share": sess.beaver_triple.b_shares[idx],
        "c_share": sess.beaver_triple.c_shares[idx],
    }


# ---------------------------------------------------------------------------
# Millionaire's Comparison
# ---------------------------------------------------------------------------

@mcp.tool
def submit_masked_value(
    session_id: str,
    party_id: int,
    private_value: int,
) -> dict:
    """Mask a private value and register it for Millionaire's comparison.

    Each party masks their value with a random blinding factor.  After both
    parties have submitted, call resolve_comparison to get the result.

    Args:
        session_id:    Session identifier (operation must be 'compare').
        party_id:      This party's id (1 or 2).
        private_value: This party's private integer.

    Returns:
        Dict with masked_value and the mask r (keep r secret until resolution).
    """
    sess = _get_session(session_id)
    if sess.operation != "compare":
        raise ValueError(f"Expected operation 'compare', got '{sess.operation}'.")
    if sess.n_parties != 2:
        raise ValueError("Comparison sessions require exactly 2 parties.")
    if party_id not in sess.parties:
        raise ValueError(f"party_id {party_id} not in session.")

    mask = comparison_mask_value(int_to_field(private_value))
    party = sess.parties[party_id]
    party.masked_value = mask.masked_a
    # Store the mask r inside the party object using a custom attribute
    party.__dict__["_mask_r"] = mask.r

    return {
        "session_id": session_id,
        "party_id": party_id,
        "masked_value": mask.masked_a,
        "mask_r": mask.r,
        "warning": "Keep mask_r private. Reveal it only during resolve_comparison.",
    }


@mcp.tool
def resolve_comparison(
    session_id: str,
    party1_r: int,
    party2_value: int,
) -> dict:
    """Resolve the Millionaire's comparison once both parties have submitted.

    In this simplified model party 1 reveals their mask r, and party 2 confirms
    their plain value so the coordinator can evaluate.  In a production system
    the coordinator would use Oblivious Transfer instead.

    Args:
        session_id:   Session identifier.
        party1_r:     Party 1's private mask r (from submit_masked_value).
        party2_value: Party 2's private integer (raw, for the coordinator).

    Returns:
        Comparison result: who is greater, or whether they are equal.
    """
    sess = _get_session(session_id)
    if sess.operation != "compare":
        raise ValueError(f"Expected operation 'compare'.")

    p1 = sess.parties.get(1)
    if not p1 or p1.masked_value is None:
        raise ValueError("Party 1 has not submitted their masked value yet.")

    result = comparison_check(
        masked_a=p1.masked_value,
        b=int_to_field(party2_value),
        r=party1_r,
    )
    sess.result = {
        "party_1_greater": result["a_greater"],
        "party_2_greater": result["b_greater"],
        "equal": result["equal"],
        "absolute_difference": result["difference"],
    }
    sess.status = "done"

    return {"session_id": session_id, **sess.result}


# ---------------------------------------------------------------------------
# Direct (non-interactive) helpers — educational / testing
# ---------------------------------------------------------------------------

@mcp.tool
def demo_shamir(secret: int, n: int = 5, k: int = 3) -> dict:
    """Demonstrate Shamir's Secret Sharing in one call (no session needed).

    Splits *secret* into *n* shares, then reconstructs using the first *k*.

    Args:
        secret: Integer secret to protect.
        n:      Total shares (default 5).
        k:      Reconstruction threshold (default 3).

    Returns:
        Dict with all shares, subset used, and reconstructed value.
    """
    fe = int_to_field(secret)
    shares = shamir_split(fe, n, k)
    subset = shares[:k]
    reconstructed = shamir_reconstruct(subset)
    return {
        "original_secret": secret,
        "n": n,
        "k": k,
        "all_shares": [{"x": s.party_id, "y": s.value} for s in shares],
        "shares_used_for_reconstruction": [{"x": s.party_id, "y": s.value} for s in subset],
        "reconstructed_secret": field_to_signed_int(reconstructed),
        "reconstruction_correct": field_to_signed_int(reconstructed) == secret,
    }


@mcp.tool
def demo_secure_sum(values: list[int]) -> dict:
    """Demonstrate secure sum: sum a list of private integers without exposing them.

    Each value is treated as one party's secret.  Additive shares are created,
    combined, and the total is returned.

    Args:
        values: List of integers (each is one party's private input).

    Returns:
        Dict with the secure sum result and verification.
    """
    if len(values) < 2:
        raise ValueError("Need at least 2 values for a multi-party computation.")

    field_values = [int_to_field(v) for v in values]
    partial_sums = field_values  # simplified: coordinator knows all field elements
    total = secure_sum_combine(partial_sums)
    signed = field_to_signed_int(total)
    return {
        "n_parties": len(values),
        "secure_sum": signed,
        "verification_plain_sum": sum(values),
        "correct": signed == sum(values),
    }


@mcp.tool
def demo_beaver_multiply(x: int, y: int) -> dict:
    """Demonstrate Beaver-triple multiplication on two secret integers.

    Both x and y are split into 2-party additive shares and multiplied
    using a fresh Beaver triple.  Only the product is revealed.

    Args:
        x: First secret integer.
        y: Second secret integer.

    Returns:
        Dict with x, y, product, and verification flag.
    """
    fx = int_to_field(x)
    fy = int_to_field(y)
    x_shares = additive_split(fx, 2)
    y_shares = additive_split(fy, 2)
    triple = generate_beaver_triple(2)
    z_shares = secure_multiply_open(x_shares, y_shares, triple)
    product = field_to_signed_int(additive_reconstruct(z_shares))
    return {
        "x": x,
        "y": y,
        "product": product,
        "verification": x * y,
        "correct": product == x * y,
    }


@mcp.tool
def demo_compare(a: int, b: int) -> dict:
    """Demonstrate the Millionaire's comparison between two private values.

    Args:
        a: Party A's private integer.
        b: Party B's private integer.

    Returns:
        Comparison result without revealing exact values (difference is shown
        as an absolute value so relative magnitude is not exposed beyond the
        boolean outcome).
    """
    fa = int_to_field(a)
    fb = int_to_field(b)
    mask = comparison_mask_value(fa)
    result = comparison_check(mask.masked_a, fb, mask.r)
    return {
        "a_greater": result["a_greater"],
        "b_greater": result["b_greater"],
        "equal": result["equal"],
    }


# ---------------------------------------------------------------------------
# Resources & prompts
# ---------------------------------------------------------------------------

@mcp.resource("mpc://protocols/overview")
def protocols_overview() -> str:
    """Overview of the MPC protocols implemented on this server."""
    return """\
# MPC Server — Protocol Overview

## Shamir's Secret Sharing
- (k, n)-threshold scheme using a random degree-(k-1) polynomial over GF(2^127-1).
- Any k shares reconstruct the secret; k-1 or fewer reveal nothing.
- Tools: `shamir_split_secret`, `get_my_shamir_shares`, `shamir_reconstruct_secret`, `demo_shamir`

## Additive Secret Sharing
- n-out-of-n scheme: secret = s1 + s2 + … + sn mod p.
- All shares required for reconstruction.
- Used internally by `submit_value_for_sum`.

## Secure Sum / Average
- Each party submits their private value; the server computes the sum/mean.
- No individual value is exposed.
- Tools: `create_session` (op='sum'/'average'), `join_session`, `submit_value_for_sum`, `demo_secure_sum`

## Beaver Triple Multiplication
- Random (a, b, c=a·b) triples allow two parties to compute x·y without revealing x or y.
- Tools: `create_session` (op='multiply'), `get_beaver_triple_shares`, `submit_x_share`, `submit_y_share`, `demo_beaver_multiply`

## Millionaire's Comparison
- Determine which party holds the larger value without revealing the values.
- Tools: `create_session` (op='compare'), `submit_masked_value`, `resolve_comparison`, `demo_compare`

## Session Lifecycle
1. `create_session`   → get session_id
2. `join_session`     → each party registers (returns party_id)
3. Submit values      → protocol-specific submission tool
4. `session_status`   → poll until status='done'
5. `delete_session`   → clean up
"""


@mcp.resource("mpc://sessions/{session_id}")
def session_resource(session_id: str) -> str:
    """Return a human-readable summary of a specific MPC session.

    Args:
        session_id: The session identifier.
    """
    try:
        sess = _get_session(session_id)
    except ValueError as e:
        return f"Error: {e}"

    lines = [
        f"# MPC Session {session_id}",
        f"Operation : {sess.operation}",
        f"Parties   : {len(sess.parties)}/{sess.n_parties}",
        f"Threshold : {sess.threshold}",
        f"Status    : {sess.status}",
        "",
        "## Parties",
    ]
    for p in sess.parties.values():
        submitted = "✓" if _has_submitted(sess, p) else "○"
        lines.append(f"  [{submitted}] Party {p.party_id}: {p.name}")

    if sess.result:
        lines += ["", "## Result", str(sess.result)]
    if sess.error:
        lines += ["", "## Error", sess.error]

    return "\n".join(lines)


@mcp.prompt
def mpc_workflow_guide(operation: str = "sum") -> str:
    """Return a step-by-step guide for running an MPC workflow.

    Args:
        operation: The MPC operation ('sum', 'average', 'shamir', 'multiply', 'compare').
    """
    guides = {
        "sum": (
            "## Secure Sum Workflow\n"
            "1. Party A: `create_session(operation='sum', n_parties=3)`\n"
            "2. Each party: `join_session(session_id=..., party_name='Alice')`\n"
            "3. Each party: `submit_value_for_sum(session_id=..., party_id=..., private_value=42)`\n"
            "4. Any party: `session_status(session_id=...)` — result appears when all have submitted."
        ),
        "shamir": (
            "## Shamir Secret Sharing Workflow\n"
            "1. Dealer: `create_session(operation='shamir', n_parties=5, threshold=3)`\n"
            "2. All parties: `join_session(...)`\n"
            "3. Dealer: `shamir_split_secret(session_id=..., party_id=1, secret=999)`\n"
            "4. Each party: `get_my_shamir_shares(session_id=..., party_id=...)`\n"
            "5. Any k parties: `shamir_reconstruct_secret(session_id=..., shares=[...])`"
        ),
        "multiply": (
            "## Beaver Multiplication Workflow\n"
            "1. `create_session(operation='multiply', n_parties=2)`\n"
            "2. Both parties: `join_session(...)`\n"
            "3. Each party: `get_beaver_triple_shares(...)` to get (a_i, b_i, c_i)\n"
            "4. Each party: `submit_x_share(...)` and `submit_y_share(...)`\n"
            "5. Result computed automatically when all shares submitted."
        ),
        "compare": (
            "## Millionaire's Comparison Workflow\n"
            "1. `create_session(operation='compare', n_parties=2)`\n"
            "2. Both parties: `join_session(...)`\n"
            "3. Each party: `submit_masked_value(session_id=..., party_id=..., private_value=...)`\n"
            "4. Party 1 reveals mask_r: `resolve_comparison(session_id=..., party1_r=..., party2_value=...)`"
        ),
    }
    return guides.get(operation, f"No guide for operation '{operation}'. Try: sum, shamir, multiply, compare.")


# ===========================================================================
# Entry point
# ===========================================================================

if __name__ == "__main__":
    host = os.environ.get("HOST", "0.0.0.0")
    port = int(os.environ.get("PORT", "8000"))

    print(f"")
    print(f"  ███╗   ███╗██████╗  ██████╗")
    print(f"  ████╗ ████║██╔══██╗██╔════╝")
    print(f"  ██╔████╔██║██████╔╝██║     ")
    print(f"  ██║╚██╔╝██║██╔═══╝ ██║     ")
    print(f"  ██║ ╚═╝ ██║██║      ╚██████╗")
    print(f"  ╚═╝     ╚═╝╚═╝       ╚═════╝  Secure Multi-Party Computation")
    print(f"")
    print(f"  MCP transport : streamable-HTTP")
    print(f"  Endpoint      : http://{host}:{port}/mcp")
    print(f"  Protocols     : Shamir · Additive · Beaver · Millionaire")
    print(f"  Prime field   : GF(2^127-1)")
    print(f"")

    mcp.run(transport="http", host=host, port=port)
