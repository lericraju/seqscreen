# Team and contributions

**SeqScreen — CIA 3, Advanced Algorithm, Semester V**

| Member | ID |
|---|---|
| Leric Raju | 2409308 |
| Mohammed Yusuf | 2410309 |
| Tabish Matwankar | 2410672 |

## Work split

This table is the module ownership used for the viva. **Edit it to match what actually
happened**, then make sure the commit history tells the same story — the rubric grades
contribution attribution, and an examiner may compare the two.

| Area | Files | Owner |
|---|---|---|
| Exact matching: KMP, naive baseline | `algo/KmpMatcher`, `algo/NaiveMatcher` | *to confirm* |
| Probabilistic screening: Bloom filter, k-mer encoding | `algo/BloomFilter`, `algo/KmerEncoder` | *to confirm* |
| Inverted k-mer index | `index/KmerIndex`, `index/SequenceIndex` | *to confirm* |
| Approximate matching and both strands | `algo/MotifPattern`, `algo/RevComp` | *to confirm* |
| Services, threading, benchmark | `service/*` | *to confirm* |
| REST API, validation, frontend | `controller/*`, `validation/*`, `static/index.html` | *to confirm* |

## Per-member summary

**Leric Raju (2409308)** — *role to confirm*: problem framing, *modules*, demo and report.

**Mohammed Yusuf (2410309)** — *role to confirm*: *modules*, benchmark runs.

**Tabish Matwankar (2410672)** — *role to confirm*: *modules*, tests and validation.

## Viva questions each member should be able to answer

- **KMP**: what the prefix table holds, why the text pointer never moves backwards, why the
  running time is O(n + m) and not O(n·m).
- **Bloom filter**: why there are no false negatives, how `m` and `k` are chosen from `n` and `p`,
  what double hashing does, and why a hit must still be confirmed.
- **Index**: why probing the *rarest* k-mers is better than probing the first ones, why the
  posting lists are unioned and never intersected, and what happens when the index does not fit.
- **Myers**: why one machine word per DP row works, and why the low bit of the shift must not be
  seeded for substring matching.
- **Threads**: why results are merged in dataset order, and why the answer must not depend on the
  number of workers.
