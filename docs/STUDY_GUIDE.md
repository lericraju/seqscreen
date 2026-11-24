# SeqScreen — study guide and viva preparation

Written so that any member can explain the project end to end without reading the whole codebase.
Read sections 1–3 once, then rehearse section 7 out loud with a partner.

---

## 1. What the project is, in one paragraph

SeqScreen answers one question: **which sequences in a collection contain a given short DNA motif,
and where?** Scanning everything works but wastes effort, because in a real screening run almost no
sequence contains the motif. So the app builds a filter that can *prove absence cheaply*, uses it to
skip most of the data, and then confirms the survivors with an exact matcher.

The one-sentence version for the viva:

> "It is a DNA motif search that rejects most sequences with an index or a Bloom filter, then
> confirms the rest with KMP, so the answer is always exact even though the filter is not."

---

## 2. The pipeline in plain words

```
upload or generate sequences
        │
        ▼
   build (once)                         per query
   ├─ inverted k-mer index              ├─ look up 3 rare k-mers → candidates
   └─ one Bloom filter per sequence     └─ or ask each sequence's filter → candidates
        │                                         │
        └───────────────┬─────────────────────────┘
                        ▼
              split candidates across workers
                        ▼
                 KMP confirms each one
                        ▼
        merge in dataset order → positions + strand
```

Two rules make the whole thing safe:

1. **A filter can only prove absence.** Failing a k-mer means the motif cannot be there. Passing
   means nothing yet.
2. **KMP is the authority.** Every survivor is confirmed exactly, so filters change *how much work*
   is done, never *what the answer is*.

---

## 3. The techniques, one page each

### 3.1 KMP — the required matcher (Unit 4)

**Idea.** When a mismatch happens after matching `j` characters, part of what you matched might
still be a valid prefix of the motif. KMP remembers that overlap in a prefix table and resumes from
there instead of restarting at the next character. The text is read once.

**Where.** `algo/KmpMatcher.java` — `prefix()` builds the table, `positions()` scans.
**Cost.** `O(L + m)` time, `O(m)` space.

**Viva answer:** "The prefix table stores, for each position, the length of the longest proper
prefix of the motif that is also a suffix of the motif up to that point. On a mismatch we set
`matched = table[matched-1]` and keep the text pointer where it is, so we never re-read a base."

**Common mistake:** thinking KMP is always faster than naive. It is not — on random DNA a 14-base
motif rarely partially matches, so naive is already near-linear. KMP's value is a guaranteed bound
and no backtracking. Say this; it is in the report.

### 3.2 Bloom filter — the required CO5 technique (Unit 6)

**Idea.** A bit array and `k` hash functions. Adding a key sets `k` bits. Asking sets nothing and
only fails if some bit is clear. So: *no false negatives ever*, but possible false positives, at a
rate you can size for.

**Where.** `algo/BloomFilter.java` (`optimalBits`, `optimalHashes`, `add`, `mightContain`),
`algo/KmerEncoder.java` (turns a k-mer into a `long`), used from
`index/SequenceIndex.java#bloomCandidates`.

**Sizing.** `m = −n·ln p / (ln 2)²` bits, `k = (m/n)·ln 2` hashes, where `n` is the number of
k-mers in that sequence and `p` the target false-positive rate. Hashing is splitmix64 twice, then
double hashing `h1 + i·h2` — one mix per key, `k` independent-looking indices.

**Cost.** Build `O(L)` per sequence, `O(L·log(1/p))` bits; lookup `O(k)` with early exit.

**Viva answer:** "A pass means *possibly present*, never *present*. We only use it to avoid reading
a sequence, and every pass is confirmed by KMP, so its false positives cost time and nothing else.
The interface reports passes, confirmations and the measured false-positive rate separately."

**Why the rate is low here:** the filter is asked whether *every* k-mer of the motif might be
present. For a 14-base motif that is 7 k-mers, all of which must be false positives at once. That
is why the measured rate is ~0 for long motifs and jumps to ~45% for an 8-base motif (one k-mer).
This is a good, honest observation to volunteer.

### 3.3 Inverted k-mer index — an extra, beyond the required two (Unit 4)

**Idea.** Keep a dictionary: k-mer → list of sequences that contain it. For a query, look up the
motif's rarest k-mers and keep the union of their lists.

**Why union and not intersection:** each k-mer of the motif is present in every sequence containing
the motif, so *one* is already sufficient evidence. Intersecting would wrongly drop real hits.

**Where.** `index/KmerIndex.java` (open-addressed `long` table, postings as a linked list in flat
int arrays), `index/SequenceIndex.java#probe`.

**Cost.** Build `O(total bases)`; query expected `O(1)` per probe, 3 probes per strand.

**Limits worth quoting:** `k ≤ 16` and 16 M k-mer occurrences; above that the index skips itself
and Bloom filters take over.

### 3.4 Myers bit-vector — an extra for approximate matching (Unit 5)

**Idea.** The edit-distance matrix normally needs `O(L·m)` cells. Myers stores a whole row in one
64-bit word and updates it with a handful of bit operations, so a column costs the same no matter
how many edits you allow.

**Where.** `algo/MotifPattern.java`.
**Cost.** `O(L)` per sequence for up to 5 edits; motif limited to 64 bases.

**The trap we hit:** the shift must **not** be seeded — `Ph = Ph << 1`, not `(Ph << 1) | 1`. Seeding
a 1 advertises "a fresh alignment can start here" at every column, which is right for aligning two
whole strings but wrong for substring search: the score drifts upward and only the first occurrence
is ever reported. Removing it made `ACGT` appear twice in `ACGTACGT`, and the randomised
comparison against a dynamic-programming baseline then passed.

### 3.5 Supporting techniques

- **Reverse complement** (`algo/RevComp.java`): A↔T, C↔G. The motif's reverse complement is searched
  as a second pattern so a site on the other strand is still found. Hits are reported in forward
  coordinates, flagged, and de-duplicated if the motif is palindromic.
- **Threads** (`service/SearchService.java`, `service/Threads.java`): candidates split into
  `2 × workers` contiguous chunks, merged in dataset order — so results never depend on scheduling.

---

## 4. Code map

| Path | Responsibility |
|---|---|
| `algo/KmpMatcher` | prefix table and exact matching |
| `algo/NaiveMatcher` | baseline, `O(L·m)` |
| `algo/BloomFilter` | sizing, add, mightContain, mightContainAll |
| `algo/KmerEncoder` | 2 bits per base into a `long` |
| `algo/MotifPattern` | Myers approximate matching, wildcards |
| `algo/RevComp` | reverse complement |
| `index/KmerIndex` | inverted index: table, postings, growth |
| `index/SequenceIndex` | dataset + both filters; decides candidates |
| `service/SearchService` | screening, threading, confirmation, merging |
| `service/BenchmarkService` | four-strategy comparison |
| `service/DatasetService` | synthetic generator, FASTA upload, current dataset |
| `validation/*` | FASTA and motif rules, `ApiException` |
| `controller/*` | REST endpoints, error mapping |
| `static/index.html` | the entire frontend |

**Where to change what:** default `k`/`p` → `DatasetService` constants. How many k-mers a query
probes → `SequenceIndex.PROBES`. Index caps → `SequenceIndex.MAX_INDEXED_OCCURRENCES` and
`KmerEncoder.MAX_K`. Worker cap → `Threads.MAX`. Match display cap → `SearchService.MATCH_LIMIT`.

---

## 5. Running and developing it

```
mvn test                       # 62 tests, must be 0 failures
mvn spring-boot:run            # http://localhost:8080
MAVEN_OPTS="-Xmx4g" mvn spring-boot:run     # for the 100k benchmark row
```

The dataset lives in memory: restarting clears it. `GET /api/dataset` shows what is loaded; the
page reloads a synthetic dataset by itself when nothing is.

Debugging tips:
- A search that suddenly returns nothing → check `stats.screeningNote`; the motif may be shorter
  than `k`, or the mode may be screening when the motif has a wildcard.
- Benchmark row marked invalid → the four strategies disagreed; that is a bug, not noise.
- `mvn test` slow on first run → Maven is downloading dependencies.

---

## 6. Reading the interface and the numbers

Under the result you will see either "no screening" or a panel:

| Field | Meaning |
|---|---|
| Sequences rejected | how many were never read |
| Bloom passes | sequences that passed the filter — *possibly present* |
| Candidates | survivors the index kept (same idea, exact k-mer evidence) |
| Confirmed by KMP | sequences that really contain the motif |
| False positives | passes that confirmed nothing |
| Measured FP rate | false positives ÷ passes |

The rule to remember: **confirmed ≤ passes ≤ total**. If confirmed ever exceeded passes, the code
would be wrong.

---

## 7. Viva question bank

**Warm-up**

1. *What problem does this solve?* → Answer in section 1.
2. *Who is it for?* → A lab technician or bioinformatics student screening a FASTA collection.
3. *Why is the algorithm central?* → Every screen and every position comes from a hand-written
   implementation; a library only serves HTTP and JSON.

**KMP**

4. *Why is KMP linear?* → The prefix table lets a mismatch resume mid-pattern; the text pointer
   never moves backwards, so each base is read once (`O(L + m)`).
5. *What does the prefix table hold?* → For each prefix, the longest proper prefix that is also a
   suffix.
6. *How do you get overlapping matches?* → After a hit, set `matched = table[matched-1]` instead of
   resetting to zero; `AAAA` with `AA` gives three hits.
7. *Is KMP faster than naive here?* → Only on repetitive input. On random DNA they are close; say
   it plainly — it is in the report.

**Bloom filter / CO5**

8. *Why no false negatives?* → Every inserted key had all its bits set, so it always passes.
9. *Why size the filter, rather than pick a bit count?* → The formula ties `n` and `p` to `m` and
   `k`; with less memory than that, the false-positive rate rises predictably.
10. *What happens on a false positive?* → KMP reads the sequence and finds nothing; it costs time
    only.
11. *Why is your measured rate sometimes 0% and sometimes 45%?* → The filter must pass *all* the
    motif's k-mers. A 14-base motif is 7 k-mers (≈`p⁷`), an 8-base motif is one k-mer (≈`p`).
12. *Where is the randomness in your project?* → The Bloom filter is the randomised structure:
    hash functions make the membership answer probabilistic. Sizing is deterministic, the outcome
    is not.

**Index**

13. *Why union the postings?* → One rare k-mer is already proof the motif could be there; the
    k-mers are not independent requirements.
14. *Why the rarest k-mers?* → The shortest posting list rejects the most sequences for the same
    work.
15. *What happens if the index does not fit?* → It reports itself skipped; Bloom screening takes
    over and the benchmark marks the row.

**Approximate matching**

16. *How do you allow edits in linear time?* → Myers' bit-vector: one word per DP row.
17. *Why is approximate search not screened?* → Every k-mer overlapping an edited position changes,
    so no exact filter can rule a sequence out.
18. *How did you verify it?* → Randomised comparison against a dynamic-programming baseline at every
    column; the divergence we found was the `| 1` seeding bug.

**Engineering**

19. *Why do threads not change the answer?* → Contiguous chunks, merged in dataset order.
20. *How do you know screening never drops a hit?* → Both the test suite and the benchmark assert
    that all four strategies return identical hit lists.
21. *How do you validate input?* → `FastaParser` and `MotifValidator` reject unknown bases, duplicate
    ids, empty records, data before a header, over-long motifs, out-of-range edits and workers.

**Harder / chapter-style**

22. *Complexity of everything you built?* → Section 3 and the report's complexity table.
23. *What are your limitations?* → Section 8 of the report: screening helps only when the motif is
    rare; approximate queries are unscreened; the index has caps; one dataset per process.
24. *If we doubled the dataset, what changes?* → Index memory and build time double; query time
    barely moves because screening is sub-linear in dataset size.

---

## 8. Scenarios the examiner may try

| They do this | What should happen | Why |
|---|---|---|
| Change the motif to a random one | 0 matches, with a hint to allow an edit | screening rejects everything, KMP confirms nothing |
| Search a 3-base motif | note: "shorter than k", every sequence searched | a 3-base motif has no 8-mer to look up |
| Set k to 20 | index skipped, Bloom only | `KmerIndex` caps at 16 bases for packed keys |
| Upload the invalid FASTA | 400 with the line and offending character | parser rejects unknown bases |
| Turn on 2 edits | note: not screened, all sequences read | an edit changes every overlapping k-mer |
| Switch strands to forward only | reverse hits disappear | only the forward pattern is searched |
| Run the benchmark | four columns + opt-in exactness check | every row recomputed and compared |

---

## 9. Who answers what

| Member | Owns | Should be able to walk through |
|---|---|---|
| Leric Raju | KMP, naive, build, report | `KmpMatcher.prefix` and `positions`, the benchmark method |
| Mohammed Yusuf | Bloom filter, k-mer encoding, validation | `BloomFilter.optimalBits`, `mightContain`, `FastaParser.parse` |
| Tabish Matwankar | index, Myers, frontend | `KmerIndex.add`/`slotOf`, `MotifPattern.positions`, the mode buttons |

Everyone should also be able to answer questions 1–3, 20 and 23 from section 7.

---

## 10. Glossary

- **k-mer** — a fragment of `k` consecutive bases; the unit the index and filters work on.
- **candidate** — a sequence a filter could not reject; it must still be confirmed.
- **false positive** — a candidate that turns out not to contain the motif.
- **strand** — DNA has two; a motif may appear as itself (forward) or as its reverse complement.
- **reverse complement** — reverse the sequence, then swap A↔T and C↔G.
- **edit distance** — the fewest substitutions, insertions or deletions turning one string into
  another.
- **wildcard `N`** — a base that matches anything; it also disables exact screening.
- **amortized** — spread over many operations: the index occasionally doubles and copies, but the
  average insert stays constant time.
