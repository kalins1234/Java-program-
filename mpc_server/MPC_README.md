# Real-Time Remote MPC Server via FastMCP

A production-ready **Multi-Party Computation (MPC)** coordinator built on
**FastMCP 3.x** with streamable-HTTP transport. Multiple remote parties can
jointly compute over their private inputs without any party ever revealing
their raw value.

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                  mpc_server.py                        │
│   FastMCP (streamable-HTTP)  http://host:8000/mcp    │
│                                                       │
│  ┌─────────────┐  ┌────────────┐  ┌───────────────┐  │
│  │  Sessions   │  │  Shamir    │  │ Beaver Triple │  │
│  │  Manager   │  │  Protocol  │  │  Multiply     │  │
│  └─────────────┘  └────────────┘  └───────────────┘  │
│  ┌─────────────┐  ┌────────────┐                      │
│  │  Additive   │  │Millionaire │                      │
│  │  Sharing   │  │ Comparison │                      │
│  └─────────────┘  └────────────┘                      │
└──────────────────────────────────────────────────────┘
          ▲                     ▲
          │ MCP / HTTP          │ MCP / HTTP
   ┌──────┴──────┐       ┌──────┴──────┐
   │  Party A    │       │  Party B    │
   │ mpc_client  │       │ mpc_client  │
   └─────────────┘       └─────────────┘
```

### Files

| File | Role |
|---|---|
| `mpc_protocol.py` | Cryptographic primitives: Shamir, Additive Sharing, Beaver Triples, Comparison |
| `mpc_server.py` | FastMCP server — session management + all MPC tools |
| `mpc_client.py` | Async Python client + high-level workflows |
| `server.py` | Original Java Algorithms MCP server (unchanged) |

---

## MPC Protocols Implemented

### 1. Shamir's Secret Sharing `(k, n)`
- Polynomial of degree `k-1` over `GF(2^127 - 1)`
- Any `k` shares reconstruct the secret; `k-1` or fewer reveal **nothing**
- Tools: `shamir_split_secret`, `get_my_shamir_shares`, `shamir_reconstruct_secret`

### 2. Additive Secret Sharing `(n-of-n)`
- `s = s₁ + s₂ + … + sₙ mod p`
- All shares required; used internally by Secure Sum

### 3. Secure Sum / Average
- Each party submits a private integer
- Server accumulates field elements; reconstructs the aggregate only
- No individual value is ever revealed
- Tools: `submit_value_for_sum`, `demo_secure_sum`

### 4. Beaver Triple Multiplication
- Pre-generated random triple `(a, b, c=a·b)` split into additive shares
- Allows computing `x · y` without revealing `x` or `y`
- Tools: `get_beaver_triple_shares`, `submit_x_share`, `submit_y_share`

### 5. Millionaire's Comparison
- Two parties determine who holds the larger value, without revealing values
- Based on additive masking / blinding
- Tools: `submit_masked_value`, `resolve_comparison`

**Prime field:** `p = 2^127 - 1` (Mersenne prime, 127-bit security)

---

## Quick Start

### 1 — Install dependencies

```bash
cd mpc_server
pip install -r requirements.txt
```

### 2 — Start the server

```bash
python mpc_server.py
# http://0.0.0.0:8000/mcp

# Custom host/port:
HOST=0.0.0.0 PORT=9000 python mpc_server.py
```

### 3 — Run the client demo

```bash
python mpc_client.py
# or point to a remote server:
python mpc_client.py http://my-server:8000/mcp
```

---

## Session Lifecycle

Every computation follows the same pattern:

```
create_session  →  join_session (×n)  →  submit values  →  session_status  →  delete_session
```

### Example: Secure Sum (three parties)

```python
from mpc_client import MPCClient
import asyncio

async def main():
    async with MPCClient("http://localhost:8000/mcp") as c:
        # High-level helper — handles the full workflow automatically
        result = await c.secure_sum(
            private_values=[50_000, 75_000, 90_000],
            party_names=["Alice", "Bob", "Carol"],
        )
        print(result)
        # {'sum': 215000, 'n_parties': 3}

asyncio.run(main())
```

### Example: Shamir Split & Reconstruct

```python
async with MPCClient() as c:
    result = await c.shamir_workflow(secret=999, n=5, k=3)
    print(result["secret"])   # 999
```

### Example: Beaver Multiplication

```python
async with MPCClient() as c:
    result = await c.beaver_multiply_workflow(x=17, y=13)
    print(result["product"])   # 221
```

### Example: Millionaire's Comparison

```python
async with MPCClient() as c:
    result = await c.compare_values(a=9900, b=8800)
    print(result["party_1_greater"])   # True
```

---

## MCP Tools Reference

### Session Management

| Tool | Parameters | Description |
|---|---|---|
| `create_session` | `operation, n_parties, threshold?` | Create a new session |
| `join_session` | `session_id, party_name` | Join as a party |
| `session_status` | `session_id` | Poll status and result |
| `list_sessions` | — | List all active sessions |
| `delete_session` | `session_id` | Clean up a session |

### Secure Sum / Average

| Tool | Parameters | Description |
|---|---|---|
| `submit_value_for_sum` | `session_id, party_id, private_value` | Submit secret integer |

### Shamir

| Tool | Parameters | Description |
|---|---|---|
| `shamir_split_secret` | `session_id, party_id, secret` | Dealer splits their secret |
| `get_my_shamir_shares` | `session_id, party_id` | Retrieve your shares |
| `shamir_reconstruct_secret` | `session_id, shares` | Reconstruct from ≥k shares |

### Beaver Multiplication

| Tool | Parameters | Description |
|---|---|---|
| `get_beaver_triple_shares` | `session_id, party_id` | Get `(aᵢ, bᵢ, cᵢ)` |
| `submit_x_share` | `session_id, party_id, x_share` | Submit share of x |
| `submit_y_share` | `session_id, party_id, y_share` | Submit share of y (triggers compute) |

### Millionaire's Comparison

| Tool | Parameters | Description |
|---|---|---|
| `submit_masked_value` | `session_id, party_id, private_value` | Mask and register value |
| `resolve_comparison` | `session_id, party1_r, party2_value` | Evaluate comparison |

### Demo Tools (no session required)

| Tool | Description |
|---|---|
| `demo_shamir` | One-call Shamir split + reconstruct |
| `demo_secure_sum` | One-call secure sum |
| `demo_beaver_multiply` | One-call Beaver multiplication |
| `demo_compare` | One-call Millionaire's comparison |

---

## Connect from Claude Desktop

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "mpc": {
      "url": "http://localhost:8000/mcp",
      "transport": "http"
    }
  }
}
```

---

## Security Notes

- All arithmetic is in `GF(2^127 - 1)` (127-bit Mersenne prime)
- Shamir shares use `secrets.randbelow` for cryptographically secure randomness
- Beaver triples are generated fresh per session
- The comparison protocol uses random additive masks
- **This is an educational/research implementation** — a production deployment
  would add TLS, authentication, and a distributed coordinator (not single-server)
