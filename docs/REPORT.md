# SeqScreen — Technical report

**Course:** Advanced Algorithm (CIA 3) · Semester V · B.Tech CS (AI & ML)
**Team:** Leric Raju (2409308) · Mohammed Yusuf (2410309) · Tabish Matwankar (2410672)
**Repository:** `seqscreen` — Java 17, Spring Boot, one plain-JS page.

---

## 1. Problem statement and users

A laboratory holds a collection of DNA sequences — isolates, reads, reference entries — and
repeatedly needs to know which of them contain a short motif: a primer binding site, a restriction
site, a marker sequence. Doing this by scanning every sequence in full, for every query, spends
work proportional to the whole collection to answer a question that a small number of positions
could have settled, because in a typical screening run almost none of the sequences contain the
motif.

**Target user.** A lab technician or bioinformatics student who loads a FASTA file, types a
motif, and needs the matching sequences with exact positions. Three requirements follow from
that: no missed hits, positions that can be trusted, and an answer fast enough to try several
motifs in a row.

**Success criteria.**

| Criterion | How it is checked |
|---|---|
| No false negatives | All four strategies must return identical hit lists; the benchmark fails a row that disagrees, and the test suite asserts the same property |
| False positives within the configured target | Bloom passes are counted and the observed rate is reported next to the configured one |
| A measured speed-up on rare motifs | Index and Bloom timings are compared against KMP on the same data |
| Honest limits | Common and repetitive motifs are reported in the same table, not hidden |

---

## 2. System design

Three layers, one process:

| Layer | Responsibility | Technology |
|---|---|---|
| Frontend | motif input, mode selection, results, benchmark charts | plain JavaScript, no framework |
| Java API | validation, orchestration, JSON | Spring Boot REST |
| Algorithm engine | matching, indexing, filters, benchmarks | plain Java, no framework dependencies |

The frontend never performs matching. It sends a motif and receives positions.

**Endpoints**

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/dataset` | current dataset summary |
| `POST` | `/api/dataset/generate` | seeded synthetic dataset |
| `POST` | `/api/dataset/upload` | FASTA upload |
| `POST` | `/api/search` | motif search |
| `POST` | `/api/benchmark` | four-strategy comparison |

**Data flow for one query**

```
motif ──► validate ──► screen (index | Bloom) ──► candidates
                                                     │
                                        split across workers
                                                     │
                                     KMP confirms each candidate
                                                     │
                                    merge in dataset order ──► JSON
```

---

## 3. Algorithms

### 3.1 KMP — required technique, Unit 4

KMP finds a motif of length `m` in a sequence of length `L` in `O(L + m)` time. The prefix table
records, for each prefix of the motif, the length of the longest proper prefix that is also a
suffix; on a mismatch we resume from that length instead of restarting at the next text position,
so the text pointer never moves backwards.

```
PREFIX(motif):
    len = 0; table[0] = 0
    for i = 1 .. m-1:
        while len > 0 and motif[i] != motif[len]: len = table[len-1]
        if motif[i] == motif[len]: len = len + 1
        table[i] = len

SEARCH(text, motif):
    table = PREFIX(motif); matched = 0
    for i = 0 .. L-1:
        while matched > 0 and text[i] != motif[matched]:
            matched = table[matched-1]
        if text[i] == motif[matched]: matched = matched + 1
        if matched == m:
            report(i - m + 1)
            matched = table[matched-1]      // keep overlapping matches
```

Its role in SeqScreen is not speed but **authority**: every candidate that any filter passes is
confirmed here, so a filter can only ever narrow the search.

### 3.2 Bloom filter — required technique, Unit 6 (CO5)

A Bloom filter is a bit array plus `k` hash functions. Adding a key sets one bit per hash; a
lookup answers "possibly contained" unless some bit is clear. Two properties matter:

- **No false negatives.** Every inserted key has all its bits set, so it can never be rejected.
- **False positives are possible**, at a rate that can be bounded by sizing the filter.

```
BUILD(sequence, p):
    n = number of k-mers in the sequence
    bits = ceil(-n * ln p / (ln 2)^2)
    hashes = round((bits / n) * ln 2)
    for each k-mer code of the sequence:
        add(code)

ADD(code):
    h1 = mix(code); h2 = mix(code XOR seed) or 1
    for i = 0 .. hashes-1:
        bit[(h1 + i*h2) mod bits] = 1

MIGHT_CONTAIN(code):
    h1 = mix(code); h2 = mix(code XOR seed) or 1
    for i = 0 .. hashes-1:
        if bit[(h1 + i*h2) mod bits] == 0: return false
    return true
```

Hashing uses splitmix64 with two seeds and double hashing, so one mix per key yields all `k`
indices. The filter is **per sequence**: a query asks each sequence's filter whether every k-mer
of the motif might be present, and only such sequences are read.

**This is where CO5 is met.** Positive outcomes are treated as *possibly present* and are always
confirmed by KMP; the interface reports passes, confirmations and false positives as separate
numbers, and the observed rate is shown next to the configured target.

### 3.3 Inverted k-mer index — extra technique, Unit 4

Beyond the two required techniques, SeqScreen also builds an inverted index: every distinct k-mer
is mapped to the sequences containing it. A query takes the motif's k-mers, sorts them by posting
length, and probes the three rarest. Because a sequence containing the motif necessarily contains
all of its k-mers, any one of them is enough evidence to keep the sequence — so the postings are
**unioned, never intersected**.

```
BUILD(sequences):
    for each sequence s, for each k-mer code c:
        if head[c] is from s: skip          // duplicate within one sequence
        link s into the posting list of c

QUERY(motif):
    codes = k-mers of motif, sorted by posting length ascending
    keep = empty set
    for the first three codes:
        keep = keep UNION postings(code)
    return keep
```

Keys live in an open-addressed `long` table that doubles at load factor 0.65; postings are an
intrusive linked list stored in two flat `int` arrays (8 bytes per posting, no object per entry).

### 3.4 Myers bit-vector — extra technique, Unit 5

Approximate matching allows substitutions, insertions and deletions. Myers' algorithm encodes one
row of the edit-distance matrix in a machine word, so a column costs a handful of word operations
regardless of the number of allowed edits: `O(L)` per sequence for up to 5 edits.

```
PEQ[base] = bitmask of motif positions holding that base
Pv = all ones; Mv = 0; score = m
for each text column j:
    Eq = PEQ[text[j]]
    Xv = Eq OR Mv
    Xh = (((Eq AND Pv) + Pv) XOR Pv) OR Eq
    Ph = Mv OR NOT (Xh OR Pv)
    Mh = Pv AND Xh
    if Ph has the top bit: score++ else if Mh has the top bit: score--
    Ph = Ph << 1            // NOT seeded with 1
    Mh = Mh << 1
    Pv = Mh OR NOT (Xv OR Ph)
    Mv = Ph AND Xv
    if j >= m-1 and score <= maxEdits: report(j - m + 1)
```

**One implementation note that cost real debugging time.** Many published listings write the shift
as `Ph = (Ph << 1) | 1`. That seeds a free start at every column, which is correct for global
alignment but wrong for substring search: the score drifts upward after a match instead of
returning to zero, so only the *first* occurrence of a motif is ever reported. `ACGT` in
`ACGTACGT` returned one hit instead of two. Removing the `| 1` fixed it; the randomised
comparison against a dynamic-programming baseline then passed.

### 3.5 Reverse complement and threads — supporting techniques

- **Reverse complement.** DNA is double stranded, so a motif can be present on either strand. The
  motif's reverse complement is searched as a second pattern; hits are reported once, in forward
  coordinates, and flagged when they exist only on the reverse strand. A palindromic motif would
  otherwise be reported twice at the same coordinate, so those duplicates are dropped.
- **Threads.** The candidate list is split into `2 × workers` contiguous chunks and merged in
  dataset order, so the result never depends on scheduling.

---

## 4. Correctness

**KMP invariant.** After processing `text[i]`, `matched` equals the length of the longest prefix
of the motif that is a suffix of `text[0..i]`. Each step either extends that prefix or falls back
to the longest shorter prefix that can still extend, so no occurrence starting before `i` is
skipped; when `matched == m` the occurrence ends at `i`, and the fallback keeps overlapping
occurrences from being lost.

**Bloom filter has no false negatives.** Adding a key sets every bit that `mightContain` later
tests, so a key that was inserted always passes. The only error mode is a false positive, when a
key that was never inserted happens to have all its bits set by other keys.

**Screening cannot remove a real hit.** If a sequence contains the motif, it contains each of the
motif's `m − k + 1` k-mers at that position. Therefore:
- its Bloom filter passes every one of those k-mers, so the sequence is not rejected; and
- its posting lists contain the sequence, so the index keeps it.
Both arguments are one-directional: failing a k-mer proves absence, passing proves nothing. Every
survivor is confirmed with KMP, which is why the filters' false positives affect only how much
work is done, never the answer.

**Approximate search is never screened.** An edited match changes every k-mer it spans, so no
exact filter can bound it. The app states this in `stats.screeningNote` and scans every sequence
in that mode rather than pretending to screen.

**Test evidence.** `mvn test` runs **62 tests, 0 failures**:

| Suite | Tests | Covers |
|---|---|---|
| KMP | 9 | overlapping hits, boundaries, prefix table, 300 random cases vs naive |
| Bloom filter | 8 | no false negatives, measured rate under target, zero key, sizing |
| Inverted index | 7 | postings, duplicate collapse, growth past capacity, zero key |
| FASTA parsing | 9 | valid files, `N`, duplicate ids, empty records, unknown base |
| Approximate + strands | 9 | substitutions, indels, wildcards, 500 random cases vs DP |
| Search service | 20 | all modes agree, Bloom decides the scan, reverse strand, threads, limits |

The randomised comparisons are the strongest evidence: KMP is checked against the naive matcher on
random text, and Myers against a dynamic-programming baseline at every column.

---

## 5. Complexity

| Structure or operation | Time | Space |
|---|---|---|
| Prefix table | `O(m)` | `O(m)` |
| KMP over one sequence | `O(L + m)` | `O(m)` |
| Naive search over one sequence | `O(L·m)` worst case | `O(1)` |
| Bloom build, per sequence | `O(L)` | `L·log(1/p)` bits |
| Bloom screen, per sequence | `O(m − k + 1)` with early exit | `O(1)` |
| Index build, whole dataset | `O(total bases)` | `O(distinct k-mers + occurrences)` |
| Index query | expected `O(1)` per probe, 3 probes per strand | `O(candidates)` |
| Myers over one sequence | `O(L)` for up to 5 edits | `O(m/64)` words |

Screening is worth its cost only when most sequences lack the motif: the index pays a build pass
once and then answers in microseconds, while the Bloom filters are cheap to build but must be
probed once per sequence.

---

## 6. Benchmark methodology

- Every row builds its dataset from the **same generator seed**, so only size varies.
- **Single thread, forward strand only**, so the rows measure the algorithms rather than the
  scheduler; threading is measured separately.
- One warm-up query is run and discarded before the first row.
- **Index build time is reported in its own column** and excluded from the search measurements —
  a speed-up that ignores preprocessing is not a fair comparison.
- Every row recomputes the hit list with all four strategies and marks the row invalid if they
  differ.

---

## 7. Results

All numbers from one machine (development laptop, warm JVM, single run per cell).

### 7.1 Increasing size, rare motif

Motif `GATTACAGATTACA` (14 bp), `k = 8`, `p = 0.01`, 1% of sequences carry the motif.

| Sequences | Bases | Naive | KMP | Bloom + KMP | Index + KMP | Index build | vs KMP |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 0.5 M | 2.7 ms | 2.3 ms | 0.5 ms | 0.1 ms | 34 ms | 18.2× |
| 10,000 | 5 M | 21.9 ms | 19.4 ms | 2.3 ms | 0.4 ms | 337 ms | 45.1× |
| 100,000 | 50 M | 249.5 ms | 186.8 ms | 11.4 ms | 9.7 ms * | — | — |

\* At 50 M bases the 16 M k-mer cap is exceeded, so the index reports itself skipped and the query
falls back to Bloom screening. The interface shows this with an asterisk and a footnote rather
than presenting a fallback as an index lookup.

**Reading the table honestly.** Naive is within tens of percent of KMP on random DNA, because a
14-base motif almost never partially matches at a random position — KMP's advantage appears on
repetitive input, not here. The large gains are from screening, not from the matcher. Timings at the
smallest size are also noisier: that row runs first, on a colder JIT.

### 7.2 What the screens actually do

Dataset: 5,000 sequences × 1,000 bp (5 Mbp), motif `GATTACAGATTACA`, exact, both strands.

| Mode | Sequences read | Rejected | Confirmed | False positives |
|---|---:|---:|---:|---:|
| KMP only | 5,000 | 0 | 50 | — |
| Bloom + KMP | 50 | 4,950 (99.0%) | 50 | 0 |
| Index + KMP | 393 | 4,607 (92.1%) | 50 | 343 |

All three report the same 50 hits. The index reads more candidates than the Bloom filters here
because it accepts a sequence on *one* rare k-mer, while the Bloom path requires *all* of the
motif's k-mers to be possibly present.

### 7.3 False positives are real — and visible

A motif of exactly `k` bases exercises the filter's failure mode, since only one k-mer has to
collide. Same 5 Mbp dataset generated with `plantedFraction: 0` through `/api/dataset/generate`
(the page's synthetic build plants 1% of sequences), motif `GATTACAG` (8 bp):

| Mode | Sequences read | Confirmed | False positives | Rate over passes |
|---|---:|---:|---:|---:|
| Bloom + KMP | 262 of 5,000 | 145 | 117 | 44.7% |
| KMP only | 5,000 | 145 | — | — |

Both report the same 147 positions. The 117 false positives cost confirmation time and nothing
else — which is exactly why a pass is never reported as a match.

On the page's default build (1% of sequences carry `GATTACAGATTACA`, which also contains
`GATTACAG`) the same query gives **307 passes, 194 confirmed, 113 false positives (36.8%)** and the
same 208 positions as KMP: the counts move with the data, the invariant does not.

### 7.4 Threads

10 Mbp dataset, `KMP` mode so the measurement is pure matching, identical hit lists throughout:

| Workers | 1 | 2 | 4 | 8 |
|---|---:|---:|---:|---:|
| Time | 103.3 ms | 53.9 ms | 46.1 ms | **25.9 ms** |

Four times faster on eight workers, with the answer unchanged.

### 7.5 Memory

At `k = 8` on 5 Mbp: index **41.6 MB** over 65,536 distinct k-mers and 4.9 M occurrences; Bloom
filters **6.0 MB** in total. The index stores 8 bytes per posting plus a table slot; the Bloom
filters store `log2(1/p) ≈ 6.6` bits per k-mer.

---

## 8. Limitations

1. **Screening needs a rare motif.** If most sequences contain the motif, nothing can be rejected
   and the filters add overhead. The benchmark reports the rare case; the common case is why a
   "KMP only" mode exists in the interface.
2. **Approximate and wildcard queries are not screened.** An edited match changes every k-mer, so
   those queries scan the full dataset. The interface says so before showing results.
3. **The index has a hard cap** (`k ≤ 16`, 16 M k-mer occurrences). Above it, Bloom filters take
   over; the fallback is labelled, not hidden.
4. **KMP is not a speed-up on random DNA.** Its benefit is bounded worst-case behaviour; the
   speed-ups in this report come from screening.
5. **Approximate matching reports every alignment within budget**, so one edited site can appear
   as a few nearby positions. This is the honest behaviour; collapsing them was rejected because
   it would hide valid alignments.
6. **One dataset per process, held in memory.** A restart clears it; there is no persistence.
7. **Bloom sizing assumes independent hashes.** Double hashing approximates that, and the measured
   rate is reported rather than assumed.

---

## 9. Individual contributions

| Member | Modules owned | Also |
|---|---|---|
| Leric Raju (2409308) | KMP, naive baseline, repository and build | benchmarks, this report, demo |
| Mohammed Yusuf (2410309) | Bloom filter, k-mer encoding, FASTA validation | false-positive measurements |
| Tabish Matwankar (2410672) | inverted index, Myers approximate matching, frontend | threading, sample data |

Commits are authored by Leric Raju's account on behalf of the team; module ownership above is who
presents and answers for each part in the viva.

---

## 10. Reproduction

```
mvn test          # 62 tests
mvn spring-boot:run
```

Open `http://localhost:8080`. The page loads a synthetic dataset automatically; press Enter to
search. For the 100k benchmark row, run with more heap:

```
MAVEN_OPTS="-Xmx4g" mvn spring-boot:run
```

Sample data: `sample-data/sample.fasta` (upload), `sample-data/reverse-strand.fasta` (strand
flag), `sample-data/invalid.fasta` (must be rejected).

---

## 11. References

1. D. E. Knuth, J. H. Morris, V. R. Pratt. *Fast pattern matching in strings.* SIAM J. Computing, 1977.
2. B. H. Bloom. *Space/time trade-offs in hash coding with allowable errors.* CACM, 1970.
3. G. Myers. *A fast bit-vector algorithm for approximate string matching based on dynamic programming.* JACM, 1999.
4. T. H. Cormen et al. *Introduction to Algorithms*, 4th ed. — string matching, hashing.
