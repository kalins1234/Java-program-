"""
mpc_client.py — Python Client for the MPC FastMCP Server
=========================================================

A high-level async client that wraps fastmcp.Client to participate in
Multi-Party Computation sessions hosted on mpc_server.py.

Usage
-----
  # 1. Run the server:
  #       python mpc_server.py

  # 2. Run the built-in demo:
  #       python mpc_client.py

  # 3. Or import and use programmatically:
  #       from mpc_client import MPCClient
  #       async with MPCClient("http://localhost:8000/mcp") as c:
  #           result = await c.demo_secure_sum([100, 200, 300])

Environment variable
--------------------
  MPC_SERVER_URL  — override the default server URL (default: http://localhost:8000/mcp)
"""

import asyncio
import json
import os
import sys
from typing import Any, Optional

try:
    from fastmcp import Client
except ImportError:
    print("ERROR: fastmcp is not installed.  Run: pip install fastmcp")
    sys.exit(1)


# ---------------------------------------------------------------------------
# Low-level helper
# ---------------------------------------------------------------------------

class MPCClient:
    """Async client for the MPC FastMCP server.

    Example
    -------
    >>> async with MPCClient() as client:
    ...     result = await client.demo_secure_sum([10, 20, 30])
    ...     print(result)
    """

    def __init__(self, url: str = ""):
        self.url = url or os.environ.get(
            "MPC_SERVER_URL", "http://localhost:8000/mcp"
        )
        self._client: Optional[Client] = None

    async def __aenter__(self) -> "MPCClient":
        self._client = Client(self.url)
        await self._client.__aenter__()
        return self

    async def __aexit__(self, *args) -> None:
        if self._client:
            await self._client.__aexit__(*args)

    # ------------------------------------------------------------------
    # Core call helper
    # ------------------------------------------------------------------

    async def call(self, tool_name: str, **kwargs) -> Any:
        """Call an MPC server tool and return the parsed result."""
        if self._client is None:
            raise RuntimeError("Use 'async with MPCClient() as c:' context manager.")
        result = await self._client.call_tool(tool_name, kwargs)
        # FastMCP returns a list of content items; extract text
        for item in result:
            if hasattr(item, "text"):
                try:
                    return json.loads(item.text)
                except json.JSONDecodeError:
                    return item.text
        return result

    # ------------------------------------------------------------------
    # Session management
    # ------------------------------------------------------------------

    async def create_session(
        self,
        operation: str,
        n_parties: int,
        threshold: int = 0,
    ) -> dict:
        """Create a new MPC session. Returns session metadata including session_id."""
        return await self.call(
            "create_session",
            operation=operation,
            n_parties=n_parties,
            threshold=threshold,
        )

    async def join_session(self, session_id: str, party_name: str) -> dict:
        """Join an existing session. Returns assigned party_id."""
        return await self.call("join_session", session_id=session_id, party_name=party_name)

    async def session_status(self, session_id: str) -> dict:
        """Poll session status and result."""
        return await self.call("session_status", session_id=session_id)

    async def list_sessions(self) -> list:
        """List all active sessions on the server."""
        return await self.call("list_sessions")

    async def delete_session(self, session_id: str) -> dict:
        """Delete a completed/abandoned session."""
        return await self.call("delete_session", session_id=session_id)

    # ------------------------------------------------------------------
    # Secure Sum / Average
    # ------------------------------------------------------------------

    async def submit_value_for_sum(
        self, session_id: str, party_id: int, private_value: int
    ) -> dict:
        """Submit a private integer for secure-sum computation."""
        return await self.call(
            "submit_value_for_sum",
            session_id=session_id,
            party_id=party_id,
            private_value=private_value,
        )

    async def secure_sum(
        self, private_values: list[int], party_names: Optional[list[str]] = None
    ) -> dict:
        """High-level: run a complete secure-sum protocol for a list of private values.

        This method acts as all parties simultaneously (useful for testing).
        In a real deployment each party would call join_session and
        submit_value_for_sum independently from their own process.

        Args:
            private_values: One integer per party.
            party_names:    Optional names; defaults to 'Party-1', 'Party-2', …

        Returns:
            Final result dict with 'sum' and 'n_parties'.
        """
        n = len(private_values)
        names = party_names or [f"Party-{i+1}" for i in range(n)]

        sess = await self.create_session(operation="sum", n_parties=n)
        sid = sess["session_id"]

        party_ids = []
        for name in names:
            info = await self.join_session(sid, name)
            party_ids.append(info["party_id"])

        result = None
        for pid, value in zip(party_ids, private_values):
            resp = await self.submit_value_for_sum(sid, pid, value)
            if resp.get("status") == "done":
                result = resp["result"]

        await self.delete_session(sid)
        return result

    async def secure_average(
        self, private_values: list[int], party_names: Optional[list[str]] = None
    ) -> dict:
        """High-level: run a complete secure-average protocol.

        Args:
            private_values: One integer per party.
            party_names:    Optional names.

        Returns:
            Final result dict with 'sum', 'average', and 'n_parties'.
        """
        n = len(private_values)
        names = party_names or [f"Party-{i+1}" for i in range(n)]

        sess = await self.create_session(operation="average", n_parties=n)
        sid = sess["session_id"]

        party_ids = []
        for name in names:
            info = await self.join_session(sid, name)
            party_ids.append(info["party_id"])

        result = None
        for pid, value in zip(party_ids, private_values):
            resp = await self.submit_value_for_sum(sid, pid, value)
            if resp.get("status") == "done":
                result = resp["result"]

        await self.delete_session(sid)
        return result

    # ------------------------------------------------------------------
    # Shamir Secret Sharing
    # ------------------------------------------------------------------

    async def shamir_split(
        self, session_id: str, dealer_party_id: int, secret: int
    ) -> dict:
        return await self.call(
            "shamir_split_secret",
            session_id=session_id,
            party_id=dealer_party_id,
            secret=secret,
        )

    async def get_my_shamir_shares(
        self, session_id: str, party_id: int
    ) -> dict:
        return await self.call(
            "get_my_shamir_shares",
            session_id=session_id,
            party_id=party_id,
        )

    async def shamir_reconstruct(
        self, session_id: str, shares: list[dict]
    ) -> dict:
        return await self.call(
            "shamir_reconstruct_secret",
            session_id=session_id,
            shares=shares,
        )

    async def shamir_workflow(
        self, secret: int, n: int = 5, k: int = 3
    ) -> dict:
        """High-level: full Shamir split-and-reconstruct using the first k parties.

        Args:
            secret: Integer to protect.
            n:      Total shares.
            k:      Reconstruction threshold.

        Returns:
            Dict with original secret and reconstructed value.
        """
        sess = await self.create_session(operation="shamir", n_parties=n, threshold=k)
        sid = sess["session_id"]

        party_ids = []
        for i in range(1, n + 1):
            info = await self.join_session(sid, f"Party-{i}")
            party_ids.append(info["party_id"])

        await self.shamir_split(sid, party_ids[0], secret)

        all_shares = []
        for pid in party_ids[:k]:
            share_info = await self.get_my_shamir_shares(sid, pid)
            all_shares.extend(share_info["shares"])

        recon = await self.shamir_reconstruct(sid, all_shares[:k])
        await self.delete_session(sid)
        return recon

    # ------------------------------------------------------------------
    # Beaver Multiplication
    # ------------------------------------------------------------------

    async def beaver_multiply_workflow(self, x: int, y: int) -> dict:
        """High-level: secure multiplication of x and y using Beaver triples.

        Args:
            x: First private integer (Party 1's secret).
            y: Second private integer (Party 2's secret).

        Returns:
            Dict with x, y, product and verification.
        """
        sess = await self.create_session(operation="multiply", n_parties=2)
        sid = sess["session_id"]

        p1 = await self.join_session(sid, "Alice")
        p2 = await self.join_session(sid, "Bob")
        pid1, pid2 = p1["party_id"], p2["party_id"]

        # Each party gets their Beaver triple shares
        t1 = await self.call("get_beaver_triple_shares", session_id=sid, party_id=pid1)
        t2 = await self.call("get_beaver_triple_shares", session_id=sid, party_id=pid2)

        # Split x and y into additive shares locally (simulated here)
        from mpc_protocol import additive_split, int_to_field
        x_shares = additive_split(int_to_field(x), 2)
        y_shares = additive_split(int_to_field(y), 2)

        await self.call("submit_x_share", session_id=sid, party_id=pid1, x_share=x_shares[0])
        await self.call("submit_x_share", session_id=sid, party_id=pid2, x_share=x_shares[1])
        await self.call("submit_y_share", session_id=sid, party_id=pid1, y_share=y_shares[0])
        result = await self.call(
            "submit_y_share", session_id=sid, party_id=pid2, y_share=y_shares[1]
        )

        await self.delete_session(sid)
        return result.get("result", result)

    # ------------------------------------------------------------------
    # Millionaire's Comparison
    # ------------------------------------------------------------------

    async def compare_values(self, a: int, b: int) -> dict:
        """Determine which of two private integers is larger without revealing them.

        Args:
            a: Party A's private integer.
            b: Party B's private integer.

        Returns:
            Dict with 'party_1_greater', 'party_2_greater', 'equal'.
        """
        sess = await self.create_session(operation="compare", n_parties=2)
        sid = sess["session_id"]

        p1 = await self.join_session(sid, "Party-A")
        p2 = await self.join_session(sid, "Party-B")

        m1 = await self.call(
            "submit_masked_value",
            session_id=sid,
            party_id=p1["party_id"],
            private_value=a,
        )
        await self.call(
            "submit_masked_value",
            session_id=sid,
            party_id=p2["party_id"],
            private_value=b,
        )

        result = await self.call(
            "resolve_comparison",
            session_id=sid,
            party1_r=m1["mask_r"],
            party2_value=b,
        )
        await self.delete_session(sid)
        return result

    # ------------------------------------------------------------------
    # Demo helpers (call server-side demo tools)
    # ------------------------------------------------------------------

    async def demo_shamir(self, secret: int, n: int = 5, k: int = 3) -> dict:
        return await self.call("demo_shamir", secret=secret, n=n, k=k)

    async def demo_secure_sum(self, values: list[int]) -> dict:
        return await self.call("demo_secure_sum", values=values)

    async def demo_beaver_multiply(self, x: int, y: int) -> dict:
        return await self.call("demo_beaver_multiply", x=x, y=y)

    async def demo_compare(self, a: int, b: int) -> dict:
        return await self.call("demo_compare", a=a, b=b)


# ---------------------------------------------------------------------------
# CLI demo
# ---------------------------------------------------------------------------

async def run_demo(url: str) -> None:
    print(f"\n{'='*60}")
    print("  MPC Client Demo")
    print(f"  Server: {url}")
    print(f"{'='*60}\n")

    async with MPCClient(url) as client:

        # ── Demo 1: Shamir Secret Sharing ────────────────────────────
        print("── Demo 1: Shamir's Secret Sharing ──")
        result = await client.demo_shamir(secret=42, n=5, k=3)
        print(f"  Secret   : {result['original_secret']}")
        print(f"  Shares   : {result['n']} shares, threshold {result['k']}")
        print(f"  Shares   : {[s['y'] for s in result['all_shares']]}")
        print(f"  Reconstr : {result['reconstructed_secret']}  ({'✓' if result['reconstruction_correct'] else '✗'})\n")

        # ── Demo 2: Secure Sum ────────────────────────────────────────
        print("── Demo 2: Secure Sum ──")
        values = [100, 250, 375, 50]
        result = await client.demo_secure_sum(values)
        print(f"  Private inputs : {values}")
        print(f"  Secure sum     : {result['secure_sum']}")
        print(f"  Plain sum      : {result['verification_plain_sum']}  ({'✓' if result['correct'] else '✗'})\n")

        # ── Demo 3: Beaver Multiplication ────────────────────────────
        print("── Demo 3: Beaver Triple Multiplication ──")
        result = await client.demo_beaver_multiply(x=17, y=13)
        print(f"  x         : {result['x']}")
        print(f"  y         : {result['y']}")
        print(f"  Product   : {result['product']}")
        print(f"  Verify    : {result['verification']}  ({'✓' if result['correct'] else '✗'})\n")

        # ── Demo 4: Comparison ───────────────────────────────────────
        print("── Demo 4: Millionaire's Comparison ──")
        result = await client.demo_compare(a=1000, b=750)
        print(f"  a > b  : {result['a_greater']}")
        print(f"  b > a  : {result['b_greater']}")
        print(f"  Equal  : {result['equal']}\n")

        # ── Full Session: Secure Sum ──────────────────────────────────
        print("── Full Session: Secure Sum (Alice=50, Bob=80, Carol=30) ──")
        result = await client.secure_sum(
            [50, 80, 30],
            party_names=["Alice", "Bob", "Carol"],
        )
        print(f"  Sum    : {result['sum']}  (expected: 160)  ({'✓' if result['sum'] == 160 else '✗'})\n")

        # ── Full Session: Secure Average ─────────────────────────────
        print("── Full Session: Secure Average (salaries) ──")
        salaries = [75000, 82000, 91000, 68000]
        result = await client.secure_average(
            salaries,
            party_names=["Alice", "Bob", "Carol", "Dave"],
        )
        expected_avg = sum(salaries) / len(salaries)
        print(f"  Average: {result['average']:.2f}  (expected: {expected_avg:.2f})  "
              f"({'✓' if abs(result['average'] - expected_avg) < 0.01 else '✗'})\n")

        # ── Full Session: Comparison ─────────────────────────────────
        print("── Full Session: Millionaire's Comparison (A=9900, B=8800) ──")
        result = await client.compare_values(9900, 8800)
        print(f"  party_1_greater: {result['party_1_greater']}  (expected: True)  "
              f"({'✓' if result['party_1_greater'] else '✗'})")
        print(f"  party_2_greater: {result['party_2_greater']}")
        print(f"  equal          : {result['equal']}\n")

        # ── Full Session: Shamir Workflow ────────────────────────────
        print("── Full Session: Shamir (secret=1337, n=5, k=3) ──")
        result = await client.shamir_workflow(secret=1337, n=5, k=3)
        print(f"  Reconstructed  : {result['secret']}  (expected: 1337)  "
              f"({'✓' if result['secret'] == 1337 else '✗'})\n")

    print("All demos complete.")


def main() -> None:
    url = os.environ.get("MPC_SERVER_URL", "http://localhost:8000/mcp")
    if len(sys.argv) > 1:
        url = sys.argv[1]
    asyncio.run(run_demo(url))


if __name__ == "__main__":
    main()
