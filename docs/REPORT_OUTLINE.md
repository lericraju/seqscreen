# Technical report outline (5–8 pages)

Fill every number from a benchmark run on your own machine; do not reuse figures from another
environment.

1. **Problem, users, constraints, success criteria.** Lab technician or bioinformatics student with
   a FASTA collection; needs the sequences containing a motif and the exact positions. Constraints:
   in-memory dataset, no database, one user at a time. Success criteria: zero false negatives
   (every screened mode returns the same hits as KMP-only), false positives within the configured
   target, and a measured speed-up on rare motifs.

2. **System design.** Three layers — the plain-JS page, the Spring Boot REST API, and the algorithm
   engine with no framework dependencies. Endpoint list and the request/response shapes are in the
   README. One diagram: dataset → index/bloom build → screen → confirm → merge.

3. **Algorithms, with pseudocode.**
   - k-mer encoding: 2 bits per base, `A=00 C=01 G=10 T=11`, packed into a `long`.
   - inverted index: open addressing (`long` key → slot), postings as an intrusive linked list in
     two flat `int` arrays, growth at load factor 0.65.
   - KMP: prefix function, single pass, all overlapping matches.
   - Bloom filter: bit array, `m = −n ln p / (ln 2)²`, `k = (m/n) ln 2`, double hashing with a
     splitmix64 mix.
   - Myers bit-vector: one `long` per DP row, `O(L)` per sequence for up to 5 edits; explain why
     the shift must **not** seed a `1` in the low bit for substring matching (it advertises a free
     start at every column and the score drifts upward instead of returning to zero).
   - reverse complement and the coordinate convention (report once, in forward coordinates).
   - threading: split candidates into `2 × workers` chunks, merge in dataset order.

4. **Correctness.**
   - KMP invariant: after processing `text[i]`, `j` is the length of the longest motif prefix that
     is a suffix of `text[0..i]`.
   - Bloom: no false negatives, because every inserted k-mer sets all its bits, so any present
     key must pass. A motif occurrence implies all its k-mers are in the sequence, so a sequence
     that fails any k-mer cannot contain the motif.
   - Index: a sequence containing the motif contains each of its k-mers, so probing any one of
     them keeps the sequence. Both strands are probed independently and unioned, never intersected.
   - Myers: checked against a dynamic-programming baseline over 500 random cases.

5. **Complexity.** Index build `O(total bases)`, expected `O(1)` lookup; KMP `O(L+m)` time and
   `O(m)` space; Bloom build `O(L)` with `L log(1/p)` bits; screen `O(m−k+1)` per sequence with
   early exit; Myers `O(L)` per sequence independent of the edit budget.

6. **Benchmark methodology.** Same generator seed per row, single thread, forward strand only,
   index build time reported separately, one warm-up run discarded, an exactness check on every
   row (all four strategies must report identical hit lists). Run three cases: rare motif
   (`plantedFraction 0.01`), common motif (`0.5`), and a repetitive motif (`AAAAAAC` style).

7. **Results.** Tables and charts from the Benchmark tab, filled with your own measured numbers.
   Include index build time and index memory next to query time; a speed-up that ignores its
   preprocessing cost is not a fair comparison.

8. **Limitations.**
   - The index is skipped above `k=16` or 16M k-mer occurrences; Bloom filters then take over.
   - Approximate and wildcard search cannot be screened at all (an edited match changes every
     k-mer), so those queries scan the full dataset.
   - Random DNA gives the naive matcher near-linear behaviour, so KMP shows little advantage on it;
     the repetitive case is where the difference appears.
   - Index memory grows with distinct k-mers — at `k=8` on 10 Mbp that is roughly 79 MB.
   - One dataset per process, held in memory; a restart clears it.

9. **Individual contributions.** Keep consistent with the commit history in CONTRIBUTIONS.md.

10. **Setup and reproduction.** Prerequisites, `mvn test`, `mvn spring-boot:run`, sample data,
    screenshot of a search and of the benchmark.
